package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class TetrisGameController implements RouteDataReceiver {

    private static final int BOARD_COLUMNS = 10;
    private static final int BOARD_ROWS = 20;

    @FXML
    private Label topNameLabel;
    @FXML
    private Label bottomNameLabel;
    @FXML
    private Label topScoreLabel;
    @FXML
    private Label bottomScoreLabel;
    @FXML
    private Label configLabel;
    @FXML
    private GridPane topBoardGrid;
    @FXML
    private GridPane bottomBoardGrid;

    private String playerOneName = "Player 1";
    private String playerTwoName = "Player 2";
    private TetrisGameConfig config = TetrisGameConfig.defaultConfig();

    @Override
    public void setRouteData(Object data) {
        if (data instanceof TetrisGameSetup setup) {
            playerOneName = setup.playerOneName();
            playerTwoName = setup.playerTwoName();
            config = setup.config();
            updatePlayersLabel();
            updateScoreLabels();
            updateConfigLabel();
        }
    }

    @FXML
    public void initialize() {
        buildBoard(topBoardGrid, true);
        buildBoard(bottomBoardGrid, false);
        updatePlayersLabel();
        updateScoreLabels();
        updateConfigLabel();
    }

    @FXML
    private void onBackToMenu(ActionEvent event) {
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }

    private void updatePlayersLabel() {
        if (bottomNameLabel != null) {
            bottomNameLabel.setText(playerOneName);
        }
        if (topNameLabel != null) {
            topNameLabel.setText(playerTwoName);
        }
    }

    private void updateScoreLabels() {
        if (bottomScoreLabel != null) {
            bottomScoreLabel.setText("Score: 0");
        }
        if (topScoreLabel != null) {
            topScoreLabel.setText("Score: 0");
        }
    }

    private void updateConfigLabel() {
        if (configLabel != null) {
            configLabel.setText("Pieces: " + config.displayText());
        }
    }

    private void buildBoard(GridPane board, boolean topBoard) {
        board.getChildren().clear();

        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int column = 0; column < BOARD_COLUMNS; column++) {
                Region cell = new Region();
                cell.getStyleClass().add("board-cell");

                if (isDummyActivePiece(row, column, topBoard)) {
                    cell.getStyleClass().add("board-cell-active");
                } else if (isDummySettledBlock(row, column, topBoard)) {
                    cell.getStyleClass().add("board-cell-filled");
                }

                board.add(cell, column, row);
            }
        }
    }

    private boolean isDummyActivePiece(int row, int column, boolean topBoard) {
        if (topBoard) {
            return (row == 16 && column >= 4 && column <= 6) || (row == 17 && column == 5);
        }

        return (row == 2 && column >= 3 && column <= 5) || (row == 3 && column == 4);
    }

    private boolean isDummySettledBlock(int row, int column, boolean topBoard) {
        if (topBoard) {
            return row <= 1 && column >= 2 && column <= 7 && column != 5;
        }

        return row >= 18 && column >= 1 && column <= 8 && column != 4;
    }
}
