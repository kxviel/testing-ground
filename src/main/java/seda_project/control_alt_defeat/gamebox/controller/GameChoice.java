package seda_project.control_alt_defeat.gamebox.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import seda_project.control_alt_defeat.gamebox.GameBox;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class GameChoice {

    @FXML
    private StackPane root;
    private boolean navigationPending;

    @FXML
    private void initialize() {
        Platform.runLater(root::requestFocus);
    }

    @FXML
    private void playMemory(ActionEvent event) {
        goToOnce(event, "/memory/MemoryMenu.fxml");
    }

    @FXML
    private void playTetris(ActionEvent event) {
        goToOnce(event, "/tetris/TetrisMenu.fxml");
    }

    @FXML
    private void playHexChess(ActionEvent event) {
        goToOnce(event, "/hexchess/HexChessMenu.fxml");
    }

    @FXML
    private void exit() {
        GameBox.cleanExit();
    }

    private void goToOnce(ActionEvent event, String route) {
        if (navigationPending) {
            return;
        }

        navigationPending = true;
        Router.goTo(event, route, null);
    }
}
