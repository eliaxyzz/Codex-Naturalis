package it.polimi.ingsw.client.model;

import it.polimi.ingsw.util.supportclasses.ChatLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static it.polimi.ingsw.util.supportclasses.Constants.CHAT_HISTORY_SIZE;

/**
 * This class represents an ObservableModel that keeps the chat of the current game.
 */
public class ChatModel extends ObservableModel {

    private static final ChatModel instance = new ChatModel();

    //written from the network thread, read by the views: guarded by the model's own lock
    private final List<ChatLine> lines = new ArrayList<>();

    private ChatModel() {}

    /**
     * Returns the singleton instance of ChatModel.
     * @return The singleton instance of ChatModel.
     */
    public static ChatModel getInstance() {
        return instance;
    }

    /**
     * Adds a line to the chat and notifies any registered observers that the data has changed.
     * The oldest line drops off once the chat is full.
     * @param line The line that just arrived.
     */
    public void addLine(ChatLine line) {
        synchronized (lines) {
            if (lines.size() >= CHAT_HISTORY_SIZE) lines.removeFirst();
            lines.add(line);
        }
        notifyObservers();
    }

    /**
     * @return The chat so far, oldest first.
     */
    public List<ChatLine> getLines() {
        synchronized (lines) {
            return Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }

    /**
     * @return The line that arrived last, or null if nothing has been said yet.
     */
    public ChatLine getLastLine() {
        synchronized (lines) {
            return lines.isEmpty() ? null : lines.getLast();
        }
    }

    /**
     * Resets the Chat Model.
     */
    public void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }
}
