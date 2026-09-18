package it.polimi.ingsw.network.input;

import it.polimi.ingsw.network.NetworkInterface;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * This class handles the input from the TCP socket.
 * End of stream means the other side is gone, so it's reported straight away
 * instead of waiting for the pinger to run out of tries.
 */
public class NetworkInputHandler implements Runnable {

    private final NetworkInterface networkInterface;
    private final BufferedReader in;
    private final JSONParser parser;
    private volatile boolean running;

    public NetworkInputHandler(NetworkInterface networkInterface, Socket socket) {
        parser = new JSONParser();
        this.networkInterface = networkInterface;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e); //should not happen
        }
        running = true;
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException ignored) {
            //connection reset, same as end of stream
        }
        //if running is false we closed the socket ourselves, nobody needs to hear about it
        if (running) {
            running = false;
            networkInterface.connectionLossNotification();
        }
    }

    private void handleLine(String line) {
        Object parsed;
        try {
            parsed = parser.parse(line);
        } catch (ParseException e) {
            System.out.println("Discarding malformed message: " + e.getMessage());
            return;
        }
        if (parsed instanceof JSONObject message) {
            networkInterface.notifyIncomingMessageFromSocket(message);
        } else {
            System.out.println("Discarding message that is not a JSON object: " + line);
        }
    }

    /**
     * Stops the InputHandler execution.
     */
    public void shutdown() {
        running = false;
    }
}
