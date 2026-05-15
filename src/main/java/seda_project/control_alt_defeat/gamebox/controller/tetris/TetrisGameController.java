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
import javafx.stage.Stage;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisStateSnapshot;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class TetrisGameController implements RouteDataReceiver {

    private static final int GRAVITY_MS = 550;
    private static final int BUG_SPAWN_SECONDS = 8;
    private static final int BUG_SPAWN_ATTEMPTS = 100;
    private static final int MENU_RETURN_SECONDS = 2;
    private static final String OPPONENT_LEFT_MESSAGE = "Your opponent has left the game.";
    private static final String[] BLOCK_COLORS = {
            "#2f7edb:#13579f",
            "#20a162:#147246",
            "#d98b1f:#9d6212",
            "#8b5cf6:#5b35b7",
            "#df4e7a:#a82f55",
            "#10a7b5:#0b737d",
            "#7a8f24:#536318"
    };

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
    private Label keymapTitleLabel;
    @FXML
    private Label keymapLabel;
    @FXML
    private Button restartButton;
    @FXML
    private GridPane topBoardGrid;
    @FXML
    private GridPane bottomBoardGrid;

    private TetrisGameSetup setup = new TetrisGameSetup("Player 1", "Player 2", TetrisGameConfig.defaultConfig());
    private TetrisGameState gameState = TetrisGameState.create(setup);
    private Timeline gameLoop;
    private Timeline bugLoop;
    private GameServer hostServer;
    private GameClient joinClient;
    private final Random bugRandom = new Random();
    private boolean networkClosed;
    private int bottomPieceIndex;
    private int topPieceIndex;

    @Override
    public void setRouteData(Object data) {
        if (data instanceof TetrisGameRouteData(TetrisGameSetup nextSetup, GameServer nextServer, GameClient nextClient)) {
            setup = nextSetup;
            hostServer = nextServer;
            joinClient = nextClient;
            setupNetworkCallbacks();
            startNewGame();
        } else if (data instanceof TetrisGameSetup nextSetup) {
            setup = nextSetup;
            startNewGame();
        }
    }

    @FXML
    public void initialize() {
        setupKeyboardControls();
        startNewGame();
    }

    private void setupNetworkCallbacks() {
        networkClosed = false;

        if (hostServer != null) {
            hostServer.setMessageListener(this::onHostMessage);
            hostServer.setDisconnectListener(() -> Platform.runLater(this::onNetworkDisconnect));
        }
        if (joinClient != null) {
            joinClient.setMessageListener(this::onClientMessage);
            joinClient.setDisconnectListener(() -> Platform.runLater(this::onNetworkDisconnect));
        }
    }

    @FXML
    private void onBackToMenu(ActionEvent event) {
        stopGameLoop();
        closeNetwork(true);
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }

    @FXML
    private void onRestart() {
        if (isLanClient()) {
            joinClient.send(TetrisProtocol.restartRequest(setup.playerTwoName()));
            resultLabel.setText("Waiting for host restart.");
        } else {
            startNewGame();
            sendRestartState();
        }
        Platform.runLater(gameRoot::requestFocus);
    }

    private void startNewGame() {
        bottomPieceIndex = 0;
        topPieceIndex = 0;
        gameState = spawnMissingPieces(TetrisGameState.create(setup).running());
        if (isLanClient()) {
            stopGameLoop();
        } else {
            startGameLoop();
        }
        render();
        sendState();
    }

    private void startGameLoop() {
        stopGameLoop();

        gameLoop = new Timeline(new KeyFrame(Duration.millis(GRAVITY_MS), event -> onGameTick()));
        gameLoop.setCycleCount(Animation.INDEFINITE);
        gameLoop.play();

        bugLoop = new Timeline(new KeyFrame(Duration.seconds(BUG_SPAWN_SECONDS), event -> onBugTick()));
        bugLoop.setCycleCount(Animation.INDEFINITE);
        bugLoop.play();
    }

    private void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        if (bugLoop != null) {
            bugLoop.stop();
            bugLoop = null;
        }
    }

    private void onGameTick() {
        if (gameState.isFinished()) {
            stopGameLoop();
            render();
            return;
        }

        gameState = gameState.applyGravity();
        finishStateChange();
    }

    private void onBugTick() {
        if (gameState.isFinished() || networkClosed) {
            return;
        }

        gameState = spawnBug(gameState, PlayerSide.BOTTOM);
        gameState = spawnBug(gameState, PlayerSide.TOP);
        render();
        sendState();
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
        renderKeymap();
        renderResult();
    }

    private void renderKeymap() {
        if (setup.isLocal()) {
            keymapTitleLabel.setText("Controls - Local");
            keymapLabel.setText("""
                    Bottom: Left/Right = move, Down = forward, Up = rotate
                    Top: A/D = move, W = forward, S = rotate""");
            return;
        }

        if (setup.localSide() == PlayerSide.TOP) {
            keymapTitleLabel.setText("Controls - " + gameState.topPlayer().playerName());
            keymapLabel.setText("A/D = move, W = forward, S = rotate");
        } else {
            keymapTitleLabel.setText("Controls - " + gameState.bottomPlayer().playerName());
            keymapLabel.setText("Left/Right = move, Down = forward, Up = rotate");
        }
    }

    private void renderResult() {
        boolean finished = gameState.isFinished();

        restartButton.setVisible(finished);
        restartButton.setManaged(finished);
        restartButton.setDisable(!setup.isLocal() && networkClosed);

        if (!setup.isLocal() && networkClosed) {
            resultLabel.setText("LAN connection lost.");
        } else if (finished) {
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
        int activeColor = -1;
        if (player.activePiece() != null) {
            activeCells.addAll(player.activePiece().boardCells());
            activeColor = player.activePiece().colorIndex();
        }

        for (int row = 0; row < player.board().rows(); row++) {
            for (int column = 0; column < player.board().columns(); column++) {
                BoardPosition position = new BoardPosition(row, column);
                Region cell = position.equals(player.bugPosition()) ? new Label("🐞") : new Region();
                cell.getStyleClass().add("board-cell");

                if (activeCells.contains(position)) {
                    cell.getStyleClass().add("board-cell-active");
                    paintBlock(cell, activeColor);
                } else if (position.equals(player.bugPosition())) {
                    cell.getStyleClass().add("board-cell-bug");
                } else if (player.board().cellAt(position) == TetrisCell.FILLED) {
                    cell.getStyleClass().add("board-cell-filled");
                    paintBlock(cell, player.board().colorAt(position));
                }

                grid.add(cell, column, row);
            }
        }

        if (!player.isPlaying()) {
            addGameOverOverlay(grid, player.side());
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
        if (gameState.isFinished() || networkClosed) {
            return;
        }

        KeyCode code = event.getCode();
        PlayerSide side = sideForKey(code);

        if (side == null) {
            return;
        }

        String command = commandForKey(side, code);
        if (isLanClient()) {
            joinClient.send(TetrisProtocol.input(side, command));
            event.consume();
            return;
        }

        applyInput(side, command);
        event.consume();
        finishStateChange();
    }

    private PlayerSide sideForKey(KeyCode code) {
        if (!setup.isLocal()) {
            PlayerSide localSide = setup.localSide();
            if (localSide == PlayerSide.TOP && isTopKey(code)) {
                return PlayerSide.TOP;
            }
            if (localSide == PlayerSide.BOTTOM && isBottomKey(code)) {
                return PlayerSide.BOTTOM;
            }
            return null;
        }

        if (isBottomKey(code)) {
            return PlayerSide.BOTTOM;
        }
        if (isTopKey(code)) {
            return PlayerSide.TOP;
        }

        return null;
    }

    private String commandForKey(PlayerSide side, KeyCode code) {
        if (side == PlayerSide.BOTTOM) {
            return switch (code) {
                case LEFT -> TetrisProtocol.MOVE_LEFT;
                case RIGHT -> TetrisProtocol.MOVE_RIGHT;
                case DOWN -> TetrisProtocol.SOFT_DROP;
                case UP -> TetrisProtocol.ROTATE;
                default -> "";
            };
        }

        if (side == PlayerSide.TOP) {
            return switch (code) {
                case A -> TetrisProtocol.MOVE_LEFT;
                case D -> TetrisProtocol.MOVE_RIGHT;
                case W -> TetrisProtocol.SOFT_DROP;
                case S -> TetrisProtocol.ROTATE;
                default -> "";
            };
        }

        return "";
    }

    private boolean isBottomKey(KeyCode code) {
        return code == KeyCode.LEFT
                || code == KeyCode.RIGHT
                || code == KeyCode.DOWN
                || code == KeyCode.UP;
    }

    private boolean isTopKey(KeyCode code) {
        return code == KeyCode.A
                || code == KeyCode.D
                || code == KeyCode.W
                || code == KeyCode.S;
    }

    private void applyInput(PlayerSide side, String command) {
        if (TetrisProtocol.MOVE_LEFT.equals(command)) {
            gameState = side == PlayerSide.TOP ? gameState.moveRight(side) : gameState.moveLeft(side);
        } else if (TetrisProtocol.MOVE_RIGHT.equals(command)) {
            gameState = side == PlayerSide.TOP ? gameState.moveLeft(side) : gameState.moveRight(side);
        } else if (TetrisProtocol.SOFT_DROP.equals(command)) {
            gameState = gameState.applyGravity(side);
        } else if (TetrisProtocol.ROTATE.equals(command)) {
            gameState = gameState.rotateClockwise(side);
        }
    }

    private void onHostMessage(String message) {
        Platform.runLater(() -> {
            if (networkClosed) {
                return;
            }

            if (TetrisProtocol.isType(message, TetrisProtocol.INPUT)) {
                List<String> fields = TetrisProtocol.fields(message);
                if (fields.size() < 2) {
                    return;
                }

                PlayerSide side = parseSide(fields.get(0));
                if (side == PlayerSide.TOP) {
                    applyInput(side, fields.get(1));
                    finishStateChange();
                }
            } else if (TetrisProtocol.isType(message, TetrisProtocol.RESTART_REQUEST)) {
                startNewGame();
                sendRestartState();
            } else if (TetrisProtocol.isType(message, TetrisProtocol.QUIT)) {
                onNetworkDisconnect();
            }
        });
    }

    private void onClientMessage(String message) {
        Platform.runLater(() -> {
            if (TetrisProtocol.isType(message, TetrisProtocol.STATE)
                    || TetrisProtocol.isType(message, TetrisProtocol.RESTART_STATE)) {
                List<String> fields = TetrisProtocol.fields(message);
                if (fields.isEmpty()) {
                    return;
                }

                gameState = TetrisStateSnapshot.deserialize(fields.get(0), setup.config());
                render();
            } else if (TetrisProtocol.isType(message, TetrisProtocol.QUIT)
                    || TetrisProtocol.isType(message, TetrisProtocol.ERROR)) {
                onNetworkDisconnect();
            }
        });
    }

    private void onNetworkDisconnect() {
        if (networkClosed) {
            return;
        }

        networkClosed = true;
        stopGameLoop();
        closeNetwork(false);
        render();
        resultLabel.setText(OPPONENT_LEFT_MESSAGE);
        returnToMenuAfterDisconnect();
    }

    private void sendState() {
        if (isLanHost() && !networkClosed) {
            hostServer.send(TetrisProtocol.state(TetrisStateSnapshot.serialize(gameState)));
        }
    }

    private void sendRestartState() {
        if (isLanHost() && !networkClosed) {
            hostServer.send(TetrisProtocol.restartState(TetrisStateSnapshot.serialize(gameState)));
        }
    }

    private void closeNetwork(boolean sendQuit) {
        networkClosed = true;

        if (sendQuit) {
            if (hostServer != null && hostServer.isConnected()) {
                hostServer.send(TetrisProtocol.quit(setup.playerOneName()));
            }
            if (joinClient != null && joinClient.isConnected()) {
                joinClient.send(TetrisProtocol.quit(setup.playerTwoName()));
            }
        }

        if (hostServer != null) {
            hostServer.close();
            hostServer = null;
        }
        if (joinClient != null) {
            joinClient.close();
            joinClient = null;
        }
    }

    private boolean isLanHost() {
        return hostServer != null;
    }

    private boolean isLanClient() {
        return joinClient != null;
    }

    private PlayerSide parseSide(String value) {
        try {
            return PlayerSide.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TetrisGameState spawnMissingPieces(TetrisGameState state) {
        TetrisGameState next = state;

        if (next.bottomPlayer().isPlaying() && next.bottomPlayer().activePiece() == null) {
            next = next.spawnPiece(PlayerSide.BOTTOM, nextPiece(PlayerSide.BOTTOM), randomBlockColor());
        }
        if (next.topPlayer().isPlaying() && next.topPlayer().activePiece() == null) {
            next = next.spawnPiece(PlayerSide.TOP, nextPiece(PlayerSide.TOP), randomBlockColor());
        }

        return next;
    }

    private TetrisGameState spawnBug(TetrisGameState state, PlayerSide side) {
        TetrisPlayerState player = side == PlayerSide.BOTTOM ? state.bottomPlayer() : state.topPlayer();
        if (!player.isPlaying() || player.bugPosition() != null) {
            return state;
        }

        for (int attempt = 0; attempt < BUG_SPAWN_ATTEMPTS; attempt++) {
            BoardPosition position = new BoardPosition(
                    bugRandom.nextInt(player.board().rows()),
                    bugRandom.nextInt(player.board().columns()));
            if (player.canPlaceBug(position)) {
                return state.spawnBug(side, position);
            }
        }

        return state;
    }

    private PieceShape nextPiece(PlayerSide side) {
        List<PieceShape> shapes = gameState.config().availableShapes();
        int index;

        if (side == PlayerSide.BOTTOM) {
            index = bottomPieceIndex++;
        } else {
            index = topPieceIndex++;
        }

        return shapes.get(index % shapes.size());
    }

    private void finishStateChange() {
        gameState = spawnMissingPieces(gameState);
        render();
        sendState();

        if (gameState.isFinished()) {
            stopGameLoop();
        }
    }

    private void paintBlock(Region cell, int colorIndex) {
        String[] colors = BLOCK_COLORS[Math.floorMod(colorIndex, BLOCK_COLORS.length)].split(":", 2);
        cell.setStyle("-fx-background-color: " + colors[0] + "; -fx-border-color: " + colors[1] + ";");
    }

    private int randomBlockColor() {
        return bugRandom.nextInt(BLOCK_COLORS.length);
    }

    private void addGameOverOverlay(GridPane grid, PlayerSide side) {
        Label label = new Label("Game Over");
        label.getStyleClass().add("board-game-over");
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (side == PlayerSide.TOP) {
            label.setRotate(180);
        }

        grid.add(label, 0, 0, TetrisBoard.COLUMNS, TetrisBoard.ROWS);
    }

    private void returnToMenuAfterDisconnect() {
        Timeline returnTimer = new Timeline(new KeyFrame(Duration.seconds(MENU_RETURN_SECONDS), event -> {
            Stage stage = (Stage) gameRoot.getScene().getWindow();
            Router.goTo(stage, "/tetris/TetrisMenu.fxml", OPPONENT_LEFT_MESSAGE);
        }));
        returnTimer.setCycleCount(1);
        returnTimer.play();
    }
}
