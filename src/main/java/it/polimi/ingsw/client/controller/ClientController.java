package it.polimi.ingsw.client.controller;

import it.polimi.ingsw.client.model.*;
import it.polimi.ingsw.network.ClientConnectionManager;
import it.polimi.ingsw.network.ClientNetworkObserver;
import it.polimi.ingsw.util.customexceptions.ServerUnreachableException;
import it.polimi.ingsw.util.supportclasses.ClientState;
import org.json.simple.JSONObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static it.polimi.ingsw.util.supportclasses.Constants.RECONNECT_RETRY_ATTEMPTS;
import static it.polimi.ingsw.util.supportclasses.Constants.RECONNECT_RETRY_INTERVAL;

/**
 * This class acts as the central controller for the client-side application of the Codex game.
 */
public class ClientController implements ClientNetworkObserver {

    private final ClientConnectionManager clientConnectionManager;
    private final ClientMessageHandler clientMessageHandler;
    private static volatile ClientController instance;

    //what it takes to come back after a drop: where the server is, who we were and where we were
    private static volatile String serverAddress;
    private static volatile int serverPort;
    private static volatile String gameName;
    private static volatile String usernameToReclaim;
    private static final ScheduledExecutorService RETRIES = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "client-reconnect");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> pendingRetry;
    private static int retriesLeft;

    /**
     * Opens a connection to the server, closing the previous one if there was any.
     * @throws ServerUnreachableException If nothing answers at that address.
     */
    public static synchronized ClientController connect(String serverAddress, int serverPort) throws ServerUnreachableException {
        if (instance != null) instance.shutdown();
        instance = new ClientController(serverAddress, serverPort);
        ClientController.serverAddress = serverAddress;
        ClientController.serverPort = serverPort;
        return instance;
    }

    /**
     * Opens a fresh connection and asks to be let back into the game this client dropped out of.
     * @return true if the server was reachable and the request went out.
     */
    public static synchronized boolean reconnect() {
        if (gameName == null || usernameToReclaim == null || serverAddress == null) return false;
        try {
            ClientController reconnected = new ClientController(serverAddress, serverPort);
            if (instance != null) instance.shutdown();
            instance = reconnected;
            reconnected.clientConnectionManager.send(ClientMessageGenerator.generateReconnectMessage(usernameToReclaim, gameName));
            return true;
        } catch (ServerUnreachableException e) {
            return false;
        }
    }

    /**
     * Keeps trying to get back in, until it works or the window closes. The user can also
     * trigger a try by hand at any point.
     */
    private static synchronized void startRetrying() {
        if (pendingRetry != null) return;
        retriesLeft = RECONNECT_RETRY_ATTEMPTS;
        pendingRetry = RETRIES.scheduleWithFixedDelay(ClientController::attemptRetry,
                RECONNECT_RETRY_INTERVAL, RECONNECT_RETRY_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private static synchronized void attemptRetry() {
        if (ClientStateModel.getInstance().getClientState() != ClientState.LOST_CONNECTION_STATE || retriesLeft-- <= 0) {
            stopRetrying();
            return;
        }
        reconnect();
    }

    /**
     * Stops the automatic retries. Called once we're back in, or once we've given up.
     */
    public static synchronized void stopRetrying() {
        if (pendingRetry == null) return;
        pendingRetry.cancel(false);
        pendingRetry = null;
    }

    /**
     * Remembers the game this client is in, so it knows where to ask to go back to.
     * @param name The game's name, or null once the client leaves it.
     */
    public static void setGameName(String name) {
        gameName = name;
    }

    public static String getGameName() {
        return gameName;
    }

    /**
     * Singleton pattern implementation to ensure only one instance of
     * `ClientController` exists throughout the application.
     * @return The singleton instance.
     * @throws IllegalStateException if the client hasn't connected to a server yet via
     * {@link #connect(String, int)}. There's no sane server to fall back to here,
     * so failing loudly beats silently talking to the wrong one.
     */
    public static ClientController getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ClientController requested before a connection was established. Call connect(serverAddress, serverPort) first.");
        }
        return instance;
    }

    private ClientController (String serverAddress, int serverPort) throws ServerUnreachableException {
        clientMessageHandler = new ClientMessageHandler();
        clientConnectionManager = new ClientConnectionManager(this,serverAddress,serverPort);
    }

    /**
     * This method is called whenever a message is received from the
     * server. It delegates the message processing to the `ClientMessageHandler`.
     * @param message message to process.
     */
    @Override
    public void processMessage(JSONObject message) {
        clientMessageHandler.execute(message);
    }


    /**
     * Resets all the client models to their initial state.
     */
    public void resetModels(){
        DeckModel.getInstance().clear();
        ObjectivesModel.getInstance().clear();
        GameFieldModel.getInstance().clear();
        HandModel.getInstance().clear();
        PlayerModel.getInstance().clear();
        ScoreBoardModel.getInstance().clear();
        SelectableCardsModel.getInstance().clear();
    }


    /**
     * Updates the client state to reflect the loss and displays a message. Called when a network connection loss is detected.
     */
    @Override
    public void notifyConnectionLoss() {
        //the connection has already closed itself by the time we hear about it
        usernameToReclaim = PlayerModel.getInstance().getUsername();
        ClientStateModel.getInstance().setClientState(ClientState.LOST_CONNECTION_STATE);
        //only worth coming back to a game we were actually in
        if (gameName != null) startRetrying();
    }

    /**
     * Sends a message to the server to set the player's username.
     * @param username The username to be set for the player.
     */
    public void sendSetUsernameMessage(String username) {
        clientConnectionManager.send(ClientMessageGenerator.generateSetUsernameMessage(username));

    }

    /**
     * Sends a message to the server requesting a list of available games to join.
     */
    public void sendGetAvailableGamesMessage(){
        clientConnectionManager.send(ClientMessageGenerator.generateGetAvailableGamesMessage());

    }

    /**
     * Sends a message to the server requesting to join a specific game.
     * @param gameName The name of the game to join.
     */
    public void sendJoinGameMessage(String gameName){
        setGameName(gameName);
        clientConnectionManager.send(ClientMessageGenerator.generateJoinGameMessage(gameName));

    }

    /**
     * Sends a message to the server indicating the client intends to leave the current game session.
     */
    public void sendLeaveMessage(){
        setGameName(null);
        clientConnectionManager.send(ClientMessageGenerator.generateLeaveMessage());
    }

    /**
     * Sends a message to the server requesting to set up a new game.
     * @param gameName The desired name for the new game.
     * @param numOfPlayers The desired number of players for the game.
     */
    public void sendSetUpGameMessage(String gameName, int numOfPlayers) {
        setGameName(gameName);
        clientConnectionManager.send(ClientMessageGenerator.generateSetUpGameMessage(gameName,numOfPlayers));

    }

    /**
     * Sends a message to the server indicating the client is ready to begin the game.
     */
    public void sendReadyMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateReadyMessage());
    }

    /**
     * Sends a message to the server informing the chosen side for a specific starter card identified by its ID.
     * @param cardId The unique identifier of the starter card.
     * @param facingUp Whether the chosen side of the card is facing up.
     */
    public void sendChosenStarterCardSideMessage(int cardId, boolean facingUp) {
        clientConnectionManager.send(ClientMessageGenerator.generateChosenStarterCardSideMessage(cardId,facingUp));
    }

    /**
     * Sends a message to the server indicating the chosen secret objective card identified by its ID.
     * @param cardId The unique identifier of the chosen secret objective card.
     */
    public void sendChosenSecretObjectiveMessage(int cardId) {
        clientConnectionManager.send(ClientMessageGenerator.generateChosenSecretObjectiveMessage(cardId));
        ObjectivesModel.getInstance().setSecretObjectiveId(cardId);
    }

    /**
     * Sends a message to the server requesting to place a card on the game board.
     * @param cardId The unique identifier of the card to be placed.
     * @param x The X-coordinate of the desired placement location.
     * @param y The Y-coordinate of the desired placement location.
     * @param facingUp Whether the card should be placed facing up or down.
     */
    public void sendPlaceMessage(int cardId, int x, int y, boolean facingUp) {
        clientConnectionManager.send(ClientMessageGenerator.generatePlaceMessage(cardId, x, y, facingUp));
    }

    /**
     * Sends a message to the server requesting to draw a resource card directly from the resource deck.
     */
    public void sendDirectDrawResourceCardMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateDirectDrawResourceCardMessage());
    }

    /**
     * Sends a message to the server requesting to draw a resource card from the left revealed card of the resource deck.
     */
    public void sendDrawLeftResourceCardMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateDrawLeftResourceCardMessage());
    }

    /**
     * Sends a message to the server requesting to draw a resource card from the right revealed card of the resource deck.
     */
    public void sendDrawRightResourceCardMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateDrawRightResourceCardMessage());
    }
    /**
     * Sends a message to the server requesting to draw a gold card directly from the gold deck.
     */
    public void sendDirectDrawGoldCardMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateDirectDrawGoldCardMessage());
    }

    /**
     * Sends a message to the server requesting to draw a gold card from the left revealed card of the gold deck.
     */
    public void sendDrawLeftGoldCardMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateDrawLeftGoldCardMessage());
    }

    /**
     * Sends a message to the server requesting to draw a gold card from the right revealed card of the gold deck.
     */
    public void sendDrawRightGoldCardMessage() {
        clientConnectionManager.send(ClientMessageGenerator.generateDrawRightGoldCardMessage());
    }

    /**
     * Closes the connection to the server. Does not terminate the JVM: the
     * caller (GUI or CLI) decides when and how to exit the process.
     */
    public void shutdown() {
        clientConnectionManager.shutdown();
    }
}
