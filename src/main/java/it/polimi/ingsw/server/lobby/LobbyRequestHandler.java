package it.polimi.ingsw.server.lobby;

import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.util.customexceptions.AlreadyTakenUsernameException;
import it.polimi.ingsw.util.customexceptions.CannotCreateGameException;
import it.polimi.ingsw.util.customexceptions.GameIsFullException;
import it.polimi.ingsw.util.customexceptions.InvalidMessageException;
import it.polimi.ingsw.util.customexceptions.NonExistentGameException;
import it.polimi.ingsw.util.supportclasses.Request;
import it.polimi.ingsw.util.supportclasses.RequestCommand;
import it.polimi.ingsw.util.supportclasses.RequestFields;
import org.json.simple.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents the request parser of the exchanged messages.
 */
public class LobbyRequestHandler {
    private final Lobby lobby;
    private final Map<String, RequestCommand> commands = new HashMap<>();

    public LobbyRequestHandler(Lobby lobby) {
        this.lobby = lobby;
        commands.put("setUsername", (client, message) -> setUsername(lobby, message, client));
        commands.put("getAvailableGames", (client, message) -> getAvailableGames(lobby, client));
        commands.put("setUp", (client, message) -> setUpGame(lobby, message, client));
        commands.put("join", (client, message) -> joinGame(lobby, message, client));
        commands.put("leave", (client, message) -> leaveLobby(lobby, client));
        commands.put("connectionLost", (client, message) -> leaveLobby(lobby, client));
    }
    /**
     * Handles the incoming request from a client
     * @param request request to be processed
     */
    public void execute(Request request) {
        JSONObject message = request.message();
        ClientHandler clientHandler = request.client();
        String command;
        try {
            command = RequestFields.getCommand(message);
        } catch (InvalidMessageException e) {
            System.out.println("Discarding malformed request from client: " + e.getMessage());
            return;
        }
        RequestCommand handler = commands.get(command);
        if (handler == null) return; //unrecognized command, discarded
        try {
            handler.execute(clientHandler, message);
        } catch (InvalidMessageException e) {
            System.out.println("Discarding malformed '" + command + "' request from client: " + e.getMessage());
        }
    }

    /**
     * Handles the logic for setting a username for a client.
     * @param lobby lobby reference
     * @param message json object message
     * @param clientHandler client handler of client
     */
    private void setUsername(Lobby lobby, JSONObject message, ClientHandler clientHandler) {
        try {
            lobby.setUsername(RequestFields.getString(message, "username"),clientHandler);
            clientHandler.send(LobbyMessageGenerator.usernameSetMessage(clientHandler.getUsername()));
        } catch (AlreadyTakenUsernameException e) {
             clientHandler.send(LobbyMessageGenerator.usernameAlreadyTakenMessage());
        }

    }

    /**
     * retrieves a list of available games from the lobby and sends them to the requesting client
     * @param lobby lobby reference
     * @param clientHandler client handler of client
     */
    private void getAvailableGames(Lobby lobby, ClientHandler clientHandler) {
        clientHandler.send(LobbyMessageGenerator.getAvailableGamesMessage(lobby.getAvailableGames()));
    }

    /**
     * processes a request to create a new game
     * @param lobby lobby reference
     * @param message json object message
     * @param clientHandler client handler of client
     */
    private void setUpGame(Lobby lobby, JSONObject message, ClientHandler clientHandler) {
        int numberOfPlayers = RequestFields.getInt(message, "numOfPlayers");
        String gameName = RequestFields.getString(message, "gameName");

        try {
            lobby.setupNewGame(numberOfPlayers,gameName,clientHandler);
            clientHandler.send(LobbyMessageGenerator.createdGameMessage());
        } catch (CannotCreateGameException e) {
            clientHandler.send(LobbyMessageGenerator.cannotCreateGameMessage(e.getMessage()));
        }
    }

    /**
     * handles a client's request to join a game
     * @param lobby lobby reference
     * @param message json object message
     * @param clientHandler client handler of client
     */
    private void joinGame(Lobby lobby, JSONObject message, ClientHandler clientHandler) {
        String gameName = RequestFields.getString(message, "gameName");
        try {
            lobby.joinGame(clientHandler,gameName);
            clientHandler.send(LobbyMessageGenerator.joinGameMessage(gameName));
        } catch (NonExistentGameException e) {
            clientHandler.send(LobbyMessageGenerator.gameDoesNotExistMessage());
        } catch (GameIsFullException e) {
            clientHandler.send(LobbyMessageGenerator.gameIsFullMessage());
        }

    }

    /**
     * removes a client from the lobby when they choose to leave
     * @param lobby lobby reference
     * @param clientHandler client handler of client
     */
    private void leaveLobby(Lobby lobby, ClientHandler clientHandler) {
        lobby.leaveLobby(clientHandler);
    }

}
