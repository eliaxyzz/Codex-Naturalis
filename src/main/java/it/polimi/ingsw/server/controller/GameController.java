package it.polimi.ingsw.server.controller;

import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.network.ServerNetworkObserver;
import it.polimi.ingsw.server.ServerLog;
import it.polimi.ingsw.server.chat.ChatEntry;
import it.polimi.ingsw.server.chat.ChatLog;
import it.polimi.ingsw.server.lobby.Lobby;
import it.polimi.ingsw.server.lobby.LobbyMessageGenerator;
import it.polimi.ingsw.server.model.DrawSource;
import it.polimi.ingsw.server.model.Game;
import it.polimi.ingsw.server.model.Player;
import it.polimi.ingsw.util.customexceptions.*;
import it.polimi.ingsw.util.supportclasses.GameState;
import it.polimi.ingsw.util.supportclasses.Request;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static it.polimi.ingsw.util.supportclasses.Constants.MAX_CHAT_LENGTH;

/**
 * Runs one game: connects the players' connections to the Game model and tells everyone what happened.
 * The rules live in Game and Player; this class sequences them and does the messaging.
 * <p>
 * Every change to the game happens on the game's own thread: network requests, joins and
 * disconnects all arrive as tasks on {@code tasks}. clientHandlers is still a CopyOnWriteArrayList
 * because the lobby and the server console read it (player counts, game info) from their threads.
 */
public class GameController implements Runnable, ServerNetworkObserver {
    private static final Logger LOG = ServerLog.get();

    private final String gameName;
    private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    private final Lobby lobby;
    private final Game game;
    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final ChatLog chatLog = new ChatLog();
    private final GameRequestHandler gameRequestHandler;
    private final ServerMessageGenerator messageGenerator;
    private volatile boolean running = true;
    //set and cancelled on the game thread only
    private ScheduledFuture<?> lastPlayerTimeout;

    public GameController(Lobby lobby, int numberOfPlayers, String gameName) {
        this.gameName = gameName;
        this.lobby = lobby;
        this.game = new Game(numberOfPlayers);
        this.messageGenerator = new ServerMessageGenerator(game);
        this.gameRequestHandler = new GameRequestHandler(this, messageGenerator, game);
        LOG.info(() -> "Game '" + gameName + "' is ready to receive players");
    }

    @Override
    public void run() {
        while (running) {
            try {
                tasks.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Game '" + gameName + "': dropping a request that failed", e);
            }
        }
    }

    @Override
    public void submitNewRequest(Request request) {
        tasks.add(() -> gameRequestHandler.execute(request));
    }

    /**
     * Queues a join. The outcome is sent to the client from the game thread: {@code confirmation}
     * if it got in, gameIsFull otherwise.
     * @param client The client that wants to join.
     * @param confirmation The message to send once the client is in.
     */
    public void submitJoin(ClientHandler client, JSONObject confirmation) {
        tasks.add(() -> enterGame(client, confirmation));
    }

    @Override
    public void notifyConnectionLoss(ClientHandler client) {
        LOG.info(() -> "In game '" + gameName + "' player '" + client.getUsername() + "' disconnected");
        tasks.add(() -> {
            if (game.isUnderWay() && clientHandlers.contains(client)) {
                //keep their seat warm: they can come back to it
                suspendPlayer(client);
                return;
            }
            removePlayer(client);
            //the lobby still has to forget the client (username, connected list)
            lobby.notifyConnectionLoss(client);
        });
    }

    /**
     * Queues a reconnection. The client is answered from the game thread: the full game state
     * if it got back in, cannotReconnect otherwise.
     * @param client The returning client, still carrying its guest username.
     * @param username The name it is claiming back.
     */
    public void submitReconnect(ClientHandler client, String username) {
        tasks.add(() -> reconnect(client, username));
    }

    /**
     * The player is gone but the game carries on without them. Their username stays reserved,
     * so the lobby is deliberately not told about this.
     */
    private void suspendPlayer(ClientHandler client) {
        String username = client.getUsername();
        clientHandlers.remove(client);
        game.suspendPlayer(username);
        client.setGame(null);
        //the lobby drops the dead connection but keeps the name reserved for their return
        lobby.forgetConnection(client);
        LOG.info(() -> "In game '" + gameName + "' player '" + username + "' is suspended");
        broadcast(messageGenerator.playerSuspendedMessage(username));
        if (game.connectedPlayerCount() == 0) {
            LOG.info(() -> "Nobody is left in the game '" + gameName + "': game is closed");
            lobby.releaseUsernames(game.getTurnOrder());
            closeGame();
            return;
        }
        //don't leave the turn parked on someone who isn't there
        if (username.equals(game.getTurnPlayer())) {
            passTurn(game.getPlayer(username));
        }
        startLastPlayerTimeoutIfAlone();
    }

    /**
     * Puts a returning player back at their seat and sends them the whole game state.
     */
    private void reconnect(ClientHandler client, String username) {
        if (!game.isUnderWay() || game.getPlayer(username) == null || game.isConnected(username)) {
            client.send(LobbyMessageGenerator.cannotReconnectMessage("There's nobody to come back as in '" + gameName + "'"));
            return;
        }
        client.setUsername(username);
        client.setGame(this);
        if (client.isClosed()) {
            client.setGame(null);
            return;
        }
        //back at the same spot in the turn order, so score lists keep coming out in that order
        clientHandlers.add(Math.min(turnOrderIndexOf(username), clientHandlers.size()), client);
        game.resumePlayer(username);
        cancelLastPlayerTimeout();
        LOG.info(() -> "Player '" + username + "' rejoined the game '" + gameName + "'");
        Player player = game.getPlayer(username);
        //they reconnected under a throwaway guest name, tell them which one they got back
        client.send(LobbyMessageGenerator.usernameSetMessage(username));
        client.send(messageGenerator.startGameMessage(this, player));
        client.send(messageGenerator.updatedScoresMessage(this));
        if (game.getGameState() == GameState.lastRound) {
            client.send(messageGenerator.lastRoundMessage("the game was already at its last round"));
        }
        replayChat(client, username);
        broadcast(messageGenerator.playerResumedMessage(username));
    }

    private int turnOrderIndexOf(String username) {
        List<String> turnOrder = game.getTurnOrder();
        int index = turnOrder.indexOf(username);
        if (index < 0) return clientHandlers.size();
        //count only the seats before this one that are actually occupied right now
        int occupied = 0;
        for (int i = 0; i < index; i++) {
            if (game.isConnected(turnOrder.get(i))) occupied++;
        }
        return occupied;
    }

    /**
     * With a single player left there's no game to play: give the others a while to come back,
     * and hand them the win if nobody does.
     */
    private void startLastPlayerTimeoutIfAlone() {
        if (game.connectedPlayerCount() != 1 || lastPlayerTimeout != null) return;
        long timeout = lobby.getReconnectTimeout();
        LOG.info(() -> "Game '" + gameName + "' has one player left, waiting " + timeout + "ms for the others");
        //fire onto the game's own queue: the game state is only ever touched from that thread
        lastPlayerTimeout = lobby.schedule(() -> tasks.add(this::awardGameToLastPlayer), timeout);
    }

    private void cancelLastPlayerTimeout() {
        if (lastPlayerTimeout == null) return;
        lastPlayerTimeout.cancel(false);
        lastPlayerTimeout = null;
    }

    private void awardGameToLastPlayer() {
        //someone may have come back, or the game may have ended, while the timer was pending
        if (!game.isUnderWay() || game.connectedPlayerCount() != 1 || clientHandlers.isEmpty()) return;
        lastPlayerTimeout = null;
        ClientHandler winner = clientHandlers.getFirst();
        LOG.info(() -> "Game '" + gameName + "' goes to '" + winner.getUsername() + "': nobody else came back");
        game.setGameState(GameState.endGame);
        winner.send(messageGenerator.wonByDefaultMessage(winner.getUsername()));
        //still show them where everyone finished, absent players included
        sendLeaderboard();
    }

    public String getGameName() {
        return gameName;
    }

    public Game getGame() {
        return game;
    }

    public List<ClientHandler> getClientHandlers() {
        return clientHandlers;
    }

    public int getNumberOfPlayers() {
        return game.getNumberOfPlayers();
    }

    public Player getCurrentPlayer(ClientHandler client) {
        return game.getPlayer(client.getUsername());
    }

    public String getTurnPlayerUsername() {
        return game.getTurnPlayer();
    }

    public void broadcast(JSONObject message) {
        for (ClientHandler player : clientHandlers) {
            player.send(message);
        }
    }

    private void enterGame(ClientHandler client, JSONObject confirmation) {
        if (game.isFull() || game.getGameState() != GameState.waitingForPlayers) {
            client.send(LobbyMessageGenerator.gameIsFullMessage());
            return;
        }
        //route the client here before checking it's still connected: a disconnect racing with
        //this join then either sees the new route and comes to us, or we see it closed and back off
        client.setGame(this);
        if (client.isClosed()) {
            client.setGame(null);
            return;
        }
        clientHandlers.add(client);
        game.addPlayer(client.getUsername());
        client.send(confirmation);
        LOG.info(() -> "Player '" + client.getUsername() + "' joined the game '" + gameName + "'");
    }

    /**
     * Sends one line of chat, to the whole table or to a single player.
     * @param client Who is writing.
     * @param rawText What they wrote.
     * @param recipient Who it's for, or null for everyone.
     */
    public void sendChat(ClientHandler client, String rawText, String recipient) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            client.send(messageGenerator.cannotChatMessage("You can't send an empty message"));
            return;
        }
        if (text.length() > MAX_CHAT_LENGTH) {
            client.send(messageGenerator.cannotChatMessage("Keep it under " + MAX_CHAT_LENGTH + " characters"));
            return;
        }
        String sender = client.getUsername();
        if (recipient == null) {
            chatLog.add(new ChatEntry(sender, null, text));
            broadcast(messageGenerator.chatMessage(sender, null, text));
            return;
        }
        if (recipient.equals(sender)) {
            client.send(messageGenerator.cannotChatMessage("You can't whisper to yourself"));
            return;
        }
        if (game.getPlayer(recipient) == null) {
            client.send(messageGenerator.cannotChatMessage("There's no '" + recipient + "' in this game"));
            return;
        }
        //logged either way, so a player who is away still reads it when they come back
        chatLog.add(new ChatEntry(sender, recipient, text));
        JSONObject message = messageGenerator.chatMessage(sender, recipient, text);
        client.send(message);
        for (ClientHandler other : clientHandlers) {
            if (recipient.equals(other.getUsername())) other.send(message);
        }
    }

    /**
     * Catches a returning player up on what was said, leaving out whispers that were never theirs.
     */
    private void replayChat(ClientHandler client, String username) {
        for (ChatEntry entry : chatLog.visibleTo(username)) {
            client.send(messageGenerator.chatMessage(entry.sender(), entry.recipient(), entry.text()));
        }
    }

    /**
     * The player chose to leave: back to the lobby they go.
     */
    public void leaveGame(ClientHandler client) {
        removePlayer(client);
        lobby.enterLobby(client);
    }

    /**
     * Takes the player out of the game and, if the game was under way, closes it for everyone else.
     * Does nothing if the client isn't (or is no longer) in this game.
     */
    private void removePlayer(ClientHandler client) {
        if (!clientHandlers.remove(client)) return;
        game.removePlayer(client.getUsername());
        client.setGame(null);
        LOG.info(() -> "Player '" + client.getUsername() + "' left the game '" + gameName + "'");
        if (game.isUnderWay()) {
            game.setGameState(GameState.aClientDisconnected);
            LOG.info(() -> "Game '" + gameName + "' is closing");
            broadcast(messageGenerator.closingGameMessage());
        }
        if (clientHandlers.isEmpty()) {
            LOG.info(() -> "There are no more players in the game '" + gameName + "': game is closed");
            closeGame();
        }
    }

    private void closeGame() {
        cancelLastPlayerTimeout();
        lobby.closeGame(gameName);
        running = false;
    }

    public void ready(ClientHandler client) {
        getCurrentPlayer(client).setReady(true);
        LOG.info(() -> "In game '" + gameName + "' player '" + client.getUsername() + "' is ready");
        if (!game.dealSetupCardsIfReady()) return;
        lobby.makeUnavailable(gameName);
        for (ClientHandler c : clientHandlers) {
            Player player = getCurrentPlayer(c);
            c.send(messageGenerator.cardsSelectionMessage(player.getStarterCard(), player.getDrawnObjectiveCards()[0], player.getDrawnObjectiveCards()[1]));
        }
        LOG.info(() -> "In game '" + gameName + "' all players are ready");
    }

    /**
     * @return false if the id isn't the player's starter card or the side was already chosen.
     */
    public boolean chooseStarterCardSide(ClientHandler client, int starterCardId, boolean facingUp) {
        if (!getCurrentPlayer(client).chooseStarterSide(starterCardId, facingUp)) return false;
        LOG.info(() -> "In game '" + gameName + "' player '" + client.getUsername() + "' chose to play their starter card on the " + (facingUp ? "front" : "back"));
        startGameIfSetupComplete();
        return true;
    }

    /**
     * @return false if the id isn't one of the two objectives the player was dealt.
     */
    public boolean chooseSecretObjectiveCard(ClientHandler client, int objectiveCardId) {
        if (!getCurrentPlayer(client).chooseSecretObjective(objectiveCardId)) return false;
        LOG.info(() -> "In game '" + gameName + "' player '" + client.getUsername() + "' chose the secret objective " + objectiveCardId);
        startGameIfSetupComplete();
        return true;
    }

    private void startGameIfSetupComplete() {
        if (!game.setupChoicesComplete()) return;
        //random turn order; clientHandlers follows it too so score lists come out in turn order
        Collections.shuffle(clientHandlers);
        game.startPlaying(clientHandlers.stream().map(ClientHandler::getUsername).toList());
        for (Player player : game.getPlayers()) {
            player.initializeHand();
            player.clearTurnState();
        }
        for (ClientHandler client : clientHandlers) {
            client.send(messageGenerator.startGameMessage(this, getCurrentPlayer(client)));
        }
        broadcast(messageGenerator.updatedScoresMessage(this));
        LOG.info(() -> "Game '" + gameName + "' is starting");
    }

    /**
     * Places a card from the hand of the player whose turn it is. In the last round there's
     * no draw afterwards, so placing ends the turn.
     * @throws CannotPlaceCardException If it's not the player's turn or the placement is illegal.
     */
    public void place(ClientHandler client, int placeableCardId, boolean facingUp, int x, int y) throws CannotPlaceCardException {
        if (!game.isTurnOf(client.getUsername())) {
            throw new CannotPlaceCardException("You can't place a card, it's not your turn!");
        }
        try {
            getCurrentPlayer(client).place(placeableCardId, facingUp, x, y);
        } catch (CardNotInHandException e) {
            throw new CannotPlaceCardException("The card is not in your hand");
        }
        startLastRoundIfDue();
        broadcast(messageGenerator.updatedScoresMessage(this));
        LOG.info(() -> "In game '" + gameName + "' player '" + client.getUsername() + "' placed the card " + placeableCardId + " at X:" + x + " Y:" + y);
        if (game.getGameState() == GameState.lastRound) passTurn(getCurrentPlayer(client));
    }

    /**
     * Draws a card for the player whose turn it is and passes the turn.
     * @throws NotYourTurnException If it's not the player's turn.
     * @throws CannotDrawException If the player hasn't placed a card yet this turn.
     * @throws EmptyDeckException If there's nothing left to draw there.
     * @throws FullHandException If the player's hand is already full.
     */
    public void draw(ClientHandler client, DrawSource source) throws NotYourTurnException, CannotDrawException, EmptyDeckException, FullHandException {
        if (!game.isTurnOf(client.getUsername())) throw new NotYourTurnException();
        Player player = getCurrentPlayer(client);
        if (!player.hasAlreadyPlaced()) throw new CannotDrawException();
        player.addToHand(game.draw(source));
        LOG.info(() -> "In game '" + gameName + "' player '" + client.getUsername() + "' has drawn " + source.description());
        startLastRoundIfDue();
        passTurn(player);
    }

    private void startLastRoundIfDue() {
        String reason = game.startLastRoundIfDue();
        if (reason == null) return;
        broadcast(messageGenerator.lastRoundMessage(reason));
        LOG.info(() -> "Game '" + gameName + "' is at the last round because " + reason);
    }

    /**
     * Ends the current player's turn, tells everyone whose turn it is and, if that closed
     * the last round, sends the final leaderboard.
     */
    private void passTurn(Player player) {
        player.clearTurnState();
        boolean gameOver = game.passTurn();
        broadcast(messageGenerator.turnPlayerUpdateMessage(this));
        if (gameOver) {
            LOG.info(() -> "Game '" + gameName + "' has ended");
            sendLeaderboard();
        }
    }

    private void sendLeaderboard() {
        //everyone who took a seat is scored, including players who never made it back
        ArrayList<String> ranking = new ArrayList<>(game.getTurnOrder());
        for (String username : ranking) {
            game.getPlayer(username).calculateFinalScore();
        }
        ranking.sort((a, b) -> game.getPlayer(a).compareTo(game.getPlayer(b)));
        broadcast(messageGenerator.leaderBoardMessage(ranking));
        LOG.info(() -> {
            StringBuilder leaderboard = new StringBuilder("Game '" + gameName + "' leaderboard:");
            for (int i = 0; i < ranking.size(); i++) {
                Player player = game.getPlayer(ranking.get(i));
                leaderboard.append(System.lineSeparator()).append(i + 1).append(": ").append(ranking.get(i))
                        .append(" ").append(player.getScore()).append(" points (")
                        .append(player.getNumOfCompletedObjectiveCards()).append(" objectives completed)");
            }
            return leaderboard.toString();
        });
    }
}
