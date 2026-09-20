package it.polimi.ingsw.client.view.GUI.viewControllers;

import it.polimi.ingsw.client.controller.ClientController;
import it.polimi.ingsw.client.model.ChatModel;
import it.polimi.ingsw.client.model.PlayerModel;
import it.polimi.ingsw.client.model.ScoreBoardModel;
import it.polimi.ingsw.client.view.observers.ModelObserver;
import it.polimi.ingsw.util.supportclasses.ChatLine;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * This class is the controller of the chat window. It lives in its own stage next to the game
 * board, so it watches the ChatModel itself instead of going through the StageManager.
 */
public class ChatViewController implements ModelObserver {
    private static final String EVERYONE = "Everyone";

    @FXML
    private TextArea chatArea;
    @FXML
    private TextField messageField;
    @FXML
    private ComboBox<String> recipientBox;
    @FXML
    private Label errorLabel;

    @FXML
    private void initialize() {
        recipientBox.getItems().add(EVERYONE);
        String me = PlayerModel.getInstance().getUsername();
        for (String username : ScoreBoardModel.getInstance().getScore().keySet()) {
            if (!username.equals(me)) recipientBox.getItems().add(username);
        }
        recipientBox.getSelectionModel().select(EVERYONE);
        redraw();
        ChatModel.getInstance().addObserver(this);
    }

    /**
     * Stops watching the model. Called when the window is closed, so a chat window that's gone
     * doesn't keep the model holding on to it.
     */
    public void detach() {
        ChatModel.getInstance().removeObserver(this);
    }

    @Override
    public void update() {
        Platform.runLater(this::redraw);
    }

    private void redraw() {
        String me = PlayerModel.getInstance().getUsername();
        StringBuilder text = new StringBuilder();
        for (ChatLine line : ChatModel.getInstance().getLines()) {
            text.append(line.format(me)).append(System.lineSeparator());
        }
        chatArea.setText(text.toString());
        chatArea.positionCaret(chatArea.getLength());
    }

    @FXML
    private void send() {
        String text = messageField.getText() == null ? "" : messageField.getText().trim();
        if (text.isEmpty()) {
            errorLabel.setText("Nothing to say.");
            return;
        }
        errorLabel.setText("");
        String chosen = recipientBox.getSelectionModel().getSelectedItem();
        ClientController.getInstance().sendChatMessage(text, EVERYONE.equals(chosen) ? null : chosen);
        messageField.clear();
    }
}
