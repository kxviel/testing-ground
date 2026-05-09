package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TetrisGameController implements RouteDataReceiver {

    @FXML
    private BorderPane gameRoot;
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
    private Label resultLabel;
    @FXML
    private Button restartButton;
    @FXML
    private GridPane topBoardGrid;
    @FXML
    private GridPane bottomBoardGrid;

    private TetrisGameSetup setup = new TetrisGameSetup("Player 1", "Player 2", TetrisGameConfig.defaultConfig());
    private TetrisGameState gameState = TetrisGameState.create(setup);
    private Timeline gameLoop;
    private int bottomPieceIndex;
    private int topPieceIndex;

    @Override
    public void setRouteData(Object data) {
        if (data instanceof TetrisGameSetup nextSetup) {
            setup = nextSetup;
            startNewGame();
        }
    }

    @FXML
    public void initialize() {
        setupKeyboardControls();
        startNewGame();
    }

    @FXML
    private void onBackToMenu(ActionEvent event) {
        stopGameLoop();
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }

    @FXML
    private void onRestart() {
        startNewGame();
        Platform.runLater(gameRoot::requestFocus);
    }

    private void startNewGame() {
        bottomPieceIndex = 0;
        topPieceIndex = 0;
        gameState = spawnMissingPieces(TetrisGameState.create(setup).running());
        startGameLoop();
        render();
    }

    private void startGameLoop() {
        stopGameLoop();

        gameLoop = new Timeline(new KeyFrame(Duration.millis(550), event -> onGameTick()));
        gameLoop.setCycleCount(Animation.INDEFINITE);
        gameLoop.play();
    }

    private void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
    }

    private void onGameTick() {
        if (gameState.isFinished()) {
            stopGameLoop();
            render();
            return;
        }

        gameState = gameState.applyGravity();
        gameState = spawnMissingPieces(gameState);
        render();

        if (gameState.isFinished()) {
            stopGameLoop();
        }
    }

    private void render() {
        renderLabels();
        renderBoard(bottomBoardGrid, gameState.bottomPlayer());
        renderBoard(topBoardGrid, gameState.topPlayer());
    }

    private void renderLabels() {
        bottomNameLabel.setText(gameState.bottomPlayer().playerName());
        topNameLabel.setText(gameState.topPlayer().playerName());
        bottomScoreLabel.setText("Score: " + gameState.bottomPlayer().score());
        topScoreLabel.setText("Score: " + gameState.topPlayer().score());
        configLabel.setText("Pieces: " + gameState.config().displayText());
        renderResult();
    }

    private void renderResult() {
        boolean finished = gameState.isFinished();

        restartButton.setVisible(finished);
        restartButton.setManaged(finished);

        if (finished) {
            resultLabel.setText(resultText());
        } else if (!gameState.bottomPlayer().isPlaying()) {
            resultLabel.setText(gameState.bottomPlayer().playerName() + " lost. "
                    + gameState.topPlayer().playerName() + " continues.");
        } else if (!gameState.topPlayer().isPlaying()) {
            resultLabel.setText(gameState.topPlayer().playerName() + " lost. "
                    + gameState.bottomPlayer().playerName() + " continues.");
        } else {
            resultLabel.setText("Game running.");
        }
    }

    private String resultText() {
        int bottomScore = finalScore(gameState.bottomPlayer());
        int topScore = finalScore(gameState.topPlayer());

        if (bottomScore > topScore) {
            return gameState.bottomPlayer().playerName() + " wins.";
        }
        if (topScore > bottomScore) {
            return gameState.topPlayer().playerName() + " wins.";
        }

        return "Draw.";
    }

    private int finalScore(TetrisPlayerState player) {
        return player.finalScore() == null ? player.score() : player.finalScore();
    }

    private void renderBoard(GridPane grid, TetrisPlayerState player) {
        grid.getChildren().clear();

        Set<BoardPosition> activeCells = new HashSet<>();
        if (player.activePiece() != null) {
            activeCells.addAll(player.activePiece().boardCells());
        }

        for (int row = 0; row < player.board().rows(); row++) {
            for (int column = 0; column < player.board().columns(); column++) {
                BoardPosition position = new BoardPosition(row, column);
                Region cell = new Region();
                cell.getStyleClass().add("board-cell");

                if (activeCells.contains(position)) {
                    cell.getStyleClass().add("board-cell-active");
                } else if (player.board().cellAt(position) == TetrisCell.FILLED) {
                    cell.getStyleClass().add("board-cell-filled");
                }

                grid.add(cell, column, row);
            }
        }
    }

    private void setupKeyboardControls() {
        gameRoot.setFocusTraversable(true);
        gameRoot.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(this::onKeyPressed);
                Platform.runLater(gameRoot::requestFocus);
            }
        });
    }

    private void onKeyPressed(KeyEvent event) {
        if (gameState.isFinished()) {
            return;
        }

        KeyCode code = event.getCode();

        if (code == KeyCode.LEFT) {
            gameState = gameState.moveLeft(PlayerSide.BOTTOM);
        } else if (code == KeyCode.RIGHT) {
            gameState = gameState.moveRight(PlayerSide.BOTTOM);
        } else if (code == KeyCode.DOWN) {
            gameState = gameState.applyGravity(PlayerSide.BOTTOM);
        } else if (code == KeyCode.UP) {
            gameState = gameState.rotateClockwise(PlayerSide.BOTTOM);
        } else if (code == KeyCode.A) {
            gameState = gameState.moveLeft(PlayerSide.TOP);
        } else if (code == KeyCode.D) {
            gameState = gameState.moveRight(PlayerSide.TOP);
        } else if (code == KeyCode.S) {
            gameState = gameState.applyGravity(PlayerSide.TOP);
        } else if (code == KeyCode.W) {
            gameState = gameState.rotateClockwise(PlayerSide.TOP);
        } else {
            return;
        }

        gameState = spawnMissingPieces(gameState);
        event.consume();
        render();

        if (gameState.isFinished()) {
            stopGameLoop();
        }
    }

    private TetrisGameState spawnMissingPieces(TetrisGameState state) {
        TetrisGameState next = state;

        if (next.bottomPlayer().isPlaying() && next.bottomPlayer().activePiece() == null) {
            next = next.spawnPiece(PlayerSide.BOTTOM, nextPiece(PlayerSide.BOTTOM));
        }
        if (next.topPlayer().isPlaying() && next.topPlayer().activePiece() == null) {
            next = next.spawnPiece(PlayerSide.TOP, nextPiece(PlayerSide.TOP));
        }

        return next;
    }

    private PieceShape nextPiece(PlayerSide side) {
        List<PieceShape> shapes = gameState.config().availableShapes();

        if (side == PlayerSide.BOTTOM) {
            PieceShape shape = shapes.get(bottomPieceIndex % shapes.size());
            bottomPieceIndex++;
            return shape;
        }

        PieceShape shape = shapes.get(topPieceIndex % shapes.size());
        topPieceIndex++;
        return shape;
    }
}
