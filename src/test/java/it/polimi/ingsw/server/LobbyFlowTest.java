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
    void outOfRangePlayerCountIsRejectedAndTheLobbyKeepsWorking() throws Exception {
        TestClient alice = server.connect("alice");
        alice.send("setUp", "gameName", "crowd", "numOfPlayers", "9");
        assertNotNull(alice.await("cannotCreateGame"));
        alice.send("setUp", "gameName", "empty", "numOfPlayers", "0");
        assertNotNull(alice.await("cannotCreateGame"));

        alice.send("setUp", "gameName", "fine", "numOfPlayers", "2");
        assertNotNull(alice.await("gameCreated"));
    }

    @Test
    void failedRenameKeepsTheOldNameReserved() throws Exception {
        server.connect("alice");
        TestClient bob = server.connect("bob");
        bob.send("setUsername", "username", "alice");
        bob.await("usernameAlreadyTaken");

        TestClient carol = server.connect("carol");
        carol.send("setUsername", "username", "bob");
        assertNotNull(carol.await("usernameAlreadyTaken"));
    }

    @Test
    void nonObjectJsonDoesNotKillTheConnection() throws Exception {
        TestClient alice = server.connect("alice");
        alice.sendRaw("[]");
        alice.sendRaw("42");
        alice.send("setUsername", "username", "alicia");
        assertEquals("alicia", alice.await("usernameSet").get("username"));
    }

    @Test
    void cardChoicesSentTooEarlyDoNotBreakTheGame() throws Exception {
        TestClient alice = server.connect("alice");
        TestClient bob = server.connect("bob");
        alice.send("setUp", "gameName", "eager", "numOfPlayers", "2");
        alice.await("gameCreated");
        alice.send("starterCard", "starterCardId", "81", "facingUp", "true");
        alice.send("objectiveCard", "objectiveCardId", "87");

        bob.send("join", "gameName", "eager");
        bob.await("joinGame");
        alice.send("ready");
        bob.send("ready");
        assertNotNull(alice.await("cardsSelection"));
        assertNotNull(bob.await("cardsSelection"));
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
