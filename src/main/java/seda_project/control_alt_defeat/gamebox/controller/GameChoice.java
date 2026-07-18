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
    private static final double CARD_MIN_WIDTH = 620;
    private static final double CARD_MAX_WIDTH = 900;
    private static final double CARD_WIDTH_RATIO = 0.47;

    @FXML
    private StackPane root;
    @FXML
    private ScrollPane gameChoiceScroll;
    @FXML
    private StackPane gameChoiceMain;
    @FXML
    private StackPane gameChoiceCard;
    private boolean navigationPending;

    @FXML
    private void initialize() {
        gameChoiceScroll.viewportBoundsProperty().addListener((observable, oldBounds, viewport) -> {
            centerContentInViewport(viewport.getHeight());
            resizeCard(viewport.getWidth());
        });
        Platform.runLater(() -> {
            double viewportWidth = gameChoiceScroll.getViewportBounds().getWidth();
            double viewportHeight = gameChoiceScroll.getViewportBounds().getHeight();
            centerContentInViewport(viewportHeight);
            resizeCard(viewportWidth);
            root.requestFocus();
        });
    }

    private void centerContentInViewport(double viewportHeight) {
        if (Double.isFinite(viewportHeight) && viewportHeight > 0) {
            gameChoiceMain.setPrefHeight(Math.max(MIN_CONTENT_HEIGHT, viewportHeight));
        }
    }

    private void resizeCard(double viewportWidth) {
        if (!Double.isFinite(viewportWidth) || viewportWidth <= 0) {
            return;
        }
        double clamped = Math.min(CARD_MAX_WIDTH, Math.max(CARD_MIN_WIDTH, viewportWidth * CARD_WIDTH_RATIO));
        if (gameChoiceCard != null) {
            gameChoiceCard.setMaxWidth(clamped);
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
