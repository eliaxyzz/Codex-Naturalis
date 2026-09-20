package it.polimi.ingsw.server;

import it.polimi.ingsw.server.lobby.Lobby;
import it.polimi.ingsw.server.persistence.GameStore;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays a game on one server, stops it, and brings a second server up on the same save
 * directory - the way a restart actually looks.
 */
class PersistenceFlowTest {
    private final List<AutoCloseable> toClose = new ArrayList<>();
    private Path saveDirectory;

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable closeable : toClose) {
            closeable.close();
        }
        if (saveDirectory != null && Files.isDirectory(saveDirectory)) {
            try (var paths = Files.walk(saveDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    /**
     * A second server on the same save directory, standing in for a restart.
     */
    private RestartedServer restart() throws Exception {
        RestartedServer server = new RestartedServer(saveDirectory);
        toClose.add(server);
        return server;
    }

    private static class RestartedServer implements AutoCloseable {
        final Lobby lobby = new Lobby();
        final int port;
        final int restored;
        final List<TestClient> clients = new ArrayList<>();

        RestartedServer(Path saveDirectory) throws Exception {
            try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
                port = probe.getLocalPort();
            }
            lobby.setGameStore(new GameStore(saveDirectory));
            restored = lobby.restoreSavedGames();
            lobby.initializeWelcomeSocket(port);
            Thread thread = new Thread(lobby::startLobby, "restarted-lobby");
            thread.setDaemon(true);
            thread.start();
        }

        TestClient connect() throws Exception {
            TestClient client = new TestClient(port);
            clients.add(client);
            client.await("joinedLobby");
            client.await("usernameSet");
            return client;
        }

        @Override
        public void close() throws IOException {
            for (TestClient client : clients) client.close();
            lobby.shutdown();
        }
    }

    @Test
    void aGameInProgressIsWrittenToDisk() throws Exception {
        saveDirectory = Files.createTempDirectory("codex-restart");
        try (ServerHarness harness = new ServerHarness(saveDirectory)) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("table", alice, bob);

            waitForSave();
            assertEquals(1, savedFiles().size(), "a running game must be on disk");
        }
    }

    @Test
    void aRestartedServerBringsTheGameBackAndLetsItsPlayersIn() throws Exception {
        String firstPlayer;
        List<JSONObject> starts;
        saveDirectory = Files.createTempDirectory("codex-restart");
        try (ServerHarness harness = new ServerHarness(saveDirectory)) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            starts = harness.startGame("table", alice, bob);
            firstPlayer = starts.getFirst().get("firstPlayer").toString();
            //play a turn so there is real state to bring back
            TestClient onTurn = "alice".equals(firstPlayer) ? alice : bob;
            JSONObject start = "alice".equals(firstPlayer) ? starts.getFirst() : starts.get(1);
            ServerHarness.placeAnyCard(onTurn, (JSONArray) start.get("hand"));
            onTurn.send("directDrawResourceCard");
            onTurn.await("turnPlayerUpdate");
            waitForSave();
        }
        //the first server is gone, its games only exist on disk now
        RestartedServer server = restart();
        assertEquals(1, server.restored, "the saved game must come back");

        TestClient returning = server.connect();
        returning.send("reconnect", "username", firstPlayer, "gameName", "table");
        JSONObject resumed = returning.await("startGame");
        assertEquals(3, ((JSONArray) resumed.get("hand")).size(), "their hand must survive the restart");
        assertFalse(((JSONArray) resumed.get("placementHistory")).isEmpty(), "their field must survive the restart");
    }

    @Test
    void bothPlayersCanComeBackAndKeepPlaying() throws Exception {
        saveDirectory = Files.createTempDirectory("codex-restart");
        try (ServerHarness harness = new ServerHarness(saveDirectory)) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("table", alice, bob);
            waitForSave();
        }
        RestartedServer server = restart();

        TestClient aliceAgain = server.connect();
        aliceAgain.send("reconnect", "username", "alice", "gameName", "table");
        JSONObject aliceState = aliceAgain.await("startGame");
        TestClient bobAgain = server.connect();
        bobAgain.send("reconnect", "username", "bob", "gameName", "table");
        JSONObject bobState = bobAgain.await("startGame");
        aliceAgain.await("playerResumed");

        String turn = bobState.get("firstPlayer").toString();
        TestClient onTurn = "alice".equals(turn) ? aliceAgain : bobAgain;
        JSONObject state = "alice".equals(turn) ? aliceState : bobState;
        assertDoesNotThrow(() -> ServerHarness.placeAnyCard(onTurn, (JSONArray) state.get("hand")),
                "a restored game must be playable again");
    }

    @Test
    void theChatSurvivesARestartWithItsWhispersStillPrivate() throws Exception {
        saveDirectory = Files.createTempDirectory("codex-restart");
        try (ServerHarness harness = new ServerHarness(saveDirectory)) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            TestClient carol = harness.connect("carol");
            harness.startGame("table", alice, bob, carol);
            alice.send("chat", "text", "see you after the restart");
            alice.send("chat", "text", "not for carol", "recipient", "bob");
            bob.await("chatMessage");
            bob.await("chatMessage");
            waitForSave();
        }
        RestartedServer server = restart();

        TestClient carolAgain = server.connect();
        carolAgain.send("reconnect", "username", "carol", "gameName", "table");
        carolAgain.await("startGame");
        assertEquals("see you after the restart", carolAgain.await("chatMessage").get("text"));
        assertEquals(0, carolAgain.countWithin("chatMessage", 400),
                "a whisper must stay private across a restart too");
    }

    @Test
    void aRestoredGameHoldsItsPlayersNames() throws Exception {
        saveDirectory = Files.createTempDirectory("codex-restart");
        try (ServerHarness harness = new ServerHarness(saveDirectory)) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("table", alice, bob);
            waitForSave();
        }
        RestartedServer server = restart();

        TestClient impostor = server.connect();
        impostor.send("setUsername", "username", "alice");
        assertNotNull(impostor.await("usernameAlreadyTaken"),
                "a seat in a restored game must not be up for grabs");
    }

    @Test
    void anUnreadableSaveIsSkippedInsteadOfStoppingTheServer() throws Exception {
        saveDirectory = Files.createTempDirectory("codex-broken-saves");
        Files.writeString(saveDirectory.resolve("rubbish.json"), "{not json at all", StandardCharsets.UTF_8);

        RestartedServer server = restart();
        assertEquals(0, server.restored);
        //the server is still perfectly usable
        TestClient client = server.connect();
        client.send("setUsername", "username", "alice");
        assertNotNull(client.await("usernameSet"));
    }

    @Test
    void aFinishedGameLeavesNothingBehind() throws Exception {
        saveDirectory = Files.createTempDirectory("codex-restart");
        try (ServerHarness harness = new ServerHarness(saveDirectory)) {
            TestClient alice = harness.connect("alice");
            TestClient bob = harness.connect("bob");
            harness.startGame("table", alice, bob);
            waitForSave();
            assertEquals(1, savedFiles().size());

            //both leave on purpose: there is nothing to come back to
            alice.send("leave");
            bob.send("leave");
            alice.await("joinedLobby");
            bob.await("joinedLobby");

            long deadline = System.currentTimeMillis() + 3000;
            while (!savedFiles().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(savedFiles().isEmpty(), "an abandoned game must not be left on disk");
        }
    }

    private List<Path> savedFiles() throws IOException {
        if (saveDirectory == null || !Files.isDirectory(saveDirectory)) return List.of();
        try (var paths = Files.list(saveDirectory)) {
            return paths.filter(path -> path.toString().endsWith(".json")).toList();
        }
    }

    private void waitForSave() throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (savedFiles().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }
}
