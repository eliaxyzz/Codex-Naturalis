package it.polimi.ingsw.server.model;

import it.polimi.ingsw.util.supportclasses.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSuspensionTest {
    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game(3);
        game.addPlayer("alice");
        game.addPlayer("bob");
        game.addPlayer("carol");
        game.startPlaying(List.of("alice", "bob", "carol"));
    }

    @Test
    void aSuspendedPlayerKeepsTheirSeatAndToken() {
        Player bob = game.getPlayer("bob");
        int tokensBefore = game.getAvailableTokens().size();

        game.suspendPlayer("bob");

        assertSame(bob, game.getPlayer("bob"));
        assertFalse(game.isConnected("bob"));
        assertEquals(tokensBefore, game.getAvailableTokens().size(), "a suspended player's token must stay theirs");
        assertEquals(2, game.connectedPlayerCount());
    }

    @Test
    void turnsSkipSuspendedPlayers() {
        game.suspendPlayer("bob");

        assertTrue(game.isTurnOf("alice"));
        game.passTurn();
        assertEquals("carol", game.getTurnPlayer(), "bob is suspended and must be skipped");
        game.passTurn();
        assertEquals("alice", game.getTurnPlayer());
    }

    @Test
    void aResumedPlayerGetsTheirTurnsBack() {
        game.suspendPlayer("bob");
        game.resumePlayer("bob");

        assertTrue(game.isConnected("bob"));
        assertEquals(3, game.connectedPlayerCount());
        game.passTurn();
        assertEquals("bob", game.getTurnPlayer());
    }

    @Test
    void theLastRoundStillEndsWhenSkippingOverTheFirstPlayer() {
        //carol plays last, alice is suspended: passing from carol wraps past index 0
        game.suspendPlayer("alice");
        game.passTurn();
        assertEquals("bob", game.getTurnPlayer());
        game.passTurn();
        assertEquals("carol", game.getTurnPlayer());

        game.setGameState(GameState.lastRound);
        assertTrue(game.passTurn(), "wrapping past the suspended first player must still end the game");
        assertEquals(GameState.endGame, game.getGameState());
    }

    @Test
    void passingTurnDoesNotSpinWhenEveryoneIsSuspended() {
        game.suspendPlayer("alice");
        game.suspendPlayer("bob");
        game.suspendPlayer("carol");

        assertDoesNotThrow(() -> game.passTurn());
        assertEquals(0, game.connectedPlayerCount());
    }
}
