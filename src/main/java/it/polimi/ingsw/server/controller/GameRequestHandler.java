package it.polimi.ingsw.server.controller;
import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.server.model.DrawSource;
import it.polimi.ingsw.server.model.Game;
import it.polimi.ingsw.util.customexceptions.*;
import it.polimi.ingsw.util.supportclasses.GameState;
import it.polimi.ingsw.util.supportclasses.Request;
import it.polimi.ingsw.util.supportclasses.RequestCommand;
import it.polimi.ingsw.util.supportclasses.RequestFields;
import org.json.simple.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * This class handles incoming requests from clients related to the game and delegates them to the appropriate methods in the game controller.
 */
public class GameRequestHandler {
    private final GameController gameController;
    private final ServerMessageGenerator messageGenerator;
    private final Game game;
    private final Map<String, RequestCommand> commands = new HashMap<>();

    private static final Map<String, DrawSource> DRAW_COMMANDS = Map.of(
            "directDrawResourceCard", DrawSource.RESOURCE_DECK,
            "directDrawGoldCard", DrawSource.GOLD_DECK,
            "drawLeftResourceCard", DrawSource.LEFT_RESOURCE,
            "drawRightResourceCard", DrawSource.RIGHT_RESOURCE,
            "drawLeftGoldCard", DrawSource.LEFT_GOLD,
            "drawRightGoldCard", DrawSource.RIGHT_GOLD
    );

    public GameRequestHandler(GameController gameController, ServerMessageGenerator messageGenerator, Game game) {
        this.messageGenerator = messageGenerator;
        this.gameController = gameController;
        this.game = game;
        commands.put("ready", (client, message) -> ready(client));
        commands.put("starterCard", (client, message) -> chooseStarterCardOrientation(message, client));
        commands.put("objectiveCard", (client, message) -> chooseSecretObjectiveCard(message, client));
        DRAW_COMMANDS.forEach((command, source) -> commands.put(command, (client, message) -> draw(client, source)));
        commands.put("place", this::place);
        commands.put("leave", (client, message) -> leave(client));
    }

    /**
     * Parses the commands received from clients and invokes the specific method in the game controller based on the requested action.
     * @param request that is about to be executed
     */
    public void execute (Request request)  {
        JSONObject message = request.message();
        ClientHandler client = request.client();
        String command;
        try {
            command = RequestFields.getCommand(message);
        } catch (InvalidMessageException e) {
            System.out.println("Discarding malformed request from client: " + e.getMessage());
            return;
        }

        if (game.getGameState() == GameState.lastRound && DRAW_COMMANDS.containsKey(command)) {
            client.send(messageGenerator.cannotDrawMessage("It's the last round: no more drawing"));
            return;
        }
        boolean gameIsOver = game.getGameState() == GameState.endGame || game.getGameState() == GameState.aClientDisconnected;
        if (gameIsOver && !command.equals("leave")) return;
        RequestCommand handler = commands.get(command);
        if (handler == null) return; //unrecognized command, discarded
        try {
            handler.execute(client, message);
        } catch (InvalidMessageException e) {
            System.out.println("Discarding malformed '" + command + "' request from client: " + e.getMessage());
        }
    }

    /**
     * invokes the ready method in the game controller
     * @param player who sent the request
     */
    private void ready(ClientHandler player)
    {
        gameController.ready(player);
    }

    /**
     * processes a request from a client to choose the orientation of their starter card.
     * @param message JSON message containing the starter card ID and facing up information
     * @param client client handler representing the player who sent the request
     */
    private void chooseStarterCardOrientation(JSONObject message, ClientHandler client) {
        int starterCardId = RequestFields.getInt(message, "starterCardId");
        boolean facingUp = RequestFields.getBoolean(message, "facingUp");
        if (!gameController.chooseStarterCardSide(client, starterCardId, facingUp)) {
            client.send(messageGenerator.invalidSelectionMessage("That's not your starter card"));
        }
    }

    /**
     * processes a request from a client to choose their secret objective card
     * @param message JSON message containing the objective card ID and facing up information
     * @param client client handler representing the player who sent the request
     */
    private void chooseSecretObjectiveCard(JSONObject message, ClientHandler client) {
        int objectiveCardId = RequestFields.getInt(message, "objectiveCardId");
        if (!gameController.chooseSecretObjectiveCard(client, objectiveCardId)) {
            client.send(messageGenerator.invalidSelectionMessage("That's not one of your drawn objective cards"));
        }
    }

    /**
     * processes a request from a client to draw a card
     * @param client client handler representing the player who sent the request
     * @param source where the client wants to draw from
     */
    private void draw(ClientHandler client, DrawSource source) {
        try {
            gameController.draw(client, source);
            client.send(messageGenerator.updatedHandMessage(gameController.getCurrentPlayer(client)));
            gameController.broadcast(messageGenerator.updatedDecksMessage());
        } catch (EmptyDeckException e) {
            client.send(messageGenerator.cannotDrawMessage("There's no card left there"));
        } catch (CannotDrawException e) {
            client.send(messageGenerator.cannotDrawMessage("You must place a card before drawing"));
        } catch (NotYourTurnException e) {
            client.send(messageGenerator.cannotDrawMessage("It's not your turn"));
        } catch (FullHandException e) {
            client.send(messageGenerator.cannotDrawMessage("Your hand is already full"));
        }
    }

    /**
     * processes a request from a client to place a card on the board
     * @param client client handler representing the player who sent the request
     * @param message JSON message containing information about the card to be placed
     */
    private void place(ClientHandler client, JSONObject message) {
        try {
            int placeableCardId = RequestFields.getInt(message, "placeableCardId");
            int x = RequestFields.getInt(message, "x");
            int y = RequestFields.getInt(message, "y");
            boolean facingUp = RequestFields.getBoolean(message, "facingUp");
            gameController.place(client, placeableCardId, facingUp, x, y);
            client.send(messageGenerator.successfulPlaceMessage(gameController.getCurrentPlayer(client)));
        }
        catch (CannotPlaceCardException e) {
            client.send(messageGenerator.cannotPlaceMessage(e.getMessage()));
        }
    }

    /**
     * processes a request from a client to leave the game
     * @param player client handler representing the player who sent the request
     */
    private void leave(ClientHandler player) {
        gameController.leaveGame(player);
    }

}
