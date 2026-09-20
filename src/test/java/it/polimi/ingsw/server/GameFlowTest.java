package it.polimi.ingsw.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameFlowTest {
    private ServerHarness server;
    private TestClient alice;
    private TestClient bob;
    private TestClient first;
    private TestClient second;
    private String secondUsername;
    private JSONObject firstStart;

    @BeforeEach
    void startTwoPlayerGame() throws Exception {
        server = new ServerHarness();
        alice = server.connect("alice");
        bob = server.connect("bob");
        List<JSONObject> starts = server.startGame("table", alice, bob);
        boolean aliceFirst = "alice".equals(starts.getFirst().get("firstPlayer"));
        first = aliceFirst ? alice : bob;
        second = aliceFirst ? bob : alice;
        secondUsername = aliceFirst ? "bob" : "alice";
        firstStart = aliceFirst ? starts.get(0) : starts.get(1);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    @Test
    void playerStartsWithThreeCardsInHand() {
        assertEquals(3, ((JSONArray) firstStart.get("hand")).size());
    }

    @Test
    void placeThenDrawPassesTheTurn() throws Exception {
        ServerHarness.placeAnyCard(first, (JSONArray) firstStart.get("hand"));

        first.send("directDrawResourceCard");
        JSONObject hand = first.await("updatedHand");
        assertEquals(3, ((JSONArray) hand.get("updatedHand")).size());

        String secondName = first == alice ? "bob" : "alice";
        assertEquals(secondName, first.await("turnPlayerUpdate").get("player"));
        assertEquals(secondName, second.await("turnPlayerUpdate").get("player"));
    }

    @Test
    void drawingBeforePlacingIsRejected() throws Exception {
        first.send("directDrawGoldCard");
        assertEquals("You must place a card before drawing", first.await("cannotDraw").get("reason"));
    }

    @Test
    void drawingOutOfTurnIsRejected() throws Exception {
        second.send("drawLeftResourceCard");
        assertEquals("It's not your turn", second.await("cannotDraw").get("reason"));
    }

    @Test
    void placingOutOfTurnIsRejected() throws Exception {
        second.send("place", "placeableCardId", "1", "facingUp", "false", "x", "1", "y", "1");
        assertNotNull(second.await("cannotPlace"));
    }

    @Test
    void leavingMidGameClosesTheGameForTheOthers() throws Exception {
        second.send("leave");
        assertNotNull(first.await("closingGame"));
        assertNotNull(second.await("joinedLobby"));
    }

    @Test
    void droppedConnectionMidGameSuspendsThePlayerAndSparesTheGame() throws Exception {
        second.close();
        // a closed socket is an EOF on the server side, no need to wait for the pinger to give up
        assertNotNull(first.await("playerSuspended", 1500));
        assertEquals(0, first.countWithin("playerSuspended", 500), "playerSuspended must be broadcast once");
        assertEquals(0, first.countWithin("closingGame", 300), "an accidental drop must not end the game");
    }

    @Test
    void droppedClientIsForgottenByTheLobbyButKeepsItsName() throws Exception {
        second.close();
        first.await("playerSuspended", 1500);
        long deadline = System.currentTimeMillis() + 2000;
        while (server.lobby().getConnectedClients().size() != 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, server.lobby().getConnectedClients().size(), "the dead connection must be dropped");

        //the name stays reserved so nobody can steal the seat they are coming back to
        TestClient impostor = server.connect("impostor");
        impostor.send("setUsername", "username", secondUsername);
        assertNotNull(impostor.await("usernameAlreadyTaken"));
    }

    @Test
    void clientsCannotFakeServerSideEvents() throws Exception {
        first.send("connectionLost");
        assertEquals(0, second.countWithin("closingGame", 500));
    }
}
