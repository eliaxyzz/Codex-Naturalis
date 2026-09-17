package it.polimi.ingsw.util.supportclasses;

import it.polimi.ingsw.network.ClientHandler;
import org.json.simple.JSONObject;

/**
 * A handler bound to a single network command, looked up by name instead of
 * living as one branch of a switch statement.
 */
@FunctionalInterface
public interface RequestCommand {
    void execute(ClientHandler client, JSONObject message);
}
