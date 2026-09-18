package it.polimi.ingsw.server.controller;
import it.polimi.ingsw.network.ClientHandler;
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

    public GameRequestHandler(GameController gameController, ServerMessageGenerator messageGenerator, Game game) {
        this.messageGenerator = messageGenerator;
        this.gameController = gameController;
        this.game = game;
        commands.put("ready", (client, message) -> ready(client));
        commands.put("starterCard", (client, message) -> chooseStarterCardOrientation(message, client));
        commands.put("objectiveCard", (client, message) -> chooseSecretObjectiveCard(message, client));
        commands.put("directDrawResourceCard", (client, message) -> directDrawResourceCard(client));
        commands.put("directDrawGoldCard", (client, message) -> directDrawGoldCard(client));
        commands.put("drawLeftResourceCard", (client, message) -> drawLeftRevealedResourceCard(client));
        commands.put("drawRightResourceCard", (client, message) -> drawRightRevealedResourceCard(client));
        commands.put("drawLeftGoldCard", (client, message) -> drawLeftRevealedGoldCard(client));
        commands.put("drawRightGoldCard", (client, message) -> drawRightRevealedGoldCard(client));
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

        if(game.getGameState() == GameState.lastRound) {
            if(command.equals("directDrawResourceCard") ||
                command.equals("directDrawGoldCard") ||
                command.equals("drawLeftResourceCard") ||
                command.equals("drawRightResourceCard") ||
                command.equals("drawLeftGoldCard") ||
                command.equals("drawRightGoldCard")) {
                return;
            }
        }
        if(game.getGameState() == GameState.endGame || game.getGameState() == GameState.aClientDisconnected) {
            if(!command.equals("leave")) {
                return;
            }
        }
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
     * processes a request from a client to directly draw a resource card from the deck
     * @param client client handler representing the player who sent the request.
     */
    private void directDrawResourceCard(ClientHandler client) {
        try {
            gameController.directDrawResourceCard(client);
            client.send(messageGenerator.updatedHandMessage(gameController.getCurrentPlayer(client)));
            gameController.broadcast(messageGenerator.updatedDecksMessage());
        } catch (EmptyDeckException e) {
            client.send(messageGenerator.cannotDrawMessage("The deck is empty"));
        } catch (CannotDrawException e) {
            client.send(messageGenerator.cannotDrawMessage("You must place a card before drawing"));
        } catch (NotYourTurnException e) {
            client.send(messageGenerator.cannotDrawMessage("It's not your turn"));
        } catch (FullHandException e) {
            client.send(messageGenerator.cannotDrawMessage("Your hand is already full"));
        }
    }

    /**
     * processes a request from a client to directly draw a gold card from the deck
     * @param client client handler representing the player who sent the request.
     */
    private void directDrawGoldCard(ClientHandler client) {
        try {
            gameController.directDrawGoldCard(client);
            client.send(messageGenerator.updatedHandMessage(gameController.getCurrentPlayer(client)));
            gameController.broadcast(messageGenerator.updatedDecksMessage());
        } catch (EmptyDeckException e) {
            client.send(messageGenerator.cannotDrawMessage("The deck is empty"));
        } catch (CannotDrawException e) {
            client.send(messageGenerator.cannotDrawMessage("You must place a card before drawing"));
        } catch (NotYourTurnException e) {
            client.send(messageGenerator.cannotDrawMessage("It's not your turn"));
        } catch (FullHandException e) {
            client.send(messageGenerator.cannotDrawMessage("Your hand is already full"));
        }
    }

    /**
     * processes a request from a client to draw the revealed resource card from the left side of the deck
     * @param client client handler representing the player who sent the request
     */
    private void drawLeftRevealedResourceCard(ClientHandler client)  {
        try {
            gameController.drawLeftRevealedResourceCard(client);
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
     * processes a request from a client to draw the revealed resource card from the right side of the deck
     * @param client client handler representing the player who sent the request
     */
    private void drawRightRevealedResourceCard(ClientHandler client) {
        try {
            gameController.drawRightRevealedResourceCard(client);
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
     * processes a request from a client to draw the revealed gold card from the left side of the deck
     * @param client client handler representing the player who sent the request
     */
    private void drawLeftRevealedGoldCard(ClientHandler client) {
        try {
            gameController.drawLeftRevealedGoldCard(client);
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
     * processes a request from a client to draw the revealed gold card from the right side of the deck
     * @param client client handler representing the player who sent the request
     */
    private void drawRightRevealedGoldCard(ClientHandler client) {
        try {
            gameController.drawRightRevealedGoldCard(client);
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
