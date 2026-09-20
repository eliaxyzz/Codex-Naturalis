package it.polimi.ingsw.server.persistence;

import it.polimi.ingsw.server.chat.ChatEntry;
import it.polimi.ingsw.server.chat.ChatLog;
import it.polimi.ingsw.server.model.Game;
import it.polimi.ingsw.server.model.Player;
import it.polimi.ingsw.server.model.card.*;
import it.polimi.ingsw.server.model.deck.*;
import it.polimi.ingsw.util.customexceptions.CannotPlaceCardException;
import it.polimi.ingsw.util.customexceptions.FullHandException;
import it.polimi.ingsw.util.supportclasses.GameState;
import it.polimi.ingsw.util.supportclasses.Token;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.List;
import static it.polimi.ingsw.util.supportclasses.Constants.LAST_GOLD_CARD_ID;
import static it.polimi.ingsw.util.supportclasses.Constants.LAST_RESOURCE_CARD_ID;
import static it.polimi.ingsw.util.supportclasses.Constants.LAST_STARTER_CARD_ID;

/**
 * Turns a game into JSON and back.
 * <p>
 * Only what was decided gets written down: the decks in the order they were left, the players'
 * tallies, and the ordered list of placements they made. Everything a game field derives from
 * those placements (the grid, the resource counts, which corners are covered) is worked out
 * again by replaying them, so none of it can drift out of step with the cards.
 */
public final class GameSnapshot {

    /**
     * A game as it came back off disk.
     * @param game The rebuilt game.
     * @param chatLog The chat that went with it.
     */
    public record Restored(Game game, ChatLog chatLog) {}

    private GameSnapshot() {}

    /**
     * @param game The game to write down.
     * @param gameName Its name.
     * @param chatLog Its chat.
     * @return The game as JSON.
     */
    @SuppressWarnings("unchecked")
    public static JSONObject of(Game game, String gameName, ChatLog chatLog) {
        JSONObject json = new JSONObject();
        json.put("gameName", gameName);
        json.put("numberOfPlayers", (long) game.getNumberOfPlayers());
        json.put("gameState", game.getGameState().name());
        json.put("turnCounter", (long) game.getTurnCounter());
        json.put("turnOrder", stringArray(game.getTurnOrder()));
        json.put("commonObjectives", idsOf(game.getCommonObjectives()));
        JSONArray tokens = new JSONArray();
        for (Token token : game.getAvailableTokens()) tokens.add(token.name());
        json.put("availableTokens", tokens);
        json.put("decks", decksOf(game));
        JSONArray players = new JSONArray();
        for (String username : usernames(game)) players.add(playerOf(username, game.getPlayer(username)));
        json.put("players", players);
        json.put("chat", chatOf(chatLog));
        return json;
    }

    /**
     * @param json A game written by {@link #of}.
     * @return The rebuilt game and its chat. Every player comes back disconnected: after a
     *         restart nobody is on the other end until they reconnect.
     */
    public static Restored restore(JSONObject json) {
        JSONObject decks = (JSONObject) json.get("decks");
        JSONObject resource = (JSONObject) decks.get("resource");
        JSONObject gold = (JSONObject) decks.get("gold");
        List<Token> availableTokens = new ArrayList<>();
        for (Object token : (JSONArray) json.get("availableTokens")) availableTokens.add(Token.valueOf(token.toString()));

        Game game = new Game(intOf(json.get("numberOfPlayers")),
                ObjectiveCardDeck.restored(idList((JSONArray) decks.get("objective"))),
                ResourceCardDeck.restored(idList((JSONArray) resource.get("cards")),
                        intOf(resource.get("left")), intOf(resource.get("right"))),
                GoldCardDeck.restored(idList((JSONArray) gold.get("cards")),
                        intOf(gold.get("left")), intOf(gold.get("right"))),
                StarterCardDeck.restored(idList((JSONArray) decks.get("starter"))),
                availableTokens);

        List<ObjectiveCard> commonObjectives = new ArrayList<>();
        for (Object id : (JSONArray) json.get("commonObjectives")) commonObjectives.add(new ObjectiveCard(intOf(id)));
        game.restoreCommonObjectives(commonObjectives);

        for (Object entry : (JSONArray) json.get("players")) restorePlayer(game, (JSONObject) entry);

        List<String> turnOrder = new ArrayList<>();
        for (Object username : (JSONArray) json.get("turnOrder")) turnOrder.add(username.toString());
        game.restoreTurn(turnOrder, intOf(json.get("turnCounter")));
        game.setGameState(GameState.valueOf(json.get("gameState").toString()));

        ChatLog chatLog = new ChatLog();
        for (Object entry : (JSONArray) json.get("chat")) {
            JSONObject line = (JSONObject) entry;
            Object recipient = line.get("recipient");
            chatLog.add(new ChatEntry(line.get("sender").toString(),
                    recipient == null ? null : recipient.toString(), line.get("text").toString()));
        }
        return new Restored(game, chatLog);
    }

    @SuppressWarnings("unchecked")
    private static JSONObject decksOf(Game game) {
        JSONObject decks = new JSONObject();
        decks.put("objective", longArray(game.getObjectiveCardDeck().remainingCardIds()));
        decks.put("starter", longArray(game.getStarterCardDeck().remainingCardIds()));
        decks.put("resource", revealedDeckOf(game.getResourceCardDeck().remainingCardIds(),
                game.getResourceCardDeck().getLeftRevealedCardID(), game.getResourceCardDeck().getRightRevealedCardID()));
        decks.put("gold", revealedDeckOf(game.getGoldCardDeck().remainingCardIds(),
                game.getGoldCardDeck().getLeftRevealedCardID(), game.getGoldCardDeck().getRightRevealedCardID()));
        return decks;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject revealedDeckOf(List<Integer> remaining, int left, int right) {
        JSONObject deck = new JSONObject();
        deck.put("cards", longArray(remaining));
        deck.put("left", (long) left);
        deck.put("right", (long) right);
        return deck;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject playerOf(String username, Player player) {
        JSONObject json = new JSONObject();
        json.put("username", username);
        json.put("token", player.getToken().name());
        json.put("score", (long) player.getScore());
        json.put("ready", player.isReady());
        json.put("starterChosen", player.isStarterCardOrientationSelected());
        json.put("alreadyPlaced", player.hasAlreadyPlaced());
        json.put("completedObjectives", (long) player.getNumOfCompletedObjectiveCards());
        json.put("hand", idsOf(player.getHand()));
        json.put("starterCardId", (long) (player.getStarterCard() == null ? 0 : player.getStarterCard().getId()));
        json.put("secretObjectiveId", (long) (player.getSecretObjective() == null ? 0 : player.getSecretObjective().getId()));
        JSONArray drawn = new JSONArray();
        for (ObjectiveCard card : player.getDrawnObjectiveCards()) {
            if (card != null) drawn.add((long) card.getId());
        }
        json.put("drawnObjectives", drawn);
        JSONArray placements = new JSONArray();
        for (PlaceableCard card : player.getGamefield().getPlacementHistory()) {
            JSONObject placement = new JSONObject();
            placement.put("id", (long) card.getId());
            placement.put("facingUp", card.isFacingUp());
            placement.put("x", (long) card.getX());
            placement.put("y", (long) card.getY());
            placements.add(placement);
        }
        json.put("placements", placements);
        return json;
    }

    @SuppressWarnings("unchecked")
    private static JSONArray chatOf(ChatLog chatLog) {
        JSONArray chat = new JSONArray();
        for (ChatEntry entry : chatLog.entries()) {
            JSONObject line = new JSONObject();
            line.put("sender", entry.sender());
            if (entry.recipient() != null) line.put("recipient", entry.recipient());
            line.put("text", entry.text());
            chat.add(line);
        }
        return chat;
    }

    private static void restorePlayer(Game game, JSONObject json) {
        String username = json.get("username").toString();
        Player player = game.addPlayer(username, Token.valueOf(json.get("token").toString()));

        int starterCardId = intOf(json.get("starterCardId"));
        if (starterCardId != 0) player.setStarterCard(new StarterCard(starterCardId));
        int secretObjectiveId = intOf(json.get("secretObjectiveId"));
        if (secretObjectiveId != 0) player.setSecretObjective(new ObjectiveCard(secretObjectiveId));
        JSONArray drawn = (JSONArray) json.get("drawnObjectives");
        ObjectiveCard[] offered = new ObjectiveCard[2];
        for (int i = 0; i < offered.length && i < drawn.size(); i++) offered[i] = new ObjectiveCard(intOf(drawn.get(i)));
        player.setDrawnObjectiveCards(offered);

        for (Object id : (JSONArray) json.get("hand")) {
            try {
                player.addToHand(placeableCard(intOf(id)));
            } catch (FullHandException e) {
                throw new IllegalStateException("saved hand holds more cards than a hand can", e);
            }
        }
        replayPlacements(player, (JSONArray) json.get("placements"));
        //after the replay, because placing sets these along the way
        player.restoreProgress(intOf(json.get("score")), boolOf(json.get("ready")),
                boolOf(json.get("starterChosen")), boolOf(json.get("alreadyPlaced")),
                intOf(json.get("completedObjectives")));
        player.setConnected(false);
    }

    /**
     * Rebuilds the game field by making the same moves again, in the same order. The points
     * each placement paid out are dropped: the score is restored from the file.
     */
    private static void replayPlacements(Player player, JSONArray placements) {
        for (Object entry : placements) {
            JSONObject placement = (JSONObject) entry;
            int id = intOf(placement.get("id"));
            boolean facingUp = boolOf(placement.get("facingUp"));
            int x = intOf(placement.get("x"));
            int y = intOf(placement.get("y"));
            if (id > LAST_GOLD_CARD_ID && id <= LAST_STARTER_CARD_ID) {
                player.getGamefield().place(new StarterCard(id), facingUp);
                continue;
            }
            try {
                player.getGamefield().place(placeableCard(id), facingUp, x, y);
            } catch (CannotPlaceCardException e) {
                throw new IllegalStateException("saved game field replays into an illegal placement", e);
            }
        }
    }

    private static PlaceableCard placeableCard(int id) {
        if (id <= LAST_RESOURCE_CARD_ID) return new ResourceCard(id);
        if (id <= LAST_GOLD_CARD_ID) return new GoldCard(id);
        return new StarterCard(id);
    }

    private static List<String> usernames(Game game) {
        //turn order once play has started, otherwise whoever is seated
        List<String> usernames = new ArrayList<>(game.getTurnOrder());
        for (String username : game.getPlayerUsernames()) {
            if (!usernames.contains(username)) usernames.add(username);
        }
        return usernames;
    }

    @SuppressWarnings("unchecked")
    private static JSONArray stringArray(List<String> values) {
        JSONArray array = new JSONArray();
        array.addAll(values);
        return array;
    }

    @SuppressWarnings("unchecked")
    private static JSONArray longArray(List<Integer> values) {
        JSONArray array = new JSONArray();
        for (Integer value : values) array.add((long) value);
        return array;
    }

    @SuppressWarnings("unchecked")
    private static JSONArray idsOf(List<? extends Card> cards) {
        JSONArray array = new JSONArray();
        for (Card card : cards) array.add((long) card.getId());
        return array;
    }

    private static List<Integer> idList(JSONArray array) {
        List<Integer> ids = new ArrayList<>();
        for (Object id : array) ids.add(intOf(id));
        return ids;
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static boolean boolOf(Object value) {
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }
}
