package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisItemBag;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisStateSnapshot;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveLayout;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.UiVisibility;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TetrisGameController implements RouteDataReceiver {

    private static final int GAME_TICK_MS = 100;
    private static final double MIN_CELL_SIZE = 14;
    private static final double MAX_CELL_SIZE = 32;
    private static final double CELL_GAP = 1;
    private static final double BOARD_CHROME = 10;
    private static final double STACK_SPACING = 20;
    private static final int OBJECT_SPAWN_SECONDS = 4;
    private static final int OBJECT_SPAWN_ATTEMPTS = 100;
    private static final int MENU_RETURN_SECONDS = 2;
    private static final String OPPONENT_LEFT_MESSAGE = "Your opponent has left the game.";
    private static final double MIN_GAME_CONTENT_HEIGHT = 520;
    private static final String[] PLAYER_BLOCK_COLORS = {
            "#7873B8",
            "#8B8BC9",
            "#A3A7D6"
    };
    private static final String[] OPPONENT_BLOCK_COLORS = {
            "#5D7FBD",
            "#7694CC",
            "#93ADD9"
    };

    @FXML
    private BorderPane gameRoot;
    @FXML
    private ScrollPane gameScrollPane;
    @FXML
    private GridPane gameMain;
    @FXML
    private Label topScoreLabel;
    @FXML
    private Label bottomScoreLabel;
    @FXML
    private Label topSpeedLabel;
    @FXML
    private Label bottomSpeedLabel;
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
    private StackPane boardZone;
    @FXML
    private GridPane topBoardGrid;
    @FXML
    private GridPane bottomBoardGrid;

    private TetrisGameSetup setup = new TetrisGameSetup(
            SafeText.PLAYER_ONE_NAME,
            SafeText.PLAYER_TWO_NAME,
            TetrisGameConfig.defaultConfig());
    private TetrisGameState gameState = TetrisGameState.create(setup);
    private Timeline gameLoop;
    private Timeline objectLoop;
    private GameServer hostServer;
    private GameClient joinClient;
    private final Random objectRandom = new Random();
    private final Random pieceRandom = new Random();
    private final TetrisItemBag bottomObjectBag = new TetrisItemBag();
    private final TetrisItemBag topObjectBag = new TetrisItemBag();
    private boolean networkClosed;
    private int bottomGravityElapsedMs;
    private int topGravityElapsedMs;
    private int elapsedGameMs;
    private final AtomicReference<TetrisGameState> pendingClientState = new AtomicReference<>();
    private final AtomicBoolean clientStateRenderScheduled = new AtomicBoolean();

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
        ResponsiveLayout.bindTwoColumnGrid(gameMain, 70.0);
        configureViewportFill();
        setupKeyboardControls();
        boardZone.layoutBoundsProperty().addListener((observable, oldBounds, bounds) -> render());
        startNewGame();
    }

    private void configureViewportFill() {
        if (gameScrollPane == null || gameMain == null) {
            return;
        }
        gameScrollPane.viewportBoundsProperty().addListener((observable, oldBounds, bounds) -> {
            fitGameMainToViewport(bounds);
            gameScrollPane.setVvalue(0);
        });
        Platform.runLater(this::resetGameViewport);
    }

    private void resetGameViewport() {
        fitGameMainToViewport(gameScrollPane.getViewportBounds());
        gameScrollPane.setHvalue(0);
        gameScrollPane.setVvalue(0);
        gameRoot.requestFocus();
        Platform.runLater(() -> gameScrollPane.setVvalue(0));
    }

    private void fitGameMainToViewport(Bounds viewportBounds) {
        if (viewportBounds == null || !Double.isFinite(viewportBounds.getHeight())) {
            return;
        }
        boolean compact = ResponsiveLayout.isCompact(gameMain.getWidth());
        gameMain.setMinHeight(Math.max(MIN_GAME_CONTENT_HEIGHT, viewportBounds.getHeight()));
        gameMain.setPrefHeight(compact
                ? Region.USE_COMPUTED_SIZE
                : Math.max(MIN_GAME_CONTENT_HEIGHT, viewportBounds.getHeight()));
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
        if (gameState.status() == TetrisGameStatus.RUNNING && !confirmQuit()) {
            Platform.runLater(gameRoot::requestFocus);
            return;
        }
        stopGameLoop();
        closeNetwork(true);
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }

    private boolean confirmQuit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Quit this match and return to the Zetris menu?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Quit Match");
        return confirm.showAndWait().filter(ButtonType.YES::equals).isPresent();
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
        bottomGravityElapsedMs = 0;
        topGravityElapsedMs = 0;
        elapsedGameMs = 0;
        bottomObjectBag.reset();
        topObjectBag.reset();
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

        gameLoop = new Timeline(new KeyFrame(Duration.millis(GAME_TICK_MS), event -> onGameTick()));
        gameLoop.setCycleCount(Animation.INDEFINITE);
        gameLoop.play();

        objectLoop = new Timeline(new KeyFrame(Duration.seconds(OBJECT_SPAWN_SECONDS), event -> onObjectTick()));
        objectLoop.setCycleCount(Animation.INDEFINITE);
        objectLoop.play();
    }

    private void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        if (objectLoop != null) {
            objectLoop.stop();
            objectLoop = null;
        }
    }

    private void onGameTick() {
        if (gameState.isFinished()) {
            stopGameLoop();
            render();
            return;
        }

        gameState = gameState.tickEffects();
        elapsedGameMs = saturatedAdd(elapsedGameMs, GAME_TICK_MS);
        bottomGravityElapsedMs = saturatedAdd(bottomGravityElapsedMs, GAME_TICK_MS);
        topGravityElapsedMs = saturatedAdd(topGravityElapsedMs, GAME_TICK_MS);

        if (bottomGravityElapsedMs >= effectiveGravityMs(gameState.bottomPlayer())) {
            bottomGravityElapsedMs = 0;
            gameState = gameState.applyGravity(PlayerSide.BOTTOM);
        }
        if (topGravityElapsedMs >= effectiveGravityMs(gameState.topPlayer())) {
            topGravityElapsedMs = 0;
            gameState = gameState.applyGravity(PlayerSide.TOP);
        }

        finishStateChange();
    }

    private void onObjectTick() {
        if (gameState.isFinished() || networkClosed) {
            return;
        }

        gameState = spawnObject(gameState, PlayerSide.BOTTOM);
        gameState = spawnObject(gameState, PlayerSide.TOP);
        render();
        sendState();
    }

    private void render() {
        renderLabels();
        applyBoardLayout();
        renderBoard(bottomBoardGrid, gameState.bottomPlayer());
        renderBoard(topBoardGrid, gameState.topPlayer());
    }

    private void applyBoardLayout() {
        boolean horizontal = gameState.config().horizontalMode();

        for (GridPane grid : List.of(bottomBoardGrid, topBoardGrid)) {
            grid.getStyleClass().removeAll("tetris-board-horizontal", "tetris-board-vertical");
            if (!grid.getStyleClass().contains("tetris-board")) {
                grid.getStyleClass().add("tetris-board");
            }
        }

        if (horizontal) {
            if (!gameRoot.getStyleClass().contains("tetris-game-horizontal")) {
                gameRoot.getStyleClass().add("tetris-game-horizontal");
            }
        } else {
            gameRoot.getStyleClass().remove("tetris-game-horizontal");
        }
    }


    private void renderLabels() {
        bottomScoreLabel.setText(scoreText(gameState.bottomPlayer()));
        topScoreLabel.setText(scoreText(gameState.topPlayer()));
        bottomSpeedLabel.setText(speedText(effectiveGravityMs(gameState.bottomPlayer())));
        topSpeedLabel.setText(speedText(effectiveGravityMs(gameState.topPlayer())));
        configLabel.setText("Pieces: " + gameState.config().displayText());
        renderKeymap();
        renderResult();
    }

    private static String scoreText(TetrisPlayerState player) {
        return player.playerName() + ": " + player.score();
    }

    static String speedText(int gravityMillis) {
        return "Speed: " + gravityMillis + " ms/step";
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

        UiVisibility.setVisibleManaged(restartButton, finished);
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
        applyBoardTheme(grid, player.board().themeSide());
        grid.getChildren().clear();
        double cellSize = computeCellSize();

        Set<BoardPosition> activeCells = new HashSet<>();
        int activeColor = -1;
        if (player.activePiece() != null) {
            activeCells.addAll(player.activePiece().boardCells());
            activeColor = player.activePiece().colorIndex();
        }

        for (int row = 0; row < player.board().rows(); row++) {
            for (int column = 0; column < player.board().columns(); column++) {
                BoardPosition position = new BoardPosition(row, column);
                TetrisBoardObject object = player.boardObject();
                boolean hasObject = object != null && position.equals(object.position());
                Region cell = hasObject ? new Label(object.type().symbol()) : new Region();
                cell.getStyleClass().add("board-cell");
                setCellSize(cell, cellSize);

                if (activeCells.contains(position)) {
                    cell.getStyleClass().add("board-cell-active");
                    paintBlock(cell, activeColor, player.board().themeSide());
                } else if (hasObject) {
                    cell.getStyleClass().add("board-cell-object");
                } else if (player.board().cellAt(position) == TetrisCell.FILLED) {
                    cell.getStyleClass().add("board-cell-filled");
                    paintBlock(cell, player.board().colorAt(position), player.board().themeSide());
                }

                grid.add(cell, column, row);
            }
        }

        if (!player.isPlaying()) {
            addGameOverOverlay(grid, player.side());
        }
    }

    private double computeCellSize() {
        if (boardZone == null) {
            return MAX_CELL_SIZE;
        }

        Bounds bounds = boardZone.getLayoutBounds();
        Insets insets = boardZone.getInsets();
        double horizontalInsets = insets == null ? 0 : insets.getLeft() + insets.getRight();
        double verticalInsets = insets == null ? 0 : insets.getTop() + insets.getBottom();
        double zoneWidth = Math.max(1, bounds.getWidth() - horizontalInsets);
        double zoneHeight = Math.max(1, bounds.getHeight() - verticalInsets);
        double boardWidth = Math.max(1, zoneWidth - BOARD_CHROME);
        double boardHeight = Math.max(1, (zoneHeight - STACK_SPACING - 2 * BOARD_CHROME) / 2.0);

        int rows = gameState.bottomPlayer().board().rows();
        int columns = gameState.bottomPlayer().board().columns();
        double widthFit = (boardWidth - CELL_GAP * Math.max(0, columns - 1)) / columns;
        double heightFit = (boardHeight - CELL_GAP * Math.max(0, rows - 1)) / rows;
        double fit = Math.min(widthFit, heightFit);

        return fit < MIN_CELL_SIZE
                ? Math.max(1, fit)
                : Math.min(MAX_CELL_SIZE, fit);
    }

    private static void setCellSize(Region cell, double size) {
        cell.setMinSize(size, size);
        cell.setPrefSize(size, size);
        cell.setMaxSize(size, size);
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
                if (fields.size() != 2) {
                    return;
                }

                PlayerSide side = parseSide(fields.get(0));
                if (side == PlayerSide.TOP && isInputCommand(fields.get(1))) {
                    applyInput(side, fields.get(1));
                    finishStateChange();
                }
            } else if (TetrisProtocol.isType(message, TetrisProtocol.RESTART_REQUEST)) {
                if (TetrisProtocol.fields(message).size() == 1) {
                    startNewGame();
                    sendRestartState();
                }
            } else if (TetrisProtocol.isType(message, TetrisProtocol.QUIT)) {
                if (TetrisProtocol.fields(message).size() == 1) {
                    onNetworkDisconnect();
                }
            }
        });
    }

    private void onClientMessage(String message) {
        if (TetrisProtocol.isType(message, TetrisProtocol.STATE)
                || TetrisProtocol.isType(message, TetrisProtocol.RESTART_STATE)) {
            List<String> fields = TetrisProtocol.fields(message);
            if (fields.size() != 1) {
                return;
            }
            pendingClientState.set(TetrisStateSnapshot.deserialize(fields.getFirst(), setup.config()));
            scheduleClientStateRender();
        } else if (TetrisProtocol.isType(message, TetrisProtocol.QUIT)
                || TetrisProtocol.isType(message, TetrisProtocol.ERROR)) {
            if (TetrisProtocol.fields(message).size() == 1) {
                Platform.runLater(this::onNetworkDisconnect);
            }
        }
    }

    private void scheduleClientStateRender() {
        if (!clientStateRenderScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            TetrisGameState latest = pendingClientState.getAndSet(null);
            if (latest != null && !networkClosed) {
                gameState = latest;
                render();
            }
            clientStateRenderScheduled.set(false);
            if (pendingClientState.get() != null) {
                scheduleClientStateRender();
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

        if (hostServer != null) {
            if (sendQuit && hostServer.isConnected()) {
                hostServer.closeAfterSending(TetrisProtocol.quit(setup.playerOneName()));
            } else {
                hostServer.close();
            }
            hostServer = null;
        }
        if (joinClient != null) {
            if (sendQuit && joinClient.isConnected()) {
                joinClient.closeAfterSending(TetrisProtocol.quit(setup.playerTwoName()));
            } else {
                joinClient.close();
            }
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

    private static boolean isInputCommand(String command) {
        return TetrisProtocol.MOVE_LEFT.equals(command)
                || TetrisProtocol.MOVE_RIGHT.equals(command)
                || TetrisProtocol.SOFT_DROP.equals(command)
                || TetrisProtocol.ROTATE.equals(command);
    }

    private static int saturatedAdd(int value, int increment) {
        return value >= Integer.MAX_VALUE - increment ? Integer.MAX_VALUE : value + increment;
    }

    private TetrisGameState spawnMissingPieces(TetrisGameState state) {
        TetrisGameState next = state;

        if (next.bottomPlayer().isPlaying() && next.bottomPlayer().activePiece() == null) {
            next = next.spawnPiece(
                    PlayerSide.BOTTOM,
                    nextSpawnShape(next.bottomPlayer(), next.config()),
                    randomBlockColor());
        }
        if (next.topPlayer().isPlaying() && next.topPlayer().activePiece() == null) {
            next = next.spawnPiece(
                    PlayerSide.TOP,
                    nextSpawnShape(next.topPlayer(), next.config()),
                    randomBlockColor());
        }

        return next;
    }

    private TetrisGameState spawnObject(TetrisGameState state, PlayerSide side) {
        TetrisPlayerState player = state.player(side);
        if (!player.isPlaying() || player.boardObject() != null) {
            return state;
        }

        TetrisItemType typeToSpawn = objectBag(side).next(objectRandom, eligibleObjectTypes(state, side));
        if (typeToSpawn == null) {
            return state;
        }
        TetrisBoardObject spawnedObject = findSpawnObject(
                player,
                typeToSpawn,
                state.config().gravityDirection(side),
                objectRandom,
                OBJECT_SPAWN_ATTEMPTS);
        return spawnedObject == null ? state : state.spawnObject(side, spawnedObject);
    }

    static Set<TetrisItemType> eligibleObjectTypes(TetrisGameState state, PlayerSide side) {
        if (state == null || side == null) {
            return Set.of();
        }

        TetrisPlayerState player = state.player(side);
        if (player == null || !player.isPlaying()) {
            return Set.of();
        }

        PlayerSide opponentSide = side.opponent();
        TetrisPlayerState opponent = state.player(opponentSide);
        return TetrisItemType.eligibleForOpponentState(opponent != null && opponent.isPlaying());
    }

    static TetrisBoardObject findSpawnObject(
            TetrisPlayerState player,
            TetrisItemType type,
            GravityDirection gravityDirection,
            Random random,
            int attempts) {
        if (player == null || type == null || gravityDirection == null || random == null || attempts <= 0) {
            return null;
        }

        for (int attempt = 0; attempt < attempts; attempt++) {
            BoardPosition position = new BoardPosition(
                    random.nextInt(player.board().rows()),
                    random.nextInt(player.board().columns()));
            TetrisBoardObject candidate = new TetrisBoardObject(type, position);
            if (player.canSpawnObject(candidate, gravityDirection)) {
                return candidate;
            }
        }

        return null;
    }

    private PieceShape nextSpawnShape(TetrisPlayerState player, TetrisGameConfig config) {
        return player.hasQueuedShape()
                ? player.queuedShapes().getFirst()
                : randomPiece(config, pieceRandom);
    }

    static PieceShape randomPiece(TetrisGameConfig config, Random random) {
        List<PieceShape> shapes = config.availableShapes();
        return shapes.get(random.nextInt(shapes.size()));
    }

    private void finishStateChange() {
        gameState = spawnMissingPieces(gameState);
        render();
        sendState();

        if (gameState.isFinished()) {
            stopGameLoop();
        }
    }

    private void paintBlock(Region cell, int colorIndex, PlayerSide themeSide) {
        String color = blockColor(themeSide, colorIndex);
        cell.setStyle("-fx-background-color: " + color + "; -fx-border-color: derive(" + color + ", -24%);");
    }

    static void applyBoardTheme(GridPane grid, PlayerSide themeSide) {
        grid.getStyleClass().remove("tetris-board-opponent");
        if (themeSide == PlayerSide.TOP) {
            grid.getStyleClass().add("tetris-board-opponent");
        }
    }

    static String blockColor(PlayerSide themeSide, int colorIndex) {
        String[] palette = themeSide == PlayerSide.TOP ? OPPONENT_BLOCK_COLORS : PLAYER_BLOCK_COLORS;
        return palette[Math.floorMod(colorIndex, palette.length)];
    }

    private int randomBlockColor() {
        return objectRandom.nextInt(PLAYER_BLOCK_COLORS.length);
    }

    private TetrisItemBag objectBag(PlayerSide side) {
        return side == PlayerSide.TOP ? topObjectBag : bottomObjectBag;
    }

    private int effectiveGravityMs(TetrisPlayerState player) {
        int baseGravityMs = gameState.config().gravityMillisAtElapsed(elapsedGameMs);
        return player.effects().gravityMillis(baseGravityMs);
    }

    private void addGameOverOverlay(GridPane grid, PlayerSide side) {
        Label label = new Label("Game Over");
        label.getStyleClass().add("board-game-over");
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (side == PlayerSide.TOP) {
            label.setRotate(180);
        }

        TetrisPlayerState player = gameState.player(side);
        grid.add(label, 0, 0, player.board().columns(), player.board().rows());
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
