package it.polimi.ingsw.server.view;

import it.polimi.ingsw.util.cli.CommandParser;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import static it.polimi.ingsw.util.supportclasses.ViewConstants.*;

/**
 * This class parses the commands imparted to the server by the user through the terminal.
 * Each command (and its short alias) maps to a handler that receives the whitespace-split tokens.
 */
public class ServerTerminalParser implements CommandParser {
    private static final int DEFAULT_PORT = 12345;

    private final Map<String, Consumer<String[]>> commands = new HashMap<>();

    public ServerTerminalParser() {
        register(noArgs(() -> ServerView.getInstance().printHelp()), "help", "h");
        register(this::handleEcho, "echo");
        register(this::handleSetPort, "setport", "sp");
        register(noArgs(() -> ServerView.getInstance().showPort()), "port", "p");
        register(noArgs(() -> ServerView.getInstance().showConnectedClients()), "clients");
        register(this::handleGames, "games");
        register(noArgs(() -> ServerView.getInstance().shutdown()), "shutdown");
    }

    private void register(Consumer<String[]> handler, String... names) {
        for (String name : names) {
            commands.put(name, handler);
        }
    }

    @Override
    public void parse(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;
        String[] tokens = trimmed.split("\\s+");
        Consumer<String[]> handler = commands.get(tokens[0].toLowerCase());
        if (handler == null) {
            ServerView.getInstance().parseError();
            return;
        }
        handler.accept(tokens);
    }

    private Consumer<String[]> noArgs(Runnable action) {
        return tokens -> {
            if (tokens.length > 1) System.out.println(INVALID_ARGUMENT_COUNT_MESSAGE);
            else action.run();
        };
    }

    private void handleEcho(String[] tokens) {
        if (tokens.length != 2) {
            System.out.println(INVALID_ARGUMENT_COUNT_MESSAGE);
            return;
        }
        String argument = tokens[1].toLowerCase();
        if (!argument.equals("on") && !argument.equals("off")) {
            System.out.println(INVALID_ARGUMENT_TYPE_MESSAGE);
            return;
        }
        ServerView.getInstance().setEcho(argument.equals("on"));
    }

    private void handleSetPort(String[] tokens) {
        if (tokens.length == 1) {
            ServerView.getInstance().setPort(DEFAULT_PORT);
            return;
        }
        if (tokens.length != 2) {
            System.out.println(INVALID_ARGUMENT_COUNT_MESSAGE);
            return;
        }
        try {
            ServerView.getInstance().setPort(Integer.parseInt(tokens[1]));
        } catch (NumberFormatException e) {
            System.out.println(INVALID_ARGUMENT_TYPE_MESSAGE);
        }
    }

    private void handleGames(String[] tokens) {
        if (tokens.length == 1) {
            ServerView.getInstance().showGames();
        } else if (tokens.length == 3 && tokens[1].equals("--info")) {
            ServerView.getInstance().showGameInfo(tokens[2]);
        } else {
            System.out.println(INVALID_ARGUMENT_COUNT_MESSAGE);
        }
    }
}
