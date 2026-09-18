package it.polimi.ingsw.network.ping;

import it.polimi.ingsw.network.NetworkInterface;
import org.json.simple.JSONObject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static it.polimi.ingsw.util.supportclasses.Constants.*;

/**
 * This class has the role to ping the other host to ensure the connection is still alive.
 * The counter is reset by the reader thread on every pong and decremented here, hence atomic.
 */
public class Pinger implements Runnable, PongObserver {
    private static final JSONObject PING = new JSONObject(Map.of("type", "ping"));

    private final NetworkInterface networkInterface;
    private final AtomicInteger remainingPings = new AtomicInteger(PING_TRIES);
    private volatile boolean running;

    public Pinger(NetworkInterface networkInterface) {
        this.networkInterface = networkInterface;
        running = true;
    }

    @Override
    public void run() {
        while (running) {
            networkInterface.send(PING);
            try {
                Thread.sleep(PING_INTERVAL);
            } catch (InterruptedException e) {
                return; //only happens on shutdown
            }
            if (running && remainingPings.decrementAndGet() <= 0) {
                running = false;
                networkInterface.connectionLossNotification();
            }
        }
    }

    @Override
    public void notifyPong() {
        remainingPings.set(PING_TRIES);
    }

    /**
     * Stops the pinger execution.
     */
    public void shutdown() {
        running = false;
    }
}
