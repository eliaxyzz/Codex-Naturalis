package it.polimi.ingsw.util.supportclasses;

import it.polimi.ingsw.util.customexceptions.InvalidMessageException;
import org.json.simple.JSONObject;

/**
 * Safe field access for JSON messages coming off the wire. A missing or
 * unparsable field throws instead of letting a NullPointerException or
 * NumberFormatException take down whatever thread is processing the request.
 */
public final class RequestFields {

    private RequestFields() {}

    public static String getCommand(JSONObject message) {
        return getString(message, "command");
    }

    public static String getString(JSONObject message, String key) {
        Object value = message.get(key);
        if (value == null) throw new InvalidMessageException("Missing field '" + key + "'");
        return value.toString();
    }

    public static int getInt(JSONObject message, String key) {
        String value = getString(message, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidMessageException("Field '" + key + "' is not a valid integer: '" + value + "'");
        }
    }

    public static boolean getBoolean(JSONObject message, String key) {
        return Boolean.parseBoolean(getString(message, key));
    }
}
