package it.polimi.ingsw.server.controller;

import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.server.model.Game;
import it.polimi.ingsw.server.model.Player;
import it.polimi.ingsw.server.model.card.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.*;

/**
 * This class is responsible for generating JSON messages that are sent to clients throughout the game.
 * These messages inform players about game state, their hand and deck information, and other relevant game events.
 */
@SuppressWarnings("unchecked") //json-simple's JSONObject is a raw map
public class ServerMessageGenerator {

    private final Game game;

    public ServerMessageGenerator(Game game) {
        this.game = game;
    }

    /**
     * This message is sent to each player before the beginning of the match so that they can select the starter card orientation and their
     * secrete objective.
     * @param starterCard The given to the player.
     * @param objectiveCard1 The first objective card that can be selected.
     * @param objectiveCard2 The second objective card that can be selected.
     * @return The message containing these three cards.
     */
    public JSONObject cardsSelectionMessage (StarterCard starterCard, ObjectiveCard objectiveCard1, ObjectiveCard objectiveCard2) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "cardsSelection");
        jsonMap.put("starterCardID", String.valueOf(starterCard.getId()));
        jsonMap.put("objectiveCardID1", String.valueOf(objectiveCard1.getId()));
        jsonMap.put("objectiveCardID2", String.valueOf(objectiveCard2.getId()));
        return new JSONObject(jsonMap);
    }

    /**
     * This message is sent to each player at the beginning of the match.
     * @param gameController The game controller of the current match.
     * @param player The player who is playing the current match.
     * @return The message containing all the necessary information the player needs to start the match.
     */
    public JSONObject startGameMessage (GameController gameController, Player player) {
        JSONObject message = new JSONObject();
        message.put("message","startGame");
        message.put("hand", updatedHand(player));
        message.put("decks", updatedDecks());
        message.put("placementHistory", updatedPlacementHistory(player));
        message.put("resources", updatedResources(player));
        message.put("secretObjectiveID", String.valueOf(player.getSecretObjective().getId()));
        message.put("token", player.getToken().toString());
        message.put("commonObjective1", String.valueOf(game.getCommonObjectives().getFirst().getId()));
        message.put("commonObjective2", String.valueOf(game.getCommonObjectives().getLast().getId()));
        message.put("firstPlayer", gameController.getTurnPlayerUsername());
        //a player who dropped between placing and drawing has to come back into the drawing step
        message.put("alreadyPlaced", String.valueOf(player.hasAlreadyPlaced()));
        return message;
    }

    /**
     * Carries one line of chat to a client.
     * @param sender Who wrote it.
     * @param recipient Who it was addressed to, or null if it went to the whole table.
     * @param text What they wrote.
     * @return The message.
     */
    public JSONObject chatMessage(String sender, String recipient, String text) {
        JSONObject message = new JSONObject();
        message.put("message", "chatMessage");
        message.put("sender", sender);
        message.put("text", text);
        if (recipient != null) message.put("recipient", recipient);
        return message;
    }

    /**
     * Tells a client their message didn't go anywhere.
     * @param reason Why it was refused.
     * @return The message.
     */
    public JSONObject cannotChatMessage(String reason) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "cannotChat");
        jsonMap.put("reason", reason);
        return new JSONObject(jsonMap);
    }

    /**
     * Tells everyone that a player lost their connection and the game is going on without them.
     * @param username The player that went away.
     * @return The message.
     */
    public JSONObject playerSuspendedMessage(String username) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "playerSuspended");
        jsonMap.put("username", username);
        return new JSONObject(jsonMap);
    }

    /**
     * Tells everyone that a player came back.
     * @param username The player that came back.
     * @return The message.
     */
    public JSONObject playerResumedMessage(String username) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "playerResumed");
        jsonMap.put("username", username);
        return new JSONObject(jsonMap);
    }

    /**
     * Sent to the only player left once nobody came back in time.
     * @param username The winner.
     * @return The message.
     */
    public JSONObject wonByDefaultMessage(String username) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "gameWonByDefault");
        jsonMap.put("username", username);
        return new JSONObject(jsonMap);
    }

    /**
     * This message sends to a player his updated hand.
     * @param player The player with the updated hand.
     * @return The message containing the updated hand.
     */
    public JSONObject updatedHandMessage(Player player) {
        JSONObject message = new JSONObject();
        message.put("message", "updatedHand");
        message.put("updatedHand", updatedHand(player));
        return message;
    }

    /**
     * This message is sent to notify the beginning of a player's turn.
     * @param gameController The game controller is used to get the player's name.
     * @return The message showing which player is about to play and his name.
     */
    public JSONObject turnPlayerUpdateMessage(GameController gameController) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "turnPlayerUpdate");
        jsonMap.put("player",  gameController.getTurnPlayerUsername());
        return new JSONObject(jsonMap);
    }

    /**
     * This message is sent to players in order to update the decks after each draw.
     * @return The message containing updated information about the decks.
     */
    public JSONObject updatedDecksMessage() {
        JSONObject message = new JSONObject();
        message.put("message", "updatedDecks");
        message.put("updatedDecks", updatedDecks());
        return message;
    }

    /**
     * This message is sent when the player executes a successful placement.
     * @param player The player who places the card.
     * @return The message containing updated information (score, his hand, resources on the game-field).
     */
    public JSONObject successfulPlaceMessage(Player player) {
        JSONObject message = new JSONObject();
        message.put("message","successfulPlace");
        message.put("placementHistory", updatedPlacementHistory(player));
        message.put("updatedHand" , updatedHand(player));
        message.put("updatedResources", updatedResources(player));
        message.put("updatedScore", String.valueOf(player.getScore()));
        return message;
    }

    /**
     * This message is sent when a player cannot place a card for a particular reason.
     * @param reason The reason that explains why the placement is incorrect.
     * @return The message to the player.
     */
    public JSONObject cannotPlaceMessage(String reason) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "cannotPlace");
        jsonMap.put("reason",  reason);
        return new JSONObject(jsonMap);
    }

    /**
     * This message is sent when a player cannot draw a card for a particular reason.
     * @param reason The reason that explains why the draw is invalid.
     * @return The message to the player.
     */
    public JSONObject cannotDrawMessage(String reason) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "cannotDraw");
        jsonMap.put("reason",  reason);
        return new JSONObject(jsonMap);
    }

    /**
     * This message is sent when a player's starter card or secret objective selection doesn't match one of their drawn cards.
     * @param reason The reason that explains why the selection is invalid.
     * @return The message to the player.
     */
    public JSONObject invalidSelectionMessage(String reason) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "invalidSelection");
        jsonMap.put("reason",  reason);
        return new JSONObject(jsonMap);
    }

    /**
     * This message is sent to notify players' updated scores after each turn.
     * @param gameController The game controller of the current game.
     * @return The message containing all the players' scores.
     */
    public JSONObject updatedScoresMessage (GameController gameController) {
        JSONObject message = new JSONObject();
        message.put("message","updatedScores");
        JSONArray scores = new JSONArray();
        for(ClientHandler clientHandler: gameController.getClientHandlers()) {
            JSONObject client = new JSONObject();
            client.put("username", clientHandler.getUsername());
            client.put("score", String.valueOf(gameController.getCurrentPlayer(clientHandler).getScore()));
            client.put("token", String.valueOf(gameController.getCurrentPlayer(clientHandler).getToken()));
            scores.add(client);
        }
        message.put("updatedScores", scores);
        return message;
    }

    /**
     * This message informs the players that they're playing the last round.
     * @param reason The reason that explains what triggered the last round.
     * @return Rhe message that informs the players.
     */
    public JSONObject lastRoundMessage(String reason) {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "lastRound");
        jsonMap.put("reason",  reason);
        return new JSONObject(jsonMap);
    }

    /**
     * This message sends to the players their final scores when the game is ended.
     * @return The final scores.
     */
    public JSONObject leaderBoardMessage (List<String> rankedUsernames) {
        //players who lost their connection are ranked too, so the slots go by username
        String[] positions = {"first", "second", "third", "fourth"};
        JSONObject message = new JSONObject();
        message.put("message", "leaderBoard");
        for (int i = 0; i < positions.length; i++) {
            if (i >= rankedUsernames.size()) {
                message.put(positions[i], null);
                continue;
            }
            String username = rankedUsernames.get(i);
            Player player = game.getPlayer(username);
            Map<String,String> jsonMap = new HashMap<>();
            jsonMap.put("username", username);
            jsonMap.put("score", String.valueOf(player.getScore()));
            jsonMap.put("solvedObjectives", String.valueOf(player.getNumOfCompletedObjectiveCards()));
            message.put(positions[i], new JSONObject(jsonMap));
        }
        return message;
    }

    /**
     * Message sent when a client loses connection to inform the other clients that the game is getting cancelled
     * @return The message
     */
    public JSONObject closingGameMessage () {
        Map<String,String> jsonMap = new HashMap<>();
        jsonMap.put("message", "closingGame");
        return new JSONObject(jsonMap);
    }

    private JSONArray updatedPlacementHistory(Player player) {
        JSONArray placementHistory = new JSONArray();
        for(PlaceableCard placeableCard : player.getGamefield().getPlacementHistory()) {
            Map<String,String> jsonMap = new HashMap<>();
            jsonMap.put("cardID", String.valueOf(placeableCard.getId()));
            jsonMap.put("facingUp", String.valueOf(placeableCard.isFacingUp()));
            jsonMap.put("x", String.valueOf(placeableCard.getX()));
            jsonMap.put("y", String.valueOf(placeableCard.getY()));
            JSONObject card = new JSONObject(jsonMap);
            placementHistory.add(card);
        }
        return placementHistory;
    }

    private JSONArray updatedHand(Player player) {
        JSONArray handArray = new JSONArray();
        for(PlaceableCard cardInHand : player.getHand()){
            handArray.add(String.valueOf(cardInHand.getId()));
        }
        return handArray;
    }

    private JSONObject updatedDecks() {
        Map<String,String> decks = new HashMap<>();
        decks.put("topDeckResourceCardID", String.valueOf(game.getResourceCardDeck().getTopCardID()));
        decks.put("leftRevealedResourceCardID", String.valueOf(game.getResourceCardDeck().getLeftRevealedCardID()));
        decks.put("rightRevealedResourceCardID", String.valueOf(game.getResourceCardDeck().getRightRevealedCardID()));
        decks.put("topDeckGoldCardID", String.valueOf(game.getGoldCardDeck().getTopCardID()));
        decks.put("leftRevealedGoldCardID", String.valueOf(game.getGoldCardDeck().getLeftRevealedCardID()));
        decks.put("rightRevealedGoldCardID", String.valueOf(game.getGoldCardDeck().getRightRevealedCardID()));
        return new JSONObject(decks);
    }

    private JSONObject updatedResources(Player player) {


        Map<String,String> jsonMap= new HashMap<>();

        jsonMap.put("fungiResources", String.valueOf(player.getGamefield().getFungiCount()));
        jsonMap.put("plantResources", String.valueOf(player.getGamefield().getPlantCount()));
        jsonMap.put("animalResources", String.valueOf(player.getGamefield().getAnimalCount()));
        jsonMap.put("insectResources", String.valueOf(player.getGamefield().getInsectCount()));
        jsonMap.put("featherCount", String.valueOf(player.getGamefield().getFeatherCount()));
        jsonMap.put("scrollCount", String.valueOf(player.getGamefield().getScrollCount()));
        jsonMap.put("inkPotCount", String.valueOf(player.getGamefield().getInkPotCount()));

        return new JSONObject(jsonMap);
    }
}
