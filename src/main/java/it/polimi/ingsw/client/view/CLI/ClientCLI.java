package it.polimi.ingsw.client.view.CLI;

import it.polimi.ingsw.client.controller.ClientController;
import it.polimi.ingsw.client.view.StageManager;
import it.polimi.ingsw.util.customexceptions.ServerUnreachableException;
import java.util.Scanner;

/**
 * This class provides the command-line interface (CLI) for the Codex game client.
 * It handles user interactions, displays menus, and retrieves user input.
 */
public class ClientCLI {
    private static final int DEFAULT_PORT = 12345;

    private static ClientCLI instance;
    private final ClientTerminalInputReader clientTerminalInputReader;
    private final Thread clientTerminalInputThread;

    private ClientCLI() {
        clientTerminalInputReader = new ClientTerminalInputReader();
        clientTerminalInputThread = new Thread(clientTerminalInputReader);
    }

    /**
     * Returns the existing instance of ClientCLI (Singleton pattern).
     * @return The ClientCLI instance.
     */
    public static ClientCLI getInstance() {
        if (instance == null) { instance = new ClientCLI(); }
        return instance;
    }

    /**
     * Starts the CLI by prompting for server address and port.
     */
    public void start() {
        //one scanner for stdin: a second one can swallow buffered input
        Scanner scanner = new Scanner(System.in);
        String address = getServerAddress(scanner);
        int port = getServerPort(scanner);
        StageManager.enableCLIMode();
        try {
            ClientController.connect(address, port);
        } catch (ServerUnreachableException e) {
            System.out.println("Could not connect to server.");
            System.exit(1);
        }
        clientTerminalInputThread.start();
    }

    /**
     * Prompts the user for the server's IP address. Allows using Enter for the default value.
     * @return The server's IP address entered by the user.
     */
    private String getServerAddress(Scanner scanner) {
        System.out.println("Insert server IP address (press Enter for set default IP: localhost)");
        String address = scanner.nextLine().trim();
        return address.isEmpty() ? "localhost" : address;
    }

    /**
     * Prompts the user for the server's port number. Allows using Enter for the default value.
     * Handles invalid input and uses the default port if necessary.
     * @return The server's port number entered by the user.
     */
    private int getServerPort(Scanner scanner) {
        System.out.println("Insert server port (press Enter for set default port: " + DEFAULT_PORT + ")");
        String portString = scanner.nextLine().trim();
        if (portString.isEmpty()) return DEFAULT_PORT;
        try {
            int port = Integer.parseInt(portString);
            //out of range would blow up in the Socket constructor instead of being reported here
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Using default port: " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /**
     * Clears the console screen.
     */
    public static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /**
     * Shuts down the client and terminates the process.
     */
    public void shutdown() {
        clientTerminalInputReader.shutdown();
        ClientController.getInstance().shutdown();
        System.exit(0);
    }
}
