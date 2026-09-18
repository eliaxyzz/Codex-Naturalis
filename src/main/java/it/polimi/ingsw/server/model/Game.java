package it.polimi.ingsw.server.model;

import it.polimi.ingsw.server.model.card.ObjectiveCard;
import it.polimi.ingsw.server.model.card.PlaceableCard;
import it.polimi.ingsw.server.model.deck.GoldCardDeck;
import it.polimi.ingsw.server.model.deck.ObjectiveCardDeck;
import it.polimi.ingsw.server.model.deck.ResourceCardDeck;
import it.polimi.ingsw.server.model.deck.StarterCardDeck;
import it.polimi.ingsw.util.customexceptions.EmptyDeckException;
import it.polimi.ingsw.util.supportclasses.Token;
import it.polimi.ingsw.util.supportclasses.GameState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static it.polimi.ingsw.util.supportclasses.Constants.SCORE_GOAL;

/**
 * This class represents the core game model, managing the game state, decks, players, and overall game logic.
 * It interacts with other components to receive player actions and update the game state accordingly.
 * Only the owning GameController's thread changes it; players is a ConcurrentHashMap because the
 * server console reads it from its own thread.
 */
public class Game {
    private int numberOfPlayers;
    private GameState gameState;
    private int turnCounter;

    private final ObjectiveCardDeck objectiveCardDeck;
    private final ResourceCardDeck resourceCardDeck;
    private final GoldCardDeck goldCardDeck;
    private final StarterCardDeck starterCardDeck;
    private final ArrayList<ObjectiveCard> commonObjectives;
    private final Map<String, Player> players;

    private final ArrayList<Token> availableTokens;
    private final List<String> turnOrder = new ArrayList<>();

    public Game(int numberOfPlayers) {
        this.setNumberOfPlayers(numberOfPlayers);
        players = new ConcurrentHashMap<>();
        objectiveCardDeck = new ObjectiveCardDeck();
        resourceCardDeck = new ResourceCardDeck();
        goldCardDeck = new GoldCardDeck();
        starterCardDeck = new StarterCardDeck();
        commonObjectives = new ArrayList<>();
        availableTokens = new ArrayList<>(Arrays.asList(Token.red, Token.yellow, Token.green, Token.blue));
        try {
            commonObjectives.add(objectiveCardDeck.directDraw());
            commonObjectives.add(objectiveCardDeck.directDraw());
        } catch (EmptyDeckException ignored) {
        }
        gameState = GameState.waitingForPlayers;
    }
    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gamestate) {this.gameState = gamestate;}

    public Token getRandomToken() {
        Collections.shuffle(getAvailableTokens());
        return getAvailableTokens().removeFirst();
    }

    public Map<String, Player> getPlayersHashMap() { return players;}

    public Player getPlayer(String username) {
        return players.get(username);
    }

    public boolean isFull() {
        return players.size() == numberOfPlayers;
    }

    /**
     * @return true between card selection and the end of the last round.
     */
    public boolean isUnderWay() {
        return gameState == GameState.waitingForCardsSelection || gameState == GameState.playing || gameState == GameState.lastRound;
    }

    public Player addPlayer(String username) {
        Player player = new Player(this);
        players.put(username, player);
        return player;
    }

    /**
     * Takes the player out of the game and makes their token available again.
     */
    public void removePlayer(String username) {
        Player player = players.remove(username);
        if (player != null) reinsertToken(player.getToken());
    }

    /**
     * Once the table is full and everyone is ready, deals each player a starter card and two
     * objectives to choose from.
     * @return true if the cards were dealt now.
     */
    public boolean dealSetupCardsIfReady() {
        if (gameState != GameState.waitingForPlayers || !isFull()) return false;
        for (Player player : players.values()) {
            if (!player.isReady()) return false;
        }
        try {
            for (Player player : players.values()) {
                player.setStarterCard(starterCardDeck.directDraw());
                player.setDrawnObjectiveCards(new ObjectiveCard[]{objectiveCardDeck.directDraw(), objectiveCardDeck.directDraw()});
            }
        } catch (EmptyDeckException e) {
            //the decks hold enough cards for four players, running out here means the card data is broken
            throw new IllegalStateException("not enough starter or objective cards to deal", e);
        }
        gameState = GameState.waitingForCardsSelection;
        return true;
    }

    /**
     * @return true once every player has picked a starter card side and a secret objective.
     */
    public boolean setupChoicesComplete() {
        if (gameState != GameState.waitingForCardsSelection) return false;
        for (Player player : players.values()) {
            if (!player.isStarterCardOrientationSelected() || player.getSecretObjective() == null) return false;
        }
        return true;
    }

    public ArrayList<Player> getPlayers() {
        return new ArrayList<>(players.values());
    }

    /**
    * Makes the token available again.
    * @param token Token to be made available again.
     */
    public void reinsertToken(Token token) {
        getAvailableTokens().add(token);
    }


    public int getNumberOfPlayers() {
        return numberOfPlayers;
    }

    public void setNumberOfPlayers(int numberOfPlayers) {
        this.numberOfPlayers = numberOfPlayers;
    }

    /**
     * Moves from card selection to actual play, in the given turn order.
     * @param usernamesInTurnOrder Every player's username, first player first.
     */
    public void startPlaying(List<String> usernamesInTurnOrder) {
        turnOrder.clear();
        turnOrder.addAll(usernamesInTurnOrder);
        turnCounter = 0;
        gameState = GameState.playing;
    }

    /**
     * @return The username of the player whose turn it is, or null before play starts.
     */
    public String getTurnPlayer() {
        return turnOrder.isEmpty() ? null : turnOrder.get(turnCounter);
    }

    public boolean isTurnOf(String username) {
        return username.equals(getTurnPlayer());
    }

    /**
     * Hands the turn to the next player. During the last round, getting back to the first
     * player ends the game.
     * @return true if this ended the game.
     */
    public boolean passTurn() {
        turnCounter = (turnCounter + 1) % turnOrder.size();
        if (gameState == GameState.lastRound && turnCounter == 0) {
            gameState = GameState.endGame;
            return true;
        }
        return false;
    }

    /**
     * Starts the last round if someone reached the score goal or both decks ran out.
     * @return Why the last round started, or null if it didn't start now.
     */
    public String startLastRoundIfDue() {
        if (gameState != GameState.playing) return null;
        for (Map.Entry<String, Player> player : players.entrySet()) {
            if (player.getValue().getScore() >= SCORE_GOAL) {
                gameState = GameState.lastRound;
                return "player " + player.getKey() + " has " + SCORE_GOAL + " or more points";
            }
        }
        if (goldCardDeck.isEmpty() && resourceCardDeck.isEmpty()) {
            gameState = GameState.lastRound;
            return "decks are empty";
        }
        return null;
    }

    /**
     * @param source Where to draw from.
     * @return The drawn card.
     * @throws EmptyDeckException If there's nothing left there.
     */
    public PlaceableCard draw(DrawSource source) throws EmptyDeckException {
        return switch (source) {
            case RESOURCE_DECK -> resourceCardDeck.directDraw();
            case GOLD_DECK -> goldCardDeck.directDraw();
            case LEFT_RESOURCE -> resourceCardDeck.drawLeftRevealedCard();
            case RIGHT_RESOURCE -> resourceCardDeck.drawRightRevealedCard();
            case LEFT_GOLD -> goldCardDeck.drawLeftRevealedCard();
            case RIGHT_GOLD -> goldCardDeck.drawRightRevealedCard();
        };
    }

    public ObjectiveCardDeck getObjectiveCardDeck() {
        return objectiveCardDeck;
    }

    public ResourceCardDeck getResourceCardDeck() {
        return resourceCardDeck;
    }

    public GoldCardDeck getGoldCardDeck() {
        return goldCardDeck;
    }

    public StarterCardDeck getStarterCardDeck() {
        return starterCardDeck;
    }

    public ArrayList<ObjectiveCard> getCommonObjectives() {
        return commonObjectives;
    }

    public ArrayList<Token> getAvailableTokens() {
        return availableTokens;
    }

}
