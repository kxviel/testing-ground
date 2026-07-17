package seda_project.control_alt_defeat.gamebox.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import seda_project.control_alt_defeat.gamebox.GameBox;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class GameChoice {
    private static final double MIN_CONTENT_HEIGHT = 620;

    @FXML
    private StackPane root;
    @FXML
    private ScrollPane gameChoiceScroll;
    @FXML
    private StackPane gameChoiceMain;
    private boolean navigationPending;

    @FXML
    private void initialize() {
        gameChoiceScroll.viewportBoundsProperty().addListener((observable, oldBounds, viewport) ->
                centerContentInViewport(viewport.getHeight()));
        Platform.runLater(() -> {
            centerContentInViewport(gameChoiceScroll.getViewportBounds().getHeight());
            root.requestFocus();
        });
    }

    private void centerContentInViewport(double viewportHeight) {
        if (Double.isFinite(viewportHeight) && viewportHeight > 0) {
            gameChoiceMain.setPrefHeight(Math.max(MIN_CONTENT_HEIGHT, viewportHeight));
        }
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
