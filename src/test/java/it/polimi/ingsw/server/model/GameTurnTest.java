package it.polimi.ingsw.server.model;

import it.polimi.ingsw.util.supportclasses.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static it.polimi.ingsw.util.supportclasses.Constants.SCORE_GOAL;
import static org.junit.jupiter.api.Assertions.*;

class GameTurnTest {
    private Game game;
    private Player bob;

    @BeforeEach
    void setUp() {
        game = new Game(2);
        game.addPlayer("alice");
        bob = game.addPlayer("bob");
        game.startPlaying(List.of("bob", "alice"));
    }

    @Test
    void turnsFollowTheGivenOrderAndWrapAround() {
        assertTrue(game.isTurnOf("bob"));
        assertFalse(game.passTurn());
        assertEquals("alice", game.getTurnPlayer());
        assertFalse(game.passTurn());
        assertEquals("bob", game.getTurnPlayer());
    }

    @Test
    void reachingTheScoreGoalStartsTheLastRoundOnce() {
        assertNull(game.startLastRoundIfDue());
        bob.setScore(SCORE_GOAL);
        assertEquals("player bob has " + SCORE_GOAL + " or more points", game.startLastRoundIfDue());
        assertEquals(GameState.lastRound, game.getGameState());
        assertNull(game.startLastRoundIfDue());
    }

    @Test
    void lastRoundEndsWhenTheTurnGetsBackToTheFirstPlayer() {
        game.passTurn();
        bob.setScore(SCORE_GOAL);
        game.startLastRoundIfDue();
        assertTrue(game.passTurn());
        assertEquals(GameState.endGame, game.getGameState());
    }
}
