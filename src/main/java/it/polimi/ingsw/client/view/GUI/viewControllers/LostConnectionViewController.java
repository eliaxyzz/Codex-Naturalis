package it.polimi.ingsw.client.view.GUI.viewControllers;

import it.polimi.ingsw.client.controller.ClientController;
import it.polimi.ingsw.client.view.GUI.ClientGUI;
import it.polimi.ingsw.client.view.StageManager;
import it.polimi.ingsw.client.view.ViewController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * This class is the controller of the "Lost Connection With the Server" Scene.
 */
public class LostConnectionViewController extends ViewController {

    @FXML
    private Label alertLabel;

    /**
     * Asks the server to take this client back into the game it dropped out of. The automatic
     * retries keep running either way; this just tries straight away.
     */
    @FXML
    private void reconnect() {
        if (ClientController.getGameName() == null) {
            showErrorMessage("There's no game to go back to.");
            return;
        }
        if (!ClientController.reconnect()) showErrorMessage("The server is still unreachable.");
    }

    @Override
    public void showErrorMessage(String message) {
        Platform.runLater(() -> alertLabel.setText(message));
    }
    /**
     * Closes the current window and shuts down the ClientController.
     */
    @FXML
    private void exit(){
        StageManager.getCurrentStage().close();
        ClientGUI.exit();
    }
}
