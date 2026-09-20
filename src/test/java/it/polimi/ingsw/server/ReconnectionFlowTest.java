package it.polimi.ingsw.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives real clients over a loopback socket through losing and regaining their connection.
 */
class ReconnectionFlowTest {

    @Test
    void aDroppedPlayerSuspendsInsteadOfClosingTheGame() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            bob.close();

            JSONObject suspended = alice.await("playerSuspended");
            assertEquals("bob", suspended.get("username"));
            assertEquals(0, alice.countWithin("closingGame", 300), "the game must survive a dropped player");
        }
    }

    @Test
    void theRemainingPlayerKeepsTheTurnWhenTheOtherDropsOnTheirTurn() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            List<JSONObject> starts = harness.startGame("g", alice, bob);
            String firstPlayer = starts.getFirst().get("firstPlayer").toString();

            //drop whoever is NOT on turn, so the turn holder can carry on
            TestClient waiting = "alice".equals(firstPlayer) ? bob : alice;
            TestClient onTurn = "alice".equals(firstPlayer) ? alice : bob;
            waiting.close();
            onTurn.await("playerSuspended");

            JSONObject start = "alice".equals(firstPlayer) ? starts.getFirst() : starts.get(1);
            ServerHarness.placeAnyCard(onTurn, (JSONArray) start.get("hand"));
            onTurn.send("directDrawResourceCard");
            JSONObject turn = onTurn.await("turnPlayerUpdate");
            assertEquals(firstPlayer, turn.get("player"), "the absent player's turn must be skipped");
        }
    }

    @Test
    void aReturningPlayerGetsTheWholeGameStateBack() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            bob.close();
            alice.await("playerSuspended");

            TestClient bobAgain = harness.connect("bob-returning");
            bobAgain.send("reconnect", "username", "bob", "gameName", "g");

            JSONObject resumed = bobAgain.await("startGame");
            assertNotNull(resumed.get("hand"));
            assertNotNull(resumed.get("placementHistory"));
            assertNotNull(resumed.get("secretObjectiveID"));
            assertNotNull(resumed.get("alreadyPlaced"));
            bobAgain.await("updatedScores");
            assertEquals("bob", alice.await("playerResumed").get("username"));
        }
    }

    @Test
    void aReturningPlayerCanPlayAgain() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            List<JSONObject> starts = harness.startGame("g", alice, bob);
            String firstPlayer = starts.getFirst().get("firstPlayer").toString();

            //drop the player whose turn it is, then bring them back
            TestClient onTurn = "alice".equals(firstPlayer) ? alice : bob;
            TestClient other = "alice".equals(firstPlayer) ? bob : alice;
            onTurn.close();
            other.await("playerSuspended");

            TestClient returning = harness.connect("returning");
            returning.send("reconnect", "username", firstPlayer, "gameName", "g");
            JSONObject resumed = returning.await("startGame");

            //the turn moved on while they were away, so wait for it to come back round
            other.await("turnPlayerUpdate");
            ServerHarness.placeAnyCard(other, (JSONArray) (("alice".equals(firstPlayer) ? starts.get(1) : starts.getFirst()).get("hand")));
            other.send("directDrawResourceCard");
            returning.await("turnPlayerUpdate");

            assertDoesNotThrow(() -> ServerHarness.placeAnyCard(returning, (JSONArray) resumed.get("hand")));
        }
    }

    @Test
    void theLastPlayerLeftWinsOnceTheWindowCloses() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            harness.lobby().setReconnectTimeout(300);
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            bob.close();
            alice.await("playerSuspended");

            assertEquals("alice", alice.await("gameWonByDefault", 3000).get("username"));
        }
    }

    @Test
    void comingBackInTimeCancelsTheWinByDefault() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            harness.lobby().setReconnectTimeout(1500);
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            bob.close();
            alice.await("playerSuspended");

            TestClient bobAgain = harness.connect("bob-returning");
            bobAgain.send("reconnect", "username", "bob", "gameName", "g");
            bobAgain.await("startGame");

            assertEquals(0, alice.countWithin("gameWonByDefault", 2000), "the window must be cancelled on reconnect");
        }
    }

    @Test
    void reconnectingAsSomeoneWhoIsNotAwayIsRefused() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("g", alice, bob);

            TestClient intruder = harness.connect("intruder");
            intruder.send("reconnect", "username", "alice", "gameName", "g");
            assertNotNull(intruder.await("cannotReconnect").get("reason"));
        }
    }

    @Test
    void reconnectingIntoAGameThatIsGoneIsRefused() throws Exception {
        try (ServerHarness harness = new ServerHarness()) {
            TestClient alice = harness.connect("alice");
            alice.send("reconnect", "username", "ghost", "gameName", "no-such-game");
            assertTrue(alice.await("cannotReconnect").get("reason").toString().contains("no-such-game"));
        }
    }
}
