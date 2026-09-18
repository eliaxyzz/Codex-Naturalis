package it.polimi.ingsw.network;

import it.polimi.ingsw.network.input.NetworkInputHandler;
import it.polimi.ingsw.network.ping.Pinger;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One end of a line-delimited JSON connection, shared by the server's ClientHandler and the
 * client's ClientConnectionManager: socket, reader thread, keep-alive pings and teardown.
 * Subclasses only decide what an application message means and who to tell when the link dies.
 * <p>
 * Nothing runs until {@link #start()}, so a subclass is fully built (and registered wherever
 * it needs to be) before the first message or disconnect can reach it.
 */
public abstract class Connection implements NetworkInterface {
    private static final JSONObject PONG = new JSONObject(Map.of("type", "pong"));

    private final Socket socket;
    private final PrintWriter out;
    private final NetworkInputHandler networkInputHandler;
    private final Pinger pinger;
    private final Thread inputHandlerThread;
    private final Thread pingerThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    protected Connection(Socket socket) {
        this.socket = socket;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        networkInputHandler = new NetworkInputHandler(this, socket);
        inputHandlerThread = new Thread(networkInputHandler, "net-input");
        pinger = new Pinger(this);
        pingerThread = new Thread(pinger, "net-pinger");
    }

    public void start() {
        inputHandlerThread.start();
        pingerThread.start();
    }

    /**
     * Called for every message that isn't ping/pong traffic.
     */
    protected abstract void onMessage(JSONObject message);

    /**
     * Called at most once, when the other side stops answering or closes the socket.
     * Never called after {@link #shutdown()}.
     */
    protected abstract void onConnectionLost();

    @Override
    public void send(JSONObject message) {
        out.println(message.toJSONString());
    }

    @Override
    public final void notifyIncomingMessageFromSocket(JSONObject message) {
        Object type = message.get("type");
        if ("ping".equals(type)) {
            send(PONG);
        } else if ("pong".equals(type)) {
            pinger.notifyPong();
        } else {
            onMessage(message);
        }
    }

    /**
     * Both the reader (end of stream) and the pinger (no pongs) can decide the link is dead,
     * possibly at the same moment: only the first one gets through.
     */
    @Override
    public final void connectionLossNotification() {
        if (closed.compareAndSet(false, true)) {
            closeResources();
            onConnectionLost();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the connection on purpose. Safe to call more than once and from any thread.
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            closeResources();
        }
    }

    private void closeResources() {
        pinger.shutdown();
        networkInputHandler.shutdown();
        out.close();
        try {
            socket.close();
        } catch (IOException ignored) {
            //already broken, which is the state we wanted anyway
        }
        pingerThread.interrupt();
    }
}
