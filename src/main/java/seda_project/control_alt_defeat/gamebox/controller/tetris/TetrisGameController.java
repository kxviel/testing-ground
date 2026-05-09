package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class TetrisGameController implements RouteDataReceiver {

    @FXML
    private Label playersLabel;

    private String playerOneName = "Player 1";
    private String playerTwoName = "Player 2";

    @Override
    public void setRouteData(Object data) {
        if (data instanceof TetrisMenuController.GameSetup setup) {
            playerOneName = setup.playerOneName();
            playerTwoName = setup.playerTwoName();
            updatePlayersLabel();
        }
    }

    @FXML
    public void initialize() {
        updatePlayersLabel();
    }

    @FXML
    private void onBackToMenu(ActionEvent event) {
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }

    private void updatePlayersLabel() {
        if (playersLabel != null) {
            playersLabel.setText(playerOneName + " vs " + playerTwoName);
        }
    }
}
