package it.polimi.ingsw.server.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import static it.polimi.ingsw.util.supportclasses.Constants.CHAT_HISTORY_SIZE;

/**
 * The recent chat of one game, kept so a player who comes back can catch up.
 * Only the owning game's thread touches it.
 */
public class ChatLog {
    private final Deque<ChatEntry> entries = new ArrayDeque<>();

    /**
     * Records a line, dropping the oldest one once the log is full.
     * @param entry The line to record.
     */
    public void add(ChatEntry entry) {
        if (entries.size() >= CHAT_HISTORY_SIZE) entries.removeFirst();
        entries.addLast(entry);
    }

    /**
     * @param username The player catching up.
     * @return The lines that player was meant to see, oldest first.
     */
    public List<ChatEntry> visibleTo(String username) {
        List<ChatEntry> visible = new ArrayList<>();
        for (ChatEntry entry : entries) {
            if (entry.isVisibleTo(username)) visible.add(entry);
        }
        return visible;
    }

    public int size() {
        return entries.size();
    }

    /**
     * @return Everything said so far, oldest first.
     */
    public List<ChatEntry> entries() {
        return List.copyOf(entries);
    }
}
