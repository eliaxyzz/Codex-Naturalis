package it.polimi.ingsw.util.supportclasses;

/**
 * One line of chat as the client holds it.
 * @param sender Who wrote it.
 * @param recipient Who it was addressed to, or null if it went to the whole table.
 * @param text What they wrote.
 */
public record ChatLine(String sender, String recipient, String text) {

    public boolean isPrivate() {
        return recipient != null;
    }

    /**
     * @param myUsername The name of the player reading the chat.
     * @return The line as it should be shown to them.
     */
    public String format(String myUsername) {
        String who = sender.equals(myUsername) ? "you" : sender;
        if (!isPrivate()) return who + ": " + text;
        String to = recipient.equals(myUsername) ? "you" : recipient;
        return "[" + who + " -> " + to + "] " + text;
    }
}
