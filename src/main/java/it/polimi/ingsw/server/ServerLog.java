package it.polimi.ingsw.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * The server's activity log, printed on the server console one plain line per event.
 * Game and lobby events are INFO and only show up with "echo on"; problems are WARNING and always do.
 */
public final class ServerLog {
    private static final Logger LOGGER = Logger.getLogger("codex.server");

    static {
        StreamHandler console = new StreamHandler(System.out, new PlainFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        console.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(console);
        LOGGER.setLevel(Level.WARNING);
    }

    private ServerLog() {}

    public static Logger get() {
        return LOGGER;
    }

    public static void setEcho(boolean enabled) {
        LOGGER.setLevel(enabled ? Level.INFO : Level.WARNING);
    }

    public static boolean isEchoOn() {
        return LOGGER.isLoggable(Level.INFO);
    }

    private static class PlainFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String line = formatMessage(record) + System.lineSeparator();
            if (record.getThrown() == null) return line;
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            return line + trace;
        }
    }
}
