package it.polimi.ingsw.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyFlowTest {
    private ServerHarness server;

    @BeforeEach
    void setUp() throws Exception {
        server = new ServerHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    @Test
    void takenUsernameIsRejected() throws Exception {
        server.connect("alice");
        TestClient other = server.connect("bob");
        other.send("setUsername", "username", "alice");
        assertNotNull(other.await("usernameAlreadyTaken"));
    }

    @Test
    void joiningAMissingGameIsRejected() throws Exception {
        TestClient alice = server.connect("alice");
        alice.send("join", "gameName", "nope");
        assertNotNull(alice.await("gameDoesNotExist"));
    }

    @Test
    void joiningAFullGameIsRejected() throws Exception {
        TestClient alice = server.connect("alice");
        TestClient bob = server.connect("bob");
        TestClient carol = server.connect("carol");
        alice.send("setUp", "gameName", "duo", "numOfPlayers", "2");
        alice.await("gameCreated");
        bob.send("join", "gameName", "duo");
        bob.await("joinGame");
        carol.send("join", "gameName", "duo");
        assertNotNull(carol.awaitAny(5000, "gameIsFull", "gameDoesNotExist"));
    }

    @Test
    void createdGameShowsUpInTheAvailableList() throws Exception {
        TestClient alice = server.connect("alice");
        TestClient bob = server.connect("bob");
        alice.send("setUp", "gameName", "trio", "numOfPlayers", "3");
        alice.await("gameCreated");
        bob.send("getAvailableGames");
        JSONArray games = (JSONArray) bob.await("availableGames").get("games");
        assertEquals("trio - (1/3)", ((JSONObject) games.getFirst()).get("nameAndPlayers"));
    }

    @Test
    void duplicateGameNameIsRejected() throws Exception {
        TestClient alice = server.connect("alice");
        TestClient bob = server.connect("bob");
        alice.send("setUp", "gameName", "same", "numOfPlayers", "2");
        alice.await("gameCreated");
        bob.send("setUp", "gameName", "same", "numOfPlayers", "2");
        assertEquals("Game name already taken!", bob.await("cannotCreateGame").get("reason"));
    }
}
