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
    void droppedConnectionMidGameClosesTheGameForTheOthers() throws Exception {
        second.close();
        assertNotNull(first.await("closingGame", 10_000));
    }
}
