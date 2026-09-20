package it.polimi.ingsw.client.view.observers;

import it.polimi.ingsw.client.model.ChatModel;
import it.polimi.ingsw.client.view.StageManager;

/**
 * This class observes the ChatModel and tells the current view to show the new line.
 */
public class ChatObserver implements ModelObserver {

    public ChatObserver() {
        ChatModel.getInstance().addObserver(this);
    }

    @Override
    public void update() {
        StageManager.getCurrentViewController().updateChat();
    }
}
