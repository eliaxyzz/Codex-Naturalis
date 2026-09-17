package it.polimi.ingsw.util.customexceptions;

/**
 * Thrown when an incoming JSON message is missing a required field or has a
 * field that can't be parsed into the expected type.
 */
public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String message) {
        super(message);
    }
}
