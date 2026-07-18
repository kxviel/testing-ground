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
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
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
    private static final double MAX_CELL_SIZE = 80;
    private static final double CELL_GAP = 1;
    // Total non-cell extent reported by the styled GridPane (padding, border, and insets).
    private static final double BOARD_CHROME = 18;
    private static final double SIDE_SPACING = 12.0;
    private static final double NETWORK_PRIMARY_RATIO = 0.65;
    private static final double OBJECT_ICON_MIN_SIZE = 8.0;
    private static final double OBJECT_ICON_MAX_SIZE = 20.0;
    private static final double OBJECT_ICON_CELL_RATIO = 0.72;
    private static final double OBJECT_ICON_OVERLAY_RATIO = 0.38;
    private static final double OBJECT_LEGEND_ICON_SIZE = 16.0;
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
    private Label configLabel;
    @FXML
    private Label resultLabel;
    @FXML
    private Label keymapTitleLabel;
    @FXML
    private Label keymapLabel;
    @FXML
    private Label topScoreLabel;
    @FXML
    private Label bottomScoreLabel;
    @FXML
    private Label topSpeedLabel;
    @FXML
    private Label bottomSpeedLabel;
    @FXML
    private Button restartButton;
    @FXML
    private StackPane boardZone;
    @FXML
    private GridPane topBoardGrid;
    @FXML
    private GridPane bottomBoardGrid;
    @FXML
    private GridPane objectLegendGrid;

    private enum BoardArrangement {
        LOCAL_STACKED, LOCAL_SIDE_BY_SIDE, NETWORK_PRIMARY_BOTTOM, NETWORK_PRIMARY_TOP
    }

    private record BoardView(boolean reverseRows, boolean reverseColumns) {
        private static final BoardView NORMAL = new BoardView(false, false);
        private static final BoardView ROTATED_180 = new BoardView(true, true);
        private static final BoardView MIRRORED_HORIZONTALLY = new BoardView(false, true);

        int visualRow(int modelRow, int rowCount) {
            return visualIndex(modelRow, rowCount, reverseRows);
        }

        int visualColumn(int modelColumn, int columnCount) {
            return visualIndex(modelColumn, columnCount, reverseColumns);
        }
    }

    private BoardArrangement currentArrangement = null;
    // Cache for gating the debug log — only print when zone dimensions actually change.
    private double lastLoggedZoneH = -1;
    private double lastLoggedZoneW = -1;

    private record CellSizePair(double primary, double secondary) {}

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
        ResponsiveLayout.bindSidebarGrid(gameMain, 320.0, 420.0);
        // Cap boardZone height to gameMain height — prevents board content from
        // inflating the GridPane row beyond the viewport.
        boardZone.maxHeightProperty().bind(gameMain.heightProperty());
        configureViewportFill();
        setupKeyboardControls();
        configureObjectLegend();
        // Re-render whenever the board zone actually changes size.
        boardZone.widthProperty().addListener((obs, o, n)  -> render());
        boardZone.heightProperty().addListener((obs, o, n) -> render());
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
        currentArrangement = null;
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
        BoardArrangement arr = applyBoardLayout();
        if (setup.isLocal()) {
            double cs = computeLocalCellSize(arr);
            if (arr == BoardArrangement.LOCAL_SIDE_BY_SIDE) {
                renderBoard(bottomBoardGrid, gameState.bottomPlayer(), cs, BoardView.MIRRORED_HORIZONTALLY);
                renderBoard(topBoardGrid, gameState.topPlayer(), cs, BoardView.MIRRORED_HORIZONTALLY);
            } else {
                renderBoard(bottomBoardGrid, gameState.bottomPlayer(), cs, BoardView.NORMAL);
                renderBoard(topBoardGrid, gameState.topPlayer(), cs, BoardView.ROTATED_180);
            }
        } else {
            CellSizePair cs = computeNetworkCellSizes();
            if (setup.localSide() == PlayerSide.BOTTOM) {
                renderBoard(bottomBoardGrid, gameState.bottomPlayer(), cs.primary(), BoardView.NORMAL);
                renderBoard(topBoardGrid, gameState.topPlayer(), cs.secondary(), BoardView.NORMAL);
            } else {
                renderBoard(topBoardGrid, gameState.topPlayer(), cs.primary(), BoardView.NORMAL);
                renderBoard(bottomBoardGrid, gameState.bottomPlayer(), cs.secondary(), BoardView.NORMAL);
            }
        }
    }

    private BoardArrangement applyBoardLayout() {
        BoardArrangement desired = computeDesiredArrangement();
        if (desired == currentArrangement) {
            return desired;
        }
        currentArrangement = desired;
        // Reset the log cache so the debug line fires once for the new arrangement.
        lastLoggedZoneH = -1;
        lastLoggedZoneW = -1;
        rebuildBoardZoneLayout(desired);
        return desired;
    }

    private BoardArrangement computeDesiredArrangement() {
        if (!setup.isLocal()) {
            return setup.localSide() == PlayerSide.BOTTOM
                    ? BoardArrangement.NETWORK_PRIMARY_BOTTOM
                    : BoardArrangement.NETWORK_PRIMARY_TOP;
        }
        // Local 2-player: horizontal (wide/landscape) boards go side by side;
        // standard (tall/portrait) boards stay stacked vertically.
        return setup.config().horizontalMode()
                ? BoardArrangement.LOCAL_SIDE_BY_SIDE
                : BoardArrangement.LOCAL_STACKED;
    }

    private void rebuildBoardZoneLayout(BoardArrangement arrangement) {
        // FXML stubs start as visible=false / managed=false so they consume no space
        // before the controller takes over.  Restore full presence now.
        topBoardGrid.setVisible(true);
        topBoardGrid.setManaged(true);
        bottomBoardGrid.setVisible(true);
        bottomBoardGrid.setManaged(true);
        hugRenderedBoard(topBoardGrid);
        hugRenderedBoard(bottomBoardGrid);
        boardZone.getChildren().clear();
        switch (arrangement) {
            case LOCAL_STACKED          -> buildStackedLayout();
            case LOCAL_SIDE_BY_SIDE     -> buildSideBySideLayout();
            case NETWORK_PRIMARY_BOTTOM -> buildNetworkLayout(true);
            case NETWORK_PRIMARY_TOP    -> buildNetworkLayout(false);
        }
    }

    private void buildStackedLayout() {
        StackPane topSlot = centeredBoardSlot(topBoardGrid);
        StackPane bottomSlot = centeredBoardSlot(bottomBoardGrid);
        VBox container = new VBox(SIDE_SPACING, topSlot, bottomSlot);
        container.setAlignment(Pos.CENTER);
        container.setFillWidth(true);
        container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(topSlot, Priority.ALWAYS);
        VBox.setVgrow(bottomSlot, Priority.ALWAYS);

        boardZone.getChildren().add(container);
    }

    /**
     * Local horizontal mode — boards are wide/short (rows=10, cols=20), placed side by side.
     *
     * <p>Gravity assignments ({@link TetrisGameConfig#gravityDirection}):
     * <ul>
     *   <li>P1 (BOTTOM, left board)  — {@code GravityDirection.RIGHT}: pieces fall col0→col19,
     *       blocks pile up at col19 (the visual RIGHT edge in a normal board).</li>
     *   <li>P2 (TOP,    right board) — {@code GravityDirection.LEFT}:  pieces fall col19→col0,
     *       blocks pile up at col0  (the visual LEFT  edge in a normal board).</li>
     * </ul>
     *
     * <p>Target: left board piles on its LEFT edge; right board piles on its RIGHT edge
     * (both stacks build outward, away from the centre gap).
     *
     * <p>The renderer reverses the model-column-to-visual-column mapping, so col0 appears
     * on the visual right and the pile side appears on the visual outer edge. The board
     * node itself remains untransformed, keeping overlays and object labels readable.
     *
     * <p>Controls are unaffected: {@code MOVE_LEFT}/{@code MOVE_RIGHT} move pieces
     * <em>perpendicular</em> to gravity — i.e. up/down in rows, not columns.
     * Only the rendered column mapping changes; the row (Y) axis is unchanged.
     * LEFT key → row−1 (visual UP) and RIGHT key → row+1 (visual DOWN) remain
     * exactly the same on both boards — no key inversion needed.
     */
    private void buildSideBySideLayout() {
        StackPane bottomSlot = centeredBoardSlot(bottomBoardGrid);
        StackPane topSlot = centeredBoardSlot(topBoardGrid);
        HBox container = new HBox(SIDE_SPACING, bottomSlot, topSlot);
        container.setAlignment(Pos.CENTER);
        container.setFillHeight(true);
        container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        HBox.setHgrow(bottomSlot, Priority.ALWAYS);
        HBox.setHgrow(topSlot, Priority.ALWAYS);

        boardZone.getChildren().add(container);
    }

    private static StackPane centeredBoardSlot(GridPane board) {
        StackPane slot = new StackPane(board);
        slot.setAlignment(Pos.CENTER);
        slot.setMinSize(0, 0);
        slot.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return slot;
    }

    private static void hugRenderedBoard(GridPane board) {
        board.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    /**
     * Network mode — local player's board is large/primary (left), opponent's is
     * small/secondary (right). Opponent board rendered right-side-up (spectator view).
     */
    private void buildNetworkLayout(boolean localIsBottom) {
        GridPane primaryGrid   = localIsBottom ? bottomBoardGrid : topBoardGrid;
        GridPane secondaryGrid = localIsBottom ? topBoardGrid    : bottomBoardGrid;

        HBox container = new HBox(SIDE_SPACING, primaryGrid, secondaryGrid);
        container.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(primaryGrid,   Priority.ALWAYS);
        HBox.setHgrow(secondaryGrid, Priority.NEVER);
        primaryGrid.setMaxWidth(Double.MAX_VALUE);
        secondaryGrid.setMinWidth(0);

        boardZone.getChildren().add(container);
    }

    private double computeLocalCellSize(BoardArrangement arrangement) {
        double viewportH = (gameScrollPane != null && gameScrollPane.getViewportBounds() != null)
                ? gameScrollPane.getViewportBounds().getHeight() : 0;
        double zoneH = boardZoneContentHeight();
        double zoneW = boardZoneContentWidth();

        // The zone is still zero during the FXML's first render. Its size listeners
        // trigger a second render after layout; this fallback only keeps that initial
        // render well-defined.
        if (zoneH <= 1 && viewportH > 1) {
            zoneH = Math.max(1.0, viewportH - boardZoneVerticalInsets());
        }

        int rows = gameState.bottomPlayer().board().rows();
        int cols = gameState.bottomPlayer().board().columns();

        if (zoneH <= 1 || zoneW <= 1 || rows <= 0 || cols <= 0) {
            return MIN_CELL_SIZE;
        }

        final double cellSize;
        final boolean fits;
        final String layoutDesc;

        if (arrangement == BoardArrangement.LOCAL_SIDE_BY_SIDE) {
            // Two boards placed horizontally: each board gets roughly half the zone width.
            // Height is the full zone height (one board tall, not two).
            double availH = Math.max(1.0, zoneH - BOARD_CHROME);
            double availW = Math.max(1.0, (zoneW - SIDE_SPACING) / 2.0 - BOARD_CHROME);

            double widthFit  = (availW - CELL_GAP * Math.max(0, cols - 1)) / cols;
            double heightFit = (availH - CELL_GAP * Math.max(0, rows - 1)) / rows;
            double cs = clampCellSize(widthFit, heightFit);

            // Verify-and-shrink: 2×(cols×cs + col-gaps + chrome) + spacing ≤ zoneW
            //                AND rows×cs + row-gaps + chrome ≤ zoneH
            for (int i = 0; i < 200; i++) {
                double bH = rows * cs + Math.max(0, rows - 1) * CELL_GAP + BOARD_CHROME;
                double bW = cols * cs + Math.max(0, cols - 1) * CELL_GAP + BOARD_CHROME;
                if (2.0 * bW + SIDE_SPACING <= zoneW && bH <= zoneH) break;
                cs = Math.max(MIN_CELL_SIZE, cs - 0.5);
                if (cs <= MIN_CELL_SIZE) break;
            }
            cellSize = cs;
            double bH = rows * cs + Math.max(0, rows - 1) * CELL_GAP + BOARD_CHROME;
            double bW = cols * cs + Math.max(0, cols - 1) * CELL_GAP + BOARD_CHROME;
            double totalW = 2.0 * bW + SIDE_SPACING;
            fits = totalW <= zoneW && bH <= zoneH;
            layoutDesc = String.format("totalW=%.1f boardH=%.1f", totalW, bH);

        } else {
            // LOCAL_STACKED: two boards stacked vertically — each board gets half the zone height.
            double availH = Math.max(1.0, (zoneH - SIDE_SPACING - 2.0 * BOARD_CHROME) / 2.0);
            double availW = Math.max(1.0, zoneW - BOARD_CHROME);

            double widthFit  = (availW - CELL_GAP * Math.max(0, cols - 1)) / cols;
            double heightFit = (availH - CELL_GAP * Math.max(0, rows - 1)) / rows;
            double cs = clampCellSize(widthFit, heightFit);

            // Verify-and-shrink: 2×(rows×cs + row-gaps + chrome) + spacing ≤ zoneH
            for (int i = 0; i < 200; i++) {
                double bH = rows * cs + Math.max(0, rows - 1) * CELL_GAP + BOARD_CHROME;
                if (2.0 * bH + SIDE_SPACING <= zoneH) break;
                cs = Math.max(MIN_CELL_SIZE, cs - 0.5);
                if (cs <= MIN_CELL_SIZE) break;
            }
            cellSize = cs;
            double bH = rows * cs + Math.max(0, rows - 1) * CELL_GAP + BOARD_CHROME;
            double totalH = 2.0 * bH + SIDE_SPACING;
            fits = totalH <= zoneH;
            layoutDesc = String.format("totalBoardH=%.1f", totalH);
        }

        // Debug log — gated on dimension change so it is SILENT during game ticks.
        // Only prints when zoneH or zoneW shifts by more than 0.5px (i.e. a real resize).
        boolean dimChanged = Math.abs(zoneH - lastLoggedZoneH) > 0.5
                          || Math.abs(zoneW - lastLoggedZoneW) > 0.5;
        if (dimChanged) {
            lastLoggedZoneH = zoneH;
            lastLoggedZoneW = zoneW;
            double boardZoneActualH = boardZone != null ? boardZone.getHeight()  : -1;
            double boardZoneLayoutY = boardZone != null ? boardZone.getLayoutY() : -1;
            System.out.printf("[Zetris diag] viewportH=%.1f boardZoneH=%.1f boardZoneLayoutY=%.1f%n",
                    viewportH, boardZoneActualH, boardZoneLayoutY);
            System.out.printf("[Zetris layout] %s zoneH=%.1f zoneW=%.1f horizontalMode=%b "
                    + "rows=%d cols=%d cellSize=%.2f %s fits=%b%n",
                    arrangement, zoneH, zoneW, setup.config().horizontalMode(),
                    rows, cols, cellSize, layoutDesc, fits);
        }

        return cellSize;
    }

    private CellSizePair computeNetworkCellSizes() {
        double viewportH = (gameScrollPane != null && gameScrollPane.getViewportBounds() != null)
                ? gameScrollPane.getViewportBounds().getHeight() : 0;
        double zoneH = boardZoneContentHeight();
        double zoneW = boardZoneContentWidth();
        if (zoneH <= 1 && viewportH > 1) {
            zoneH = Math.max(1.0, viewportH - boardZoneVerticalInsets());
        }

        int rows = gameState.bottomPlayer().board().rows();
        int cols = gameState.bottomPlayer().board().columns();

        if (zoneH <= 1 || zoneW <= 1 || rows <= 0 || cols <= 0) {
            return new CellSizePair(MIN_CELL_SIZE, MIN_CELL_SIZE);
        }

        double commonH    = Math.max(1.0, zoneH - BOARD_CHROME);
        double primaryW   = Math.max(1.0, zoneW * NETWORK_PRIMARY_RATIO   - SIDE_SPACING / 2.0 - BOARD_CHROME);
        double secondaryW = Math.max(1.0, zoneW * (1.0 - NETWORK_PRIMARY_RATIO) - SIDE_SPACING / 2.0 - BOARD_CHROME);
        double gapCols = CELL_GAP * Math.max(0, cols - 1);
        double gapRows = CELL_GAP * Math.max(0, rows - 1);

        return new CellSizePair(
                clampCellSize((primaryW   - gapCols) / cols, (commonH - gapRows) / rows),
                clampCellSize((secondaryW - gapCols) / cols, (commonH - gapRows) / rows));
    }

    private double boardZoneContentWidth() {
        return boardZone == null
                ? 0.0
                : Math.max(0.0, boardZone.getWidth()
                        - boardZone.getInsets().getLeft()
                        - boardZone.getInsets().getRight());
    }

    private double boardZoneContentHeight() {
        return boardZone == null
                ? 0.0
                : Math.max(0.0, boardZone.getHeight() - boardZoneVerticalInsets());
    }

    private double boardZoneVerticalInsets() {
        return boardZone == null
                ? 0.0
                : boardZone.getInsets().getTop() + boardZone.getInsets().getBottom();
    }

    private double clampCellSize(double widthFit, double heightFit) {
        double fit = Math.min(widthFit, heightFit);
        return fit < MIN_CELL_SIZE ? Math.max(1, fit) : Math.min(MAX_CELL_SIZE, fit);
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
            keymapTitleLabel.setText("Controls");
            keymapLabel.setText("""
                    Bottom (arrow keys): ← / → move   ↓ drop   ↑ rotate
                    Top (WASD): A / D move   W drop   S rotate""");
            return;
        }

        if (setup.localSide() == PlayerSide.TOP) {
            keymapTitleLabel.setText("Controls — " + gameState.topPlayer().playerName());
            keymapLabel.setText("A / D = move  |  W = drop  |  S = rotate");
        } else {
            keymapTitleLabel.setText("Controls — " + gameState.bottomPlayer().playerName());
            keymapLabel.setText("← / → = move  |  ↓ = drop  |  ↑ = rotate");
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

    private void renderBoard(GridPane grid, TetrisPlayerState player, double cellSize, BoardView view) {
        applyBoardTheme(grid, player.board().themeSide());
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
                TetrisBoardObject object = player.boardObject();
                boolean hasObject = object != null && position.equals(object.position());
                Region cell = hasObject
                        ? createObjectCell(object.type(), cellSize)
                        : new Region();
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

                grid.add(
                        cell,
                        view.visualColumn(column, player.board().columns()),
                        view.visualRow(row, player.board().rows()));
            }
        }

        if (!player.isPlaying()) {
            addGameOverOverlay(grid, player.side());
        }
    }

    private void configureObjectLegend() {
        if (objectLegendGrid == null) {
            return;
        }
        List<StackPane> iconSlots = objectLegendGrid.getChildren().stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .sorted((left, right) -> Integer.compare(
                        gridRow(left), gridRow(right)))
                .toList();
        TetrisItemType[] types = TetrisItemType.values();
        for (int index = 0; index < Math.min(iconSlots.size(), types.length); index++) {
            populateObjectIcon(iconSlots.get(index), types[index], OBJECT_LEGEND_ICON_SIZE);
        }
    }

    private static int gridRow(Node node) {
        Integer row = GridPane.getRowIndex(node);
        return row == null ? 0 : row;
    }

    private static StackPane createObjectCell(TetrisItemType type, double cellSize) {
        StackPane cell = new StackPane();
        double iconSize = Math.max(OBJECT_ICON_MIN_SIZE,
                Math.min(OBJECT_ICON_MAX_SIZE, cellSize * OBJECT_ICON_CELL_RATIO));
        populateObjectIcon(cell, type, iconSize);
        return cell;
    }

    private static void populateObjectIcon(StackPane target, TetrisItemType type, double baseSize) {
        target.getChildren().clear();
        FontIcon baseIcon = createObjectIcon(type.icon(), iconSize(baseSize));
        target.getChildren().add(baseIcon);

        if (type.actorOverlay() != null) {
            FontIcon actorOverlay = createObjectIcon(
                    type.actorOverlay(), iconSize(Math.max(4.0, baseSize * OBJECT_ICON_OVERLAY_RATIO)));
            StackPane.setAlignment(actorOverlay, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(actorOverlay, new Insets(0.5));
            target.getChildren().add(actorOverlay);
        }
    }

    private static FontIcon createObjectIcon(org.kordamp.ikonli.Ikon icon, int size) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(size);
        fontIcon.setIconColor(Color.BLACK);
        fontIcon.getStyleClass().add("object-icon");
        fontIcon.setMouseTransparent(true);
        return fontIcon;
    }

    private static int iconSize(double size) {
        return Math.max(1, (int) Math.round(size));
    }

    private static void setCellSize(Region cell, double size) {
        // setMinSize is intentionally 0,0 — NOT size,size.
        // Cells sized by setMinSize(size) propagate as minimum constraints upward:
        //   cell → board GridPane row/column min → VBox min → boardZone StackPane min
        // This inflates boardZone's reported height above the true viewport, causing
        // computeLocalCellSize to believe more space is available than actually exists.
        // With minSize=0, cells still render at the correct size via prefSize and maxSize.
        cell.setMinSize(0, 0);
        cell.setPrefSize(size, size);
        cell.setMaxSize(size, size);
    }

    static int visualIndex(int modelIndex, int count, boolean reversed) {
        return reversed ? count - 1 - modelIndex : modelIndex;
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
