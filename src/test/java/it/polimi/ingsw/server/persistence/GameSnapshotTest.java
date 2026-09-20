package it.polimi.ingsw.server.persistence;

import it.polimi.ingsw.server.chat.ChatEntry;
import it.polimi.ingsw.server.chat.ChatLog;
import it.polimi.ingsw.server.model.DrawSource;
import it.polimi.ingsw.server.model.Game;
import it.polimi.ingsw.server.model.GameField;
import it.polimi.ingsw.server.model.Player;
import it.polimi.ingsw.server.model.card.PlaceableCard;
import it.polimi.ingsw.util.supportclasses.GameState;
import it.polimi.ingsw.util.supportclasses.Resource;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A game that has actually been played is written down and rebuilt, and has to come back
 * indistinguishable from the original.
 */
class GameSnapshotTest {
    private Game game;
    private ChatLog chatLog;

    @BeforeEach
    void playAFewTurns() throws Exception {
        game = new Game(2);
        Player alice = game.addPlayer("alice");
        Player bob = game.addPlayer("bob");
        alice.setReady(true);
        bob.setReady(true);
        assertTrue(game.dealSetupCardsIfReady());
        for (Player player : List.of(alice, bob)) {
            player.chooseStarterSide(player.getStarterCard().getId(), true);
            player.chooseSecretObjective(player.getDrawnObjectiveCards()[0].getId());
            player.initializeHand();
            player.clearTurnState();
        }
        game.startPlaying(List.of("alice", "bob"));

        //alice actually plays: a placement next to her starter card and a draw
        placeSomething(alice);
        alice.addToHand(game.draw(DrawSource.LEFT_RESOURCE));
        game.passTurn();
        alice.clearTurnState();
        bob.setScore(4);

        chatLog = new ChatLog();
        chatLog.add(new ChatEntry("alice", null, "hello table"));
        chatLog.add(new ChatEntry("alice", "bob", "psst"));
    }

    private void placeSomething(Player player) {
        int[][] spots = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] spot : spots) {
            try {
                player.place(player.getHand().getFirst().getId(), false, spot[0], spot[1]);
                return;
            } catch (Exception ignored) {
                //that diagonal was not open on this starter card, try the next
            }
        }
        throw new AssertionError("no diagonal around the starter card accepted a card");
    }

    private Game roundTrip() {
        JSONObject written = GameSnapshot.of(game, "table", chatLog);
        return GameSnapshot.restore(written).game();
    }

    @Test
    void theTurnAndStateComeBack() {
        Game restored = roundTrip();
        assertEquals(game.getGameState(), restored.getGameState());
        assertEquals(game.getTurnOrder(), restored.getTurnOrder());
        assertEquals(game.getTurnCounter(), restored.getTurnCounter());
        assertEquals(game.getTurnPlayer(), restored.getTurnPlayer());
        assertEquals(game.getNumberOfPlayers(), restored.getNumberOfPlayers());
    }

    @Test
    void everyPlayerComesBackWithTheirOwnThings() {
        Game restored = roundTrip();
        for (String username : List.of("alice", "bob")) {
            Player before = game.getPlayer(username);
            Player after = restored.getPlayer(username);
            assertNotNull(after, username + " was lost");
            assertEquals(before.getToken(), after.getToken(), username + " changed colour");
            assertEquals(before.getScore(), after.getScore());
            assertEquals(before.getSecretObjective().getId(), after.getSecretObjective().getId());
            assertEquals(before.getStarterCard().getId(), after.getStarterCard().getId());
            assertEquals(idsOf(before.getHand()), idsOf(after.getHand()), username + " got a different hand");
            assertEquals(before.hasAlreadyPlaced(), after.hasAlreadyPlaced());
            assertEquals(before.isReady(), after.isReady());
            assertEquals(before.isStarterCardOrientationSelected(), after.isStarterCardOrientationSelected());
        }
    }

    @Test
    void theGameFieldIsRebuiltFromThePlacements() {
        Game restored = roundTrip();
        GameField before = game.getPlayer("alice").getGamefield();
        GameField after = restored.getPlayer("alice").getGamefield();

        assertEquals(idsOf(before.getPlacementHistory()), idsOf(after.getPlacementHistory()));
        for (PlaceableCard card : before.getPlacementHistory()) {
            PlaceableCard sameSpot = after.lookAtCoordinates(card.getX(), card.getY());
            assertNotNull(sameSpot, "nothing at " + card.getX() + "," + card.getY() + " after restoring");
            assertEquals(card.getId(), sameSpot.getId());
            assertEquals(card.isFacingUp(), sameSpot.isFacingUp());
        }
        //resource counts are derived from the placements, so they must land on the same numbers
        for (Resource resource : Resource.values()) {
            assertEquals(before.getResourceCount(resource), after.getResourceCount(resource),
                    "resource count for " + resource + " drifted");
        }
    }

    @Test
    void theDecksComeBackInTheSameOrder() {
        Game restored = roundTrip();
        assertEquals(game.getResourceCardDeck().remainingCardIds(), restored.getResourceCardDeck().remainingCardIds());
        assertEquals(game.getGoldCardDeck().remainingCardIds(), restored.getGoldCardDeck().remainingCardIds());
        assertEquals(game.getObjectiveCardDeck().remainingCardIds(), restored.getObjectiveCardDeck().remainingCardIds());
        assertEquals(game.getStarterCardDeck().remainingCardIds(), restored.getStarterCardDeck().remainingCardIds());
        assertEquals(game.getResourceCardDeck().getLeftRevealedCardID(), restored.getResourceCardDeck().getLeftRevealedCardID());
        assertEquals(game.getResourceCardDeck().getRightRevealedCardID(), restored.getResourceCardDeck().getRightRevealedCardID());
        assertEquals(game.getGoldCardDeck().getLeftRevealedCardID(), restored.getGoldCardDeck().getLeftRevealedCardID());
    }

    @Test
    void theCommonObjectivesAndSpareTokensComeBack() {
        Game restored = roundTrip();
        assertEquals(idsOf(game.getCommonObjectives()), idsOf(restored.getCommonObjectives()));
        assertEquals(game.getAvailableTokens(), restored.getAvailableTokens(),
                "a restored game must not hand out a colour someone is already playing");
    }

    @Test
    void theChatComesBackWithItsWhispersIntact() {
        ChatLog restored = GameSnapshot.restore(GameSnapshot.of(game, "table", chatLog)).chatLog();
        assertEquals(2, restored.size());
        assertEquals(2, restored.visibleTo("alice").size());
        assertEquals(2, restored.visibleTo("bob").size());
        assertEquals(1, restored.visibleTo("carol").size(), "a whisper stays private across a restart");
    }

    @Test
    void everyoneComesBackDisconnected() {
        Game restored = roundTrip();
        assertEquals(0, restored.connectedPlayerCount(), "after a restart nobody is on the other end yet");
    }

    @Test
    void aGameThatHasNotStartedSurvivesToo() {
        Game waiting = new Game(2);
        waiting.addPlayer("alice");
        assertEquals(GameState.waitingForPlayers, waiting.getGameState());

        Game restored = GameSnapshot.restore(GameSnapshot.of(waiting, "lobbygame", new ChatLog())).game();
        assertEquals(GameState.waitingForPlayers, restored.getGameState());
        assertNotNull(restored.getPlayer("alice"));
        assertTrue(restored.getTurnOrder().isEmpty());
    }

    private List<Integer> idsOf(List<? extends it.polimi.ingsw.server.model.card.Card> cards) {
        return cards.stream().map(it.polimi.ingsw.server.model.card.Card::getId).toList();
    }
}
