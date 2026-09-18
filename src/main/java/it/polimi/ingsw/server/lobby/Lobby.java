package it.polimi.ingsw.server.lobby;

import it.polimi.ingsw.server.ServerLog;
import java.util.logging.Level;
import java.util.logging.Logger;
import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.network.ServerWelcomeSocket;
import it.polimi.ingsw.network.ServerNetworkObserver;
import it.polimi.ingsw.server.controller.GameController;
import it.polimi.ingsw.util.customexceptions.*;
import it.polimi.ingsw.util.supportclasses.Request;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static it.polimi.ingsw.util.supportclasses.Constants.MAX_PLAYERS;
import static it.polimi.ingsw.util.supportclasses.Constants.MIN_PLAYERS;

/**
 * This class represents the lobby where players can create or join a game and set their usernames.
 * Lobby state changes on the lobby thread: requests, new connections and disconnects are queued
 * as tasks. The collections are still concurrent because games (closing, going unavailable) and
 * the server console touch them from their own threads.
 */
public class Lobby implements ServerNetworkObserver {
    private static final Logger LOG = ServerLog.get();

    private final BlockingQueue<Runnable> tasks;
    private final List<ClientHandler> connectedClients;
    private final Map<String, GameController> games;
    private final Map<String, GameController> availableGames;
    private final Set<String> takenUsernames;
    private ServerWelcomeSocket serverWelcomeSocket = null;
    private boolean welcomeSocketIsRunning = false;
    private int welcomeSocketPort;
    private final ExecutorService executorService;
    private final LobbyRequestHandler lobbyRequestHandler;
    private volatile boolean running;

    public Lobby() {
        connectedClients = new CopyOnWriteArrayList<>();
        games = new ConcurrentHashMap<>();
        availableGames = new ConcurrentHashMap<>();
        takenUsernames = ConcurrentHashMap.newKeySet();
        tasks = new LinkedBlockingQueue<>();
        executorService = Executors.newCachedThreadPool();
        lobbyRequestHandler = new LobbyRequestHandler(this);
        running = true;

    }

    /**
     * Initializes the welcome socket that is used by the server to listen for new connections.
     * @param port The port to listen on.
     * @throws CannotOpenWelcomeSocket Thrown when the welcome socket can't be opened on the specified port.
     * @throws WelcomeSocketIsAlreadyOpenException Thrown when the server is already listening on a port.
     */
    public void initializeWelcomeSocket(int port) throws CannotOpenWelcomeSocket, WelcomeSocketIsAlreadyOpenException {
        if (!welcomeSocketIsRunning) {
            serverWelcomeSocket = new ServerWelcomeSocket(this, port);
            executorService.submit(serverWelcomeSocket);
            welcomeSocketPort = port;
            welcomeSocketIsRunning = true;
        }
        else throw new WelcomeSocketIsAlreadyOpenException(String.valueOf(welcomeSocketPort));
    }

    public Map<String, GameController> getAvailableGames() {
        return availableGames;
    }

    public Map<String, GameController> getGames() {
        return games;
    }

    public List<ClientHandler> getConnectedClients() {
        return connectedClients;
    }

    public int getWelcomeSocketPort() {
        return welcomeSocketPort;
    }

    /**
     * Starts the lobby execution that continuously processes requests from clients until the server is shut down.
     */
    public void startLobby() {
        System.out.println("Lobby started");
        System.out.println("Echo: off");
        System.out.println("Set a port for the server with 'setport' command.");
        System.out.println("Type 'help' for more information.");
        System.out.println();
        while (running) {
            try {
                tasks.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                //one broken request must not take down the lobby, and with it every client still in it
                LOG.log(Level.WARNING, "Lobby: dropping a request that failed", e);
            }
        }
    }

    /**
     * Adds a new client to the lobby.
     * @param client New client handler.
     */
    public void submitNewClient(ClientHandler client) {
        tasks.add(() -> {
            enterLobby(client);
            setRandomGuestUsername(client);
            client.send(LobbyMessageGenerator.usernameSetMessage(client.getUsername()));
            //only now can messages or a disconnect come in, and the client already has a name
            client.start();
        });
    }

    /**
     * Sets a random username to the client.
     * @param client The client handler that needs to be assigned with a random username.
     */
    private void setRandomGuestUsername(ClientHandler client){
        boolean usernameNotSet = true;
        while (usernameNotSet) {
            String username = "Guest" + (int) (Math.random() * 100000);
            try {
                setUsername(username,client);
                usernameNotSet = false;
            } catch (AlreadyTakenUsernameException ignored) {
            }
        }
    }

    /**
     * Submits a new request to lobby.
     * @param request The new request.
     */
    public void submitNewRequest(Request request) {
        tasks.add(() -> lobbyRequestHandler.execute(request));
    }

    /**
     * Adds the client to the lobby's arraylist of connected clients.
     * @param client The client to allow in.
     */
    public void enterLobby(ClientHandler client) {
        if(!connectedClients.contains(client)) {
            connectedClients.add(client);
        }
        LOG.info(() -> "Client '" + (client.getUsername() == null ? "" : client.getUsername()) + "' is now in the lobby");
        client.send(LobbyMessageGenerator.joinedLobbyMessage());
    }

    /**
     * Removes the client from the lobby.
     * @param client The client to allow in.
     */
    public void leaveLobby(ClientHandler client) {
        connectedClients.remove(client);
        if (client.getUsername() != null) {
            takenUsernames.remove(client.getUsername());
        }
        LOG.info(() -> "Client '" + client.getUsername() + "' left the lobby");
        client.shutdown();
    }

    /**
     * Allows the client to pick a username, the username is added to the taken usernames list to ensure the uniqueness.
     * @param username The chosen username.
     * @param client The client that is setting the username.
     * @throws AlreadyTakenUsernameException Thrown when trying to choose an already taken username.
     */
    public void setUsername(String username, ClientHandler client) throws AlreadyTakenUsernameException {
        if (username.equals(client.getUsername())) return;
        //claim the new name first: add() is atomic, so two clients racing for it can't both win,
        //and a failed rename leaves the old name reserved
        if (!takenUsernames.add(username)) {
            throw new AlreadyTakenUsernameException();
        }
        String oldUsername = client.getUsername();
        client.setUsername(username);
        if (oldUsername != null) {
            takenUsernames.remove(oldUsername);
            LOG.info(() -> "Client '" + oldUsername + "' changed their username to '" + username + "'");
        }
    }

    /**
     * Creates a new game with the requested number of players and assign a name to it.
     * The creator joins it straight away and gets the gameCreated confirmation from the game thread.
     * @param numberOfPlayers The number of players that will join.
     * @param gameName The name to identify the game.
     */
    public void setupNewGame(int numberOfPlayers, String gameName, ClientHandler client) throws CannotCreateGameException {
        if(gameName == null) throw new CannotCreateGameException("Invalid name!");
        if(numberOfPlayers < MIN_PLAYERS || numberOfPlayers > MAX_PLAYERS) {
            throw new CannotCreateGameException("A game needs " + MIN_PLAYERS + " to " + MAX_PLAYERS + " players!");
        }
        if(games.containsKey(gameName)) throw new CannotCreateGameException("Game name already taken!");
        GameController newGameController = new GameController(this, numberOfPlayers, gameName);
        games.put(gameName, newGameController);
        availableGames.put(gameName,newGameController);
        newGameController.submitJoin(client, LobbyMessageGenerator.createdGameMessage());
        executorService.submit(newGameController);
    }

    /**
     * Removes a game from the list of available games.
     * @param gameName The game name.
     */
    public void makeUnavailable(String gameName) {
        availableGames.remove(gameName);
    }

    /**
     * CLoses the game from the lobby.
     * @param gameName The game name.
     */
    public void closeGame(String gameName) {
        makeUnavailable(gameName);
        games.remove(gameName);
    }

    /**
     * Allows a client to join a game that is waiting for players. The game itself answers
     * with joinGame or gameIsFull once it has processed the request.
     * @param client The client that wants to join.
     * @param gameName The name of the game to join.
     * @throws NonExistentGameException Thrown when the given game name isn't the name of one of the available games to join.
     */
    public void joinGame(ClientHandler client, String gameName) throws NonExistentGameException {
        GameController gameController = availableGames.get(gameName);
        if (gameController == null) { throw new NonExistentGameException(); }
        gameController.submitJoin(client, LobbyMessageGenerator.joinGameMessage(gameName));
    }

    @Override
    public void notifyConnectionLoss(ClientHandler clientHandler) {
        LOG.info(() -> "Client '" + clientHandler.getUsername() + "' lost connection");
        tasks.add(() -> leaveLobby(clientHandler));
    }

    /**
     * Stops the lobby execution, draining the client-handling thread pool before returning.
     * Does not terminate the JVM: the caller decides when to exit the process.
     */
    public void shutdown() {
        running = false;
        if (serverWelcomeSocket != null) {
            serverWelcomeSocket.shutdown();
        }
        if (executorService != null) {
            //games sit in tasks.take() and only stop on interrupt
            executorService.shutdownNow();
            try {
                boolean terminatedCleanly = executorService.awaitTermination(5, TimeUnit.SECONDS);
                if (!terminatedCleanly) {
                    System.out.println("Lobby shutdown: some games didn't stop within the timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        //startLobby() is blocked on tasks.take(): wake it up so it can observe running == false
        tasks.add(() -> {});
    }
}
