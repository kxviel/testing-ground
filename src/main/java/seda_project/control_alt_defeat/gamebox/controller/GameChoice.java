package seda_project.control_alt_defeat.gamebox.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class GameChoice {

    @FXML
    private void playMemory(ActionEvent event) {
        Router.goTo(event, "/memory/MemoryMenu.fxml", null);
    }

    @FXML
    private void playTetris(ActionEvent event) {
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }
}
