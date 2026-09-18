package it.polimi.ingsw.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Raw-socket client speaking the same line-delimited JSON as the real one.
 * Answers pings on its own so the server doesn't drop it during slow tests;
 * everything else is queued for the test to assert on.
 */
public class TestClient implements AutoCloseable {
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final PrintWriter out;
    private final BlockingQueue<JSONObject> inbox = new LinkedBlockingQueue<>();
    private final List<JSONObject> seen = new ArrayList<>();
    private volatile boolean answerPings = true;

    public TestClient(int port) throws IOException {
        socket = new Socket("localhost", port);
        out = new PrintWriter(socket.getOutputStream(), true);
        Thread reader = new Thread(this::readLoop, "test-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        JSONParser parser = new JSONParser();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                JSONObject message = (JSONObject) parser.parse(line);
                if ("ping".equals(message.get("type"))) {
                    if (answerPings) sendRaw("{\"type\":\"pong\"}");
                } else if (!message.containsKey("type")) {
                    inbox.add(message);
                }
            }
        } catch (IOException | ParseException ignored) {
            // socket closed by either side, the test decides whether that was expected
        }
    }

    public void sendRaw(String line) {
        out.println(line);
    }

    public void send(String command, String... keyValues) {
        Map<String, String> fields = new HashMap<>();
        fields.put("command", command);
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put(keyValues[i], keyValues[i + 1]);
        }
        out.println(new JSONObject(fields).toJSONString());
    }

    /**
     * Waits for the next message of the given kind, skipping (but remembering) anything else.
     */
    public JSONObject await(String messageName) throws InterruptedException {
        return await(messageName, DEFAULT_TIMEOUT_MS);
    }

    public JSONObject await(String messageName, long timeoutMs) throws InterruptedException {
        return awaitAny(timeoutMs, messageName);
    }

    public JSONObject awaitAny(long timeoutMs, String... messageNames) throws InterruptedException {
        List<String> wanted = List.of(messageNames);
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (JSONObject old : seen) {
            if (wanted.contains(old.get("message"))) {
                seen.remove(old);
                return old;
            }
        }
        while (true) {
            long left = deadline - System.currentTimeMillis();
            JSONObject message = left > 0 ? inbox.poll(left, TimeUnit.MILLISECONDS) : null;
            if (message == null) {
                throw new AssertionError("No " + wanted + " within " + timeoutMs + "ms, got: " + seen);
            }
            if (wanted.contains(message.get("message"))) return message;
            seen.add(message);
        }
    }

    /**
     * Collects every message that arrives in the next {@code windowMs} and counts the ones with the given name.
     */
    public int countWithin(String messageName, long windowMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + windowMs;
        long left;
        while ((left = deadline - System.currentTimeMillis()) > 0) {
            JSONObject message = inbox.poll(left, TimeUnit.MILLISECONDS);
            if (message != null) seen.add(message);
        }
        int count = 0;
        for (JSONObject message : seen) {
            if (messageName.equals(message.get("message"))) count++;
        }
        seen.removeIf(message -> messageName.equals(message.get("message")));
        return count;
    }

    public void stopAnsweringPings() {
        answerPings = false;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
