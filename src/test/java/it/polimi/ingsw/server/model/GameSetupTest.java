package it.polimi.ingsw.server.model;

import it.polimi.ingsw.server.model.card.ObjectiveCard;
import it.polimi.ingsw.util.supportclasses.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameSetupTest {
    private Game game;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        game = new Game(2);
        alice = game.addPlayer("alice");
        bob = game.addPlayer("bob");
    }

    @Test
    void cardsAreDealtOnlyOnceEveryoneIsReady() {
        alice.setReady(true);
        assertFalse(game.dealSetupCardsIfReady());
        bob.setReady(true);
        assertTrue(game.dealSetupCardsIfReady());
        assertEquals(GameState.waitingForCardsSelection, game.getGameState());
        assertNotNull(alice.getStarterCard());
        assertEquals(2, bob.getDrawnObjectiveCards().length);
        assertFalse(game.dealSetupCardsIfReady());
    }

    @Test
    void theStarterCardSideCanOnlyBeChosenOnce() {
        dealCards();
        int starterId = alice.getStarterCard().getId();
        assertFalse(alice.chooseStarterSide(starterId + 100, true));
        assertTrue(alice.chooseStarterSide(starterId, true));
        assertFalse(alice.chooseStarterSide(starterId, false));
        assertEquals(1, alice.getGamefield().getPlacementHistory().size());
    }

    @Test
    void onlyADrawnObjectiveCanBeKeptSecret() {
        dealCards();
        ObjectiveCard offered = alice.getDrawnObjectiveCards()[1];
        assertFalse(alice.chooseSecretObjective(bob.getDrawnObjectiveCards()[0].getId()));
        assertTrue(alice.chooseSecretObjective(offered.getId()));
        assertEquals(offered, alice.getSecretObjective());
    }

    @Test
    void setupIsCompleteWhenEveryoneHasChosenBothCards() {
        dealCards();
        for (Player player : game.getPlayers()) {
            assertFalse(game.setupChoicesComplete());
            player.chooseStarterSide(player.getStarterCard().getId(), true);
            player.chooseSecretObjective(player.getDrawnObjectiveCards()[0].getId());
        }
        assertTrue(game.setupChoicesComplete());
    }

    @Test
    void aLeavingPlayerGivesBackTheirToken() {
        int before = game.getAvailableTokens().size();
        game.removePlayer("alice");
        assertEquals(before + 1, game.getAvailableTokens().size());
        assertFalse(game.isFull());
    }

    private void dealCards() {
        alice.setReady(true);
        bob.setReady(true);
        game.dealSetupCardsIfReady();
    }
}
