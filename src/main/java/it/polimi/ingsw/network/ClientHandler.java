package it.polimi.ingsw.network;

import it.polimi.ingsw.server.controller.GameController;
import it.polimi.ingsw.server.lobby.Lobby;
import it.polimi.ingsw.util.supportclasses.Request;
import org.json.simple.JSONObject;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Server side of a client connection. Requests go to the game the client is in, or to the lobby
 * when it's in none. {@code game} is written by the lobby/game threads and read by the network
 * reader, so it's volatile and read once per decision.
 */
public class ClientHandler extends Connection {
    private final Lobby lobby;
    private final InetAddress inetAddress;
    private volatile String username;
    private volatile GameController game;

    public ClientHandler(Socket socket, Lobby lobby) {
        super(socket);
        this.lobby = lobby;
        this.inetAddress = socket.getInetAddress();
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the game this client is playing in, or null while it's in the lobby.
     */
    public GameController getGameController() {
        return game;
    }

    public void setGame(GameController game) {
        this.game = game;
    }

    @Override
    protected void onMessage(JSONObject message) {
        GameController current = game;
        if (current != null) {
            current.submitNewRequest(new Request(this, message));
        } else {
            lobby.submitNewRequest(new Request(this, message));
        }
    }

    @Override
    protected void onConnectionLost() {
        GameController current = game;
        if (current != null) {
            current.notifyConnectionLoss(this);
        } else {
            lobby.notifyConnectionLoss(this);
        }
    }
}
