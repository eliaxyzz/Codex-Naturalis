package it.polimi.ingsw.server.lobby;

import it.polimi.ingsw.server.ServerLog;
import java.util.logging.Level;
import java.util.logging.Logger;
import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.network.ServerWelcomeSocket;
import it.polimi.ingsw.network.ServerNetworkObserver;
import it.polimi.ingsw.server.controller.GameController;
import it.polimi.ingsw.server.persistence.GameSnapshot;
import it.polimi.ingsw.server.persistence.GameStore;
import org.json.simple.JSONObject;
import java.nio.file.Path;
import it.polimi.ingsw.util.customexceptions.*;
import it.polimi.ingsw.util.supportclasses.Request;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static it.polimi.ingsw.util.supportclasses.Constants.MAX_PLAYERS;
import static it.polimi.ingsw.util.supportclasses.Constants.RECONNECT_TIMEOUT;
import static it.polimi.ingsw.util.supportclasses.Constants.SAVE_DIRECTORY;
import static it.polimi.ingsw.util.supportclasses.Constants.MIN_PLAYERS;

/**
 * This class represents the lobby where players can create or join a game and set their usernames.
 * Lobby state changes on the lobby thread: requests, new connections and disconnects are queued
 * as tasks. The collections are still concurrent because games (closing, going unavailable) and
 * the server console touch them from their own threads.
 */
public class Lobby implements ServerNetworkObserver {
    private static final Logger LOG = ServerLog.get();

    private final BlockingQueue<Runnable> tasks;
    private final List<ClientHandler> connectedClients;
    private final Map<String, GameController> games;
    private final Map<String, GameController> availableGames;
    private final Set<String> takenUsernames;
    private ServerWelcomeSocket serverWelcomeSocket = null;
    private boolean welcomeSocketIsRunning = false;
    private int welcomeSocketPort;
    private final ExecutorService executorService;
    //one timer thread for every game's reconnection window
    private final ScheduledExecutorService scheduler;
    private final LobbyRequestHandler lobbyRequestHandler;
    private volatile boolean running;
    private volatile long reconnectTimeout = RECONNECT_TIMEOUT;
    private volatile GameStore gameStore = new GameStore(Path.of(SAVE_DIRECTORY));

    public Lobby() {
        connectedClients = new CopyOnWriteArrayList<>();
        games = new ConcurrentHashMap<>();
        availableGames = new ConcurrentHashMap<>();
        takenUsernames = ConcurrentHashMap.newKeySet();
        tasks = new LinkedBlockingQueue<>();
        executorService = Executors.newCachedThreadPool();
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "lobby-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        lobbyRequestHandler = new LobbyRequestHandler(this);
        running = true;

    }

    /**
     * Initializes the welcome socket that is used by the server to listen for new connections.
     * @param port The port to listen on.
     * @throws CannotOpenWelcomeSocket Thrown when the welcome socket can't be opened on the specified port.
     * @throws WelcomeSocketIsAlreadyOpenException Thrown when the server is already listening on a port.
     */
    public void initializeWelcomeSocket(int port) throws CannotOpenWelcomeSocket, WelcomeSocketIsAlreadyOpenException {
        if (!welcomeSocketIsRunning) {
            serverWelcomeSocket = new ServerWelcomeSocket(this, port);
            executorService.submit(serverWelcomeSocket);
            welcomeSocketPort = port;
            welcomeSocketIsRunning = true;
        }
        else throw new WelcomeSocketIsAlreadyOpenException(String.valueOf(welcomeSocketPort));
    }

    public Map<String, GameController> getAvailableGames() {
        return availableGames;
    }

    public Map<String, GameController> getGames() {
        return games;
    }

    public List<ClientHandler> getConnectedClients() {
        return connectedClients;
    }

    public int getWelcomeSocketPort() {
        return welcomeSocketPort;
    }

    /**
     * Starts the lobby execution that continuously processes requests from clients until the server is shut down.
     */
    public void startLobby() {
        System.out.println("Lobby started");
        System.out.println("Echo: off");
        System.out.println("Set a port for the server with 'setport' command.");
        System.out.println("Type 'help' for more information.");
        System.out.println();
        while (running) {
            try {
                tasks.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                //one broken request must not take down the lobby, and with it every client still in it
                LOG.log(Level.WARNING, "Lobby: dropping a request that failed", e);
            }
        }
    }

    /**
     * Adds a new client to the lobby.
     * @param client New client handler.
     */
    public void submitNewClient(ClientHandler client) {
        tasks.add(() -> {
            setRandomGuestUsername(client);
            enterLobby(client);
            client.send(LobbyMessageGenerator.usernameSetMessage(client.getUsername()));
            //only now can messages or a disconnect come in, and the client already has a name
            client.start();
        });
    }

    /**
     * Sets a random username to the client.
     * @param client The client handler that needs to be assigned with a random username.
     */
    private void setRandomGuestUsername(ClientHandler client){
        boolean usernameNotSet = true;
        while (usernameNotSet) {
            String username = "Guest" + (int) (Math.random() * 100000);
            try {
                setUsername(username,client);
                usernameNotSet = false;
            } catch (AlreadyTakenUsernameException ignored) {
            }
        }
    }

    /**
     * Submits a new request to lobby.
     * @param request The new request.
     */
    public void submitNewRequest(Request request) {
        tasks.add(() -> lobbyRequestHandler.execute(request));
    }

    /**
     * Adds the client to the lobby's arraylist of connected clients.
     * @param client The client to allow in.
     */
    public void enterLobby(ClientHandler client) {
        if(!connectedClients.contains(client)) {
            connectedClients.add(client);
        }
        LOG.info(() -> "Client '" + (client.getUsername() == null ? "" : client.getUsername()) + "' is now in the lobby");
        client.send(LobbyMessageGenerator.joinedLobbyMessage());
    }

    /**
     * Removes the client from the lobby.
     * @param client The client to allow in.
     */
    public void leaveLobby(ClientHandler client) {
        connectedClients.remove(client);
        if (client.getUsername() != null) {
            takenUsernames.remove(client.getUsername());
        }
        LOG.info(() -> "Client '" + client.getUsername() + "' left the lobby");
        client.shutdown();
    }

    /**
     * Drops a dead connection without giving up the username: a suspended player still owns
     * their name until their game ends.
     * @param client The client that went away.
     */
    public void forgetConnection(ClientHandler client) {
        connectedClients.remove(client);
        client.shutdown();
    }

    /**
     * Allows the client to pick a username, the username is added to the taken usernames list to ensure the uniqueness.
     * @param username The chosen username.
     * @param client The client that is setting the username.
     * @throws AlreadyTakenUsernameException Thrown when trying to choose an already taken username.
     */
    public void setUsername(String username, ClientHandler client) throws AlreadyTakenUsernameException {
        if (username.equals(client.getUsername())) return;
        //claim the new name first: add() is atomic, so two clients racing for it can't both win,
        //and a failed rename leaves the old name reserved
        if (!takenUsernames.add(username)) {
            throw new AlreadyTakenUsernameException();
        }
        String oldUsername = client.getUsername();
        client.setUsername(username);
        if (oldUsername != null) {
            takenUsernames.remove(oldUsername);
            LOG.info(() -> "Client '" + oldUsername + "' changed their username to '" + username + "'");
        }
    }

    /**
     * Creates a new game with the requested number of players and assign a name to it.
     * The creator joins it straight away and gets the gameCreated confirmation from the game thread.
     * @param numberOfPlayers The number of players that will join.
     * @param gameName The name to identify the game.
     */
    public void setupNewGame(int numberOfPlayers, String gameName, ClientHandler client) throws CannotCreateGameException {
        if(gameName == null) throw new CannotCreateGameException("Invalid name!");
        if(numberOfPlayers < MIN_PLAYERS || numberOfPlayers > MAX_PLAYERS) {
            throw new CannotCreateGameException("A game needs " + MIN_PLAYERS + " to " + MAX_PLAYERS + " players!");
        }
        if(games.containsKey(gameName)) throw new CannotCreateGameException("Game name already taken!");
        GameController newGameController = new GameController(this, numberOfPlayers, gameName);
        games.put(gameName, newGameController);
        availableGames.put(gameName,newGameController);
        newGameController.submitJoin(client, LobbyMessageGenerator.createdGameMessage());
        executorService.submit(newGameController);
    }

    /**
     * Runs a task later, for the games' reconnection windows. The task is expected to hand the
     * work straight to its game's queue: this thread must not touch game state itself.
     * @param task What to run.
     * @param delayMillis How long to wait.
     * @return A handle to cancel it with.
     */
    public GameStore getGameStore() {
        return gameStore;
    }

    /**
     * Points the lobby at a different directory for its saved games. The tests use this to keep
     * out of the real one.
     * @param gameStore Where saved games live.
     */
    public void setGameStore(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    /**
     * Picks up every game that was left saved, so a restarted server comes back with them.
     * Each one waits for its players to reconnect: nobody is connected to a game yet.
     * @return How many games came back.
     */
    public int restoreSavedGames() {
        int restored = 0;
        for (JSONObject snapshot : gameStore.loadAll()) {
            String gameName = String.valueOf(snapshot.get("gameName"));
            try {
                if (games.containsKey(gameName)) continue;
                GameSnapshot.Restored saved = GameSnapshot.restore(snapshot);
                GameController gameController = new GameController(this, saved.game(), gameName, saved.chatLog());
                games.put(gameName, gameController);
                //hold their names so nobody takes a seat that isn't theirs
                takenUsernames.addAll(saved.game().getPlayerUsernames());
                executorService.submit(gameController);
                restored++;
                LOG.info(() -> "Restored the saved game '" + gameName + "'");
            } catch (RuntimeException e) {
                //one unreadable save must not stop the server coming up
                LOG.log(Level.WARNING, "Could not restore the saved game '" + gameName + "'", e);
                gameStore.delete(gameName);
            }
        }
        return restored;
    }

    public long getReconnectTimeout() {
        return reconnectTimeout;
    }

    /**
     * Shortens the window games wait for absent players. Only the tests need this.
     * @param reconnectTimeout The new window, in milliseconds.
     */
    public void setReconnectTimeout(long reconnectTimeout) {
        this.reconnectTimeout = reconnectTimeout;
    }

    public ScheduledFuture<?> schedule(Runnable task, long delayMillis) {
        return scheduler.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Frees the usernames a closed game was still holding for players that could have come back.
     * @param usernames The usernames to release.
     */
    public void releaseUsernames(Collection<String> usernames) {
        takenUsernames.removeAll(usernames);
    }

    /**
     * Hands a returning client to the game it claims a seat in. The game answers with the full
     * game state or with cannotReconnect.
     * @param client The returning client.
     * @param username The name it is claiming back.
     * @param gameName The game it wants back into.
     */
    public void reconnect(ClientHandler client, String username, String gameName) {
        GameController gameController = games.get(gameName);
        if (gameController == null) {
            client.send(LobbyMessageGenerator.cannotReconnectMessage("Game '" + gameName + "' is no longer running"));
            return;
        }
        if (!gameController.getGame().isConnected(username) && gameController.getGame().getPlayer(username) != null) {
            //the name is still reserved for them, so hand it over from their throwaway guest name
            String guestName = client.getUsername();
            if (guestName != null) takenUsernames.remove(guestName);
            gameController.submitReconnect(client, username);
            return;
        }
        client.send(LobbyMessageGenerator.cannotReconnectMessage("There's nobody called '" + username + "' to come back as"));
    }

    /**
     * Removes a game from the list of available games.
     * @param gameName The game name.
     */
    public void makeUnavailable(String gameName) {
        availableGames.remove(gameName);
    }

    /**
     * CLoses the game from the lobby.
     * @param gameName The game name.
     */
    public void closeGame(String gameName) {
        makeUnavailable(gameName);
        games.remove(gameName);
        gameStore.delete(gameName);
    }

    /**
     * Allows a client to join a game that is waiting for players. The game itself answers
     * with joinGame or gameIsFull once it has processed the request.
     * @param client The client that wants to join.
     * @param gameName The name of the game to join.
     * @throws NonExistentGameException Thrown when the given game name isn't the name of one of the available games to join.
     */
    public void joinGame(ClientHandler client, String gameName) throws NonExistentGameException {
        GameController gameController = availableGames.get(gameName);
        if (gameController == null) { throw new NonExistentGameException(); }
        gameController.submitJoin(client, LobbyMessageGenerator.joinGameMessage(gameName));
    }

    @Override
    public void notifyConnectionLoss(ClientHandler clientHandler) {
        LOG.info(() -> "Client '" + clientHandler.getUsername() + "' lost connection");
        tasks.add(() -> leaveLobby(clientHandler));
    }

    /**
     * Stops the lobby execution, draining the client-handling thread pool before returning.
     * Does not terminate the JVM: the caller decides when to exit the process.
     */
    public void shutdown() {
        running = false;
        if (serverWelcomeSocket != null) {
            serverWelcomeSocket.shutdown();
        }
        scheduler.shutdownNow();
        if (executorService != null) {
            //games sit in tasks.take() and only stop on interrupt
            executorService.shutdownNow();
            try {
                boolean terminatedCleanly = executorService.awaitTermination(5, TimeUnit.SECONDS);
                if (!terminatedCleanly) {
                    System.out.println("Lobby shutdown: some games didn't stop within the timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        //startLobby() is blocked on tasks.take(): wake it up so it can observe running == false
        tasks.add(() -> {});
    }
}
