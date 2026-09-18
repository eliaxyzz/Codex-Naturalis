package it.polimi.ingsw.server;

import it.polimi.ingsw.server.lobby.Lobby;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * A real Lobby listening on a free local port, plus shortcuts to drive clients
 * through the setup phases so each test can start from the state it cares about.
 */
public class ServerHarness implements AutoCloseable {
    private final Lobby lobby;
    private final int port;
    private final List<TestClient> clients = new ArrayList<>();

    public ServerHarness() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        lobby = new Lobby();
        lobby.initializeWelcomeSocket(port);
        Thread lobbyThread = new Thread(lobby::startLobby, "lobby");
        lobbyThread.setDaemon(true);
        lobbyThread.start();
    }

    public Lobby lobby() {
        return lobby;
    }

    public TestClient connect(String username) throws IOException, InterruptedException {
        TestClient client = new TestClient(port);
        clients.add(client);
        client.await("joinedLobby");
        client.await("usernameSet");
        client.send("setUsername", "username", username);
        client.await("usernameSet");
        return client;
    }

    /**
     * Creates a game for the first client, joins the others and gets everyone
     * past ready + starter card + secret objective, returning once each client has "startGame".
     */
    public List<JSONObject> startGame(String gameName, TestClient... players) throws InterruptedException {
        players[0].send("setUp", "gameName", gameName, "numOfPlayers", String.valueOf(players.length));
        players[0].await("gameCreated");
        for (int i = 1; i < players.length; i++) {
            players[i].send("join", "gameName", gameName);
            players[i].await("joinGame");
        }
        for (TestClient player : players) {
            player.send("ready");
        }
        for (TestClient player : players) {
            JSONObject selection = player.await("cardsSelection");
            player.send("starterCard", "starterCardId", selection.get("starterCardID").toString(), "facingUp", "true");
            player.send("objectiveCard", "objectiveCardId", selection.get("objectiveCardID1").toString());
        }
        List<JSONObject> starts = new ArrayList<>();
        for (TestClient player : players) {
            starts.add(player.await("startGame"));
        }
        return starts;
    }

    /**
     * Places the first card in hand face down next to the starter card, trying each diagonal
     * until the server accepts it (which diagonals are open depends on the random starter card).
     */
    public static JSONObject placeAnyCard(TestClient player, JSONArray hand) throws InterruptedException {
        String cardId = hand.getFirst().toString();
        int[][] spots = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] spot : spots) {
            player.send("place", "placeableCardId", cardId, "facingUp", "false",
                    "x", String.valueOf(spot[0]), "y", String.valueOf(spot[1]));
            JSONObject reply = player.awaitAny(5000, "successfulPlace", "cannotPlace");
            if ("successfulPlace".equals(reply.get("message"))) return reply;
        }
        throw new AssertionError("No diagonal around the starter card accepted card " + cardId);
    }

    @Override
    public void close() throws IOException {
        for (TestClient client : clients) {
            client.close();
        }
        lobby.shutdown();
    }
}
