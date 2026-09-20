package it.polimi.ingsw.server.chat;

/**
 * One line of chat. A null recipient means everyone in the game was meant to see it.
 * @param sender Who wrote it.
 * @param recipient Who it was addressed to, or null for the whole table.
 * @param text What they wrote.
 */
public record ChatEntry(String sender, String recipient, String text) {

    public boolean isPrivate() {
        return recipient != null;
    }

    /**
     * @param username The player asking.
     * @return true if that player was ever meant to see this line.
     */
    public boolean isVisibleTo(String username) {
        return !isPrivate() || username.equals(sender) || username.equals(recipient);
    }
}
