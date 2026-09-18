package it.polimi.ingsw.network;

import it.polimi.ingsw.client.controller.ClientController;
import it.polimi.ingsw.util.customexceptions.ServerUnreachableException;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.net.Socket;

/**
 * Client side of the connection to the server: every application message goes to the ClientController.
 */
public class ClientConnectionManager extends Connection {
    private final ClientController clientController;

    public ClientConnectionManager(ClientController clientController, String serverAddress, int port) throws ServerUnreachableException {
        super(openSocket(serverAddress, port));
        this.clientController = clientController;
        start();
    }

    private static Socket openSocket(String serverAddress, int port) throws ServerUnreachableException {
        try {
            return new Socket(serverAddress, port);
        } catch (IOException e) {
            throw new ServerUnreachableException();
        }
    }

    @Override
    protected void onMessage(JSONObject message) {
        clientController.processMessage(message);
    }

    @Override
    protected void onConnectionLost() {
        clientController.notifyConnectionLoss();
    }
}
