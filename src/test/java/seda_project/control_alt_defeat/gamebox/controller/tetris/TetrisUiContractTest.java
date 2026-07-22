package seda_project.control_alt_defeat.gamebox.controller.tetris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisEffectState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPiece;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveViewport;

class TetrisUiContractTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable configureJavaFx = () -> {
            Platform.setImplicitExit(false);
            latch.countDown();
        };
        try {
            Platform.startup(configureJavaFx);
        } catch (IllegalStateException ignored) {
            Platform.runLater(configureJavaFx);
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void menuExposesCustomSpeedDualAndHorizontalOptions() throws Exception {
        String fxml = read("/tetris/TetrisMenu.fxml");

        assertTrue(fxml.contains("fx:id=\"customPieceCheckBox\""));
        assertTrue(fxml.contains("fx:id=\"customPieceGrid\""));
        assertTrue(fxml.contains("fx:id=\"customPiecePreviewRow\""));
        assertTrue(fxml.contains("fx:id=\"customPieceCountLabel\""));
        assertTrue(fxml.contains("fx:id=\"speedChoiceBox\""));
        assertTrue(fxml.contains("fx:id=\"dualPieceCheckBox\""));
        assertTrue(fxml.contains("fx:id=\"horizontalModeCheckBox\""));
    }

    @Test
    void customEditorShowsThreeSavedPreviewsAndAllowsRemoval() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        TetrisUiContractTest.class.getResource("/tetris/TetrisMenu.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1080, 720);
                scene.getStylesheets().add(
                        TetrisUiContractTest.class.getResource("/Theme.css").toExternalForm());
                root.applyCss();
                root.layout();

                CheckBox customCheckBox = (CheckBox) loader.getNamespace().get("customPieceCheckBox");
                GridPane editor = (GridPane) loader.getNamespace().get("customPieceGrid");
                Button save = (Button) loader.getNamespace().get("saveCustomPieceButton");
                Label count = (Label) loader.getNamespace().get("customPieceCountLabel");
                HBox previews = (HBox) loader.getNamespace().get("customPiecePreviewRow");
                customCheckBox.fire();

                assertEquals(3, TetrisGameConfig.MAX_CUSTOM_PIECES);
                for (int pieceSize = 1; pieceSize <= TetrisGameConfig.MAX_CUSTOM_PIECES; pieceSize++) {
                    for (int cell = 0; cell < pieceSize; cell++) {
                        ((Button) editor.getChildren().get(cell)).fire();
                    }
                    save.fire();
                    assertEquals("Saved pieces (" + pieceSize + "/3)", count.getText());
                    assertEquals(pieceSize, previews.getChildren().size());
                }

                assertTrue(save.isDisabled());
                VBox firstPreview = (VBox) previews.getChildren().getFirst();
                Button remove = (Button) firstPreview.getChildren().get(2);
                remove.fire();

                assertEquals("Saved pieces (2/3)", count.getText());
                assertEquals(2, previews.getChildren().size());
                assertFalse(save.isDisabled());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void menuSpeedChoicesMapToConcreteGravityValues() {
        assertEquals(750, TetrisMenuController.gravityMillisForSpeed("Slow"));
        assertEquals(TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                TetrisMenuController.gravityMillisForSpeed("Normal"));
        assertEquals(320, TetrisMenuController.gravityMillisForSpeed("Fast"));
        assertEquals(TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                TetrisMenuController.gravityMillisForSpeed(null));
    }

    @Test
    void menuHighlightsSpeedChoicesOnHover() throws Exception {
        String css = read("/tetris/TetrisMenu.css");

        assertTrue(css.contains(".combo-box-popup .list-view .list-cell:filled:hover"));
        assertTrue(css.contains("-fx-background-color: -sh-accent"));
    }

    @Test
    void boardsUseTheBlueAndPurpleThemePalettes() throws Exception {
        String css = read("/tetris/TetrisMenu.css");

        assertTrue(css.contains("-opponent-accent: #5d7fbd"));
        assertTrue(css.contains(".tetris-board-opponent .board-cell"));
        assertEquals("#DDD4F5", TetrisGameController.blockColor(PlayerSide.TOP, 0));
        assertEquals("#B3D4F5", TetrisGameController.blockColor(PlayerSide.BOTTOM, 0));

        GridPane board = new GridPane();
        TetrisGameController.applyBoardTheme(board, PlayerSide.TOP);
        assertTrue(board.getStyleClass().contains("tetris-board-opponent"));
        TetrisGameController.applyBoardTheme(board, PlayerSide.BOTTOM);
        assertFalse(board.getStyleClass().contains("tetris-board-opponent"));
    }

    @Test
    void eachPlayerDrawsRandomlyFromTheConfiguredPieceSet() {
        TetrisGameConfig config = TetrisGameConfig.defaultConfig();
        Random random = new Random(42);

        List<String> draws = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> TetrisGameController.randomPiece(config, random).name())
                .toList();

        assertTrue(draws.stream().distinct().count() > 1);
    }

    @Test
    void dualModeRandomSelectionReturnsTwoTetrominoesFromTheFirstDraw() {
        TetrisGameConfig config = new TetrisGameConfig(
                List.of("Standard"),
                List.of(),
                TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                true,
                false);

        PieceShape firstDraw = TetrisGameController.randomPiece(config, new Random(7));

        assertTrue(firstDraw.name().startsWith("Dual "));
        assertEquals(8, firstDraw.cells().size());
    }

    @Test
    void horizontalDualGameStartsWithWideBoardsAndDualPieces() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            TetrisGameController controller = null;
            try {
                FXMLLoader loader = new FXMLLoader(
                        TetrisUiContractTest.class.getResource("/tetris/TetrisGame.fxml"));
                loader.load();
                controller = loader.getController();
                controller.setRouteData(new TetrisGameSetup(
                        "Bottom",
                        "Top",
                        new TetrisGameConfig(
                                List.of("Standard"),
                                List.of(),
                                TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                                true,
                                true)));

                TetrisGameState state = controllerState(controller);
                assertEquals(10, state.bottomPlayer().board().rows());
                assertEquals(20, state.bottomPlayer().board().columns());
                assertNotNull(state.bottomPlayer().activePiece());
                assertNotNull(state.topPlayer().activePiece());
                assertEquals(8, state.bottomPlayer().activePiece().shape().cells().size());
                assertEquals(8, state.topPlayer().activePiece().shape().cells().size());

                assertWideGrid((GridPane) loader.getNamespace().get("bottomBoardGrid"));
                assertWideGrid((GridPane) loader.getNamespace().get("topBoardGrid"));
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                stopGameLoop(controller);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void visualIndexMappingMirrorsBoardDataWithoutTransformingNodes() {
        assertEquals(2, TetrisGameController.visualIndex(2, 10, false));
        assertEquals(7, TetrisGameController.visualIndex(2, 10, true));
        assertEquals(19, TetrisGameController.visualIndex(0, 20, true));
        assertEquals(0, TetrisGameController.visualIndex(19, 20, true));
    }

    @Test
    void localBoardsAreCenteredTransparentAndUprightAtMaximizedSizes() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                for (boolean horizontal : new boolean[] {false, true}) {
                    for (double[] viewport : new double[][] {{1366, 768}, {1920, 1080}}) {
                        assertLocalBoardPresentation(viewport[0], viewport[1], horizontal);
                    }
                }
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void playerCardsAvoidBoardsWithLongNamesAndActiveEffects() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            TetrisGameController controller = null;
            try {
                FXMLLoader loader = new FXMLLoader(
                        TetrisUiContractTest.class.getResource("/tetris/TetrisGame.fxml"));
                Parent root = loader.load();
                controller = loader.getController();
                TetrisGameConfig config = new TetrisGameConfig(
                        List.of("Standard"),
                        List.of(),
                        TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                        false,
                        true);
                controller.setRouteData(new TetrisGameSetup("Bottom", "Top", config));

                ResponsiveViewport viewport = new ResponsiveViewport(root);
                Scene scene = new Scene(viewport, 1366, 768);
                scene.getStylesheets().add(
                        TetrisUiContractTest.class.getResource("/Theme.css").toExternalForm());
                viewport.resize(1366, 768);
                pulse(viewport);

                TetrisGameState state = controllerState(controller);
                TetrisPlayerState bottom = withRichPlayerCard(
                        state.bottomPlayer(),
                        new TetrisEffectState(
                                200, 100, 100, 0,
                                TetrisItemType.SLOW_SELF,
                                TetrisItemType.ROTATION_DELAY_SELF));
                TetrisPlayerState top = withRichPlayerCard(
                        state.topPlayer(),
                        new TetrisEffectState(
                                50, 100, 100, 0,
                                TetrisItemType.SPEED_UP_OPPONENT,
                                TetrisItemType.ROTATION_DELAY_OPPONENT));
                setControllerState(controller, new TetrisGameState(bottom, top, config, state.status()));
                render(controller);
                pulse(viewport);

                GridPane topBoard = (GridPane) loader.getNamespace().get("topBoardGrid");
                GridPane bottomBoard = (GridPane) loader.getNamespace().get("bottomBoardGrid");
                assertCardDoesNotOverlapBoard(topBoard, "horizontal top rich card");
                assertCardDoesNotOverlapBoard(bottomBoard, "horizontal bottom rich card");
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                stopGameLoop(controller);
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void radiusPreviewFullyEnclosesOutermostAffectedCells() {
        for (double cellSize : new double[] {12.0, 14.0, 21.45, 32.80}) {
            double cellStep = cellSize + 1.0;
            double halfCell = cellSize / 2.0;
            double outerCornerDistance = Math.hypot(3.0 * cellStep + halfCell, halfCell);

            assertEquals(
                    outerCornerDistance,
                    TetrisGameController.radiusPreviewRadius(cellSize),
                    0.0001,
                    "preview radius for cell size " + cellSize);
        }
    }

    @Test
    void radiusPreviewIsCenteredOnBombCellInBothBoardOrientations() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            TetrisGameController controller = null;
            try {
                for (boolean horizontalMode : new boolean[] {false, true}) {
                    FXMLLoader loader = new FXMLLoader(
                            TetrisUiContractTest.class.getResource("/tetris/TetrisGame.fxml"));
                    Parent root = loader.load();
                    controller = loader.getController();
                    TetrisGameConfig config = new TetrisGameConfig(
                            List.of("Standard"),
                            List.of(),
                            TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                            false,
                            horizontalMode);
                    controller.setRouteData(new TetrisGameSetup("Bottom", "Top", config));
                    stopGameLoop(controller);

                    ResponsiveViewport viewport = new ResponsiveViewport(root);
                    Scene scene = new Scene(viewport, 1366, 768);
                    scene.getStylesheets().add(
                            TetrisUiContractTest.class.getResource("/Theme.css").toExternalForm());
                    viewport.resize(1366, 768);
                    pulse(viewport);

                    TetrisGameState state = controllerState(controller);
                    BoardPosition position = horizontalMode
                            ? new BoardPosition(5, 10)
                            : new BoardPosition(10, 5);
                    TetrisPiece bomb = new TetrisPiece(
                            PieceShape.bombShape(PieceType.RADIUS_BOMB),
                            position,
                            Rotation.SPAWN);
                    TetrisPlayerState bottom = state.bottomPlayer().withActivePiece(bomb);
                    setControllerState(controller,
                            new TetrisGameState(bottom, state.topPlayer(), config, state.status()));
                    render(controller);
                    pulse(viewport);

                    GridPane board = (GridPane) loader.getNamespace().get("bottomBoardGrid");
                    Circle preview = board.getChildren().stream()
                            .filter(Circle.class::isInstance)
                            .map(Circle.class::cast)
                            .findFirst()
                            .orElseThrow();
                    StackPane bombCell = board.getChildren().stream()
                            .filter(StackPane.class::isInstance)
                            .map(StackPane.class::cast)
                            .filter(cell -> cell.getStyleClass().contains("board-cell-bomb"))
                            .findFirst()
                            .orElseThrow();

                    Bounds previewBounds = preview.localToScene(preview.getBoundsInLocal());
                    Bounds bombBounds = bombCell.localToScene(bombCell.getBoundsInLocal());
                    String context = horizontalMode ? "horizontal" : "vertical";
                    assertEquals(
                            (bombBounds.getMinX() + bombBounds.getMaxX()) / 2.0,
                            (previewBounds.getMinX() + previewBounds.getMaxX()) / 2.0,
                            0.01,
                            context + " preview center x");
                    assertEquals(
                            (bombBounds.getMinY() + bombBounds.getMaxY()) / 2.0,
                            (previewBounds.getMinY() + previewBounds.getMaxY()) / 2.0,
                            0.01,
                            context + " preview center y");
                    controller = null;
                }
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                stopGameLoop(controller);
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void networkBoardsUseTheSameEqualSizeLayoutsAsLocalMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                assertNetworkBoardPresentation(false);
                assertNetworkBoardPresentation(true);
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    void gameSidebarShowsScoresWithoutSpeedForBothPlayersAndEverySpecialObject() throws Exception {
        String fxml = read("/tetris/TetrisGame.fxml");

        int statusLineStart = fxml.indexOf("fx:id=\"statusLineLabel\"");
        int statusLineEnd = fxml.indexOf("/>", statusLineStart);
        assertTrue(statusLineStart >= 0 && statusLineEnd > statusLineStart);
        String statusLine = fxml.substring(statusLineStart, statusLineEnd);
        assertTrue(statusLine.contains("maxWidth=\"Infinity\""));
        assertTrue(statusLine.contains("wrapText=\"true\""));
        assertFalse(fxml.contains("Zetris"));
        assertFalse(fxml.contains("Points"));
        assertFalse(fxml.contains("Controls"));
        assertFalse(fxml.contains("maxHeight=\"200\""));
        for (TetrisItemType type : TetrisItemType.values()) {
            assertNotNull(type.icon(), () -> type + " must have a vector icon");
        }

        String css = read("/tetris/TetrisMenu.css");
        assertTrue(css.contains("-fx-background-color: #EF9F27"));
        assertTrue(css.contains(".object-icon"));
        assertTrue(css.contains("-fx-icon-color: #000000"));
        assertTrue(css.contains(".player-card"));
        assertTrue(css.contains("-fx-padding: 22;"));
        assertTrue(css.contains("-fx-max-width: 340;"));
        assertTrue(css.contains("-fx-font-family: \"Monospaced\""));
        assertTrue(fxml.contains("fx:id=\"objectLegendGrid\""));
        assertFalse(fxml.contains("object-symbol-glyph"));
        assertFalse(fxml.contains("Segoe UI Emoji"));
    }

    @Test
    void boardSpecialObjectsUseSharedGlyphNodesAtSmallCellSizes() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                for (boolean horizontal : new boolean[] {false, true}) {
                    for (double cellSize : new double[] {12.0, 14.0}) {
                        assertObjectGlyphs(horizontal, cellSize);
                    }
                }
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static void assertObjectGlyphs(boolean horizontal, double cellSize) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                TetrisUiContractTest.class.getResource("/tetris/TetrisGame.fxml"));
        Parent root = loader.load();
        TetrisGameController controller = loader.getController();
        try {
            TetrisGameConfig config = new TetrisGameConfig(
                    List.of("Standard"),
                    List.of(),
                    TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                    false,
                    horizontal);
            controller.setRouteData(new TetrisGameSetup("Bottom", "Top", config));

            ResponsiveViewport viewport = new ResponsiveViewport(root);
            Scene scene = new Scene(viewport, 1366, 768);
            scene.getStylesheets().add(
                    TetrisUiContractTest.class.getResource("/Theme.css").toExternalForm());
            viewport.resize(1366, 768);
            for (int pulse = 0; pulse < 3; pulse++) {
                viewport.applyCss();
                viewport.layout();
            }

            GridPane[] boards = {
                    (GridPane) loader.getNamespace().get("topBoardGrid"),
                    (GridPane) loader.getNamespace().get("bottomBoardGrid")};
            PlayerSide[] sides = {PlayerSide.TOP, PlayerSide.BOTTOM};
            for (Node legendIcon : root.lookupAll(".object-symbol")) {
                assertAmberBadge((Region) legendIcon, "sidebar legend");
            }
            Method renderBoard = TetrisGameController.class.getDeclaredMethod(
                    "renderBoard",
                    GridPane.class,
                    TetrisPlayerState.class,
                    double.class,
                    Class.forName(TetrisGameController.class.getName() + "$BoardView"));
            renderBoard.setAccessible(true);
            Class<?> boardViewType = Class.forName(TetrisGameController.class.getName() + "$BoardView");
            Field normalField = boardViewType.getDeclaredField("NORMAL");
            normalField.setAccessible(true);
            Object normalView = normalField.get(null);

            for (TetrisItemType type : TetrisItemType.values()) {
                BoardPosition objectPosition = new BoardPosition(4, 4);
                for (int boardIndex = 0; boardIndex < boards.length; boardIndex++) {
                    PlayerSide side = sides[boardIndex];
                    GridPane board = boards[boardIndex];
                    TetrisPlayerState player = new TetrisPlayerState(
                            side == PlayerSide.BOTTOM ? "Bottom" : "Top",
                            side,
                            TetrisBoard.createDefault(horizontal, side),
                            null,
                            0,
                            PlayerStatus.PLAYING,
                            null,
                            new TetrisBoardObject(type, objectPosition),
                            null,
                            List.of());
                    renderBoard.invoke(controller, board, player, cellSize, normalView);
                    root.applyCss();
                    root.layout();

                    Node cell = board.getChildren().stream()
                            .filter(node -> Integer.valueOf(objectPosition.row()).equals(GridPane.getRowIndex(node)))
                            .filter(node -> Integer.valueOf(objectPosition.column()).equals(GridPane.getColumnIndex(node)))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Missing board object cell for " + type));
                    assertTrue(cell instanceof StackPane, type + " should use a stack cell");
                    assertAmberBadge((Region) cell, side + " board");
                    assertTrue(cell.getStyleClass().contains("board-cell-object"), type.toString());
                    assertTrue(cell.getStyleClass().contains("board-cell"), type.toString());
                    assertVectorIconStack((StackPane) cell, type, side + " board " + type);
                }
            }

            List<Node> legendSlots = root.lookupAll(".object-symbol").stream()
                    .sorted((left, right) -> Integer.compare(
                            rowIndex(left), rowIndex(right)))
                    .toList();
            assertEquals(TetrisItemType.values().length, legendSlots.size());
            for (int index = 0; index < legendSlots.size(); index++) {
                assertVectorIconStack((StackPane) legendSlots.get(index),
                        TetrisItemType.values()[index], "sidebar " + index);
            }
        } finally {
            stopGameLoop(controller);
        }
    }

    private static int rowIndex(Node node) {
        Integer row = GridPane.getRowIndex(node);
        return row == null ? 0 : row;
    }

    private static void assertVectorIconStack(StackPane stack, TetrisItemType type, String context) {
        List<FontIcon> icons = stack.getChildren().stream()
                .filter(FontIcon.class::isInstance)
                .map(FontIcon.class::cast)
                .toList();
        int expectedCount = type.actorOverlay() == null ? 1 : 2;
        assertEquals(expectedCount, icons.size(), context + " icon count");
        FontIcon base = icons.get(0);
        assertEquals(type.icon(), base.getIconCode(), context + " base icon");
        assertEquals(Color.BLACK, base.getIconColor(), context + " base color");
        assertTrue(base.getIconSize() >= 8, context + " base size");
        if (type.actorOverlay() != null) {
            FontIcon overlay = icons.get(1);
            assertEquals(type.actorOverlay(), overlay.getIconCode(), context + " overlay icon");
            assertEquals(Color.BLACK, overlay.getIconColor(), context + " overlay color");
            assertTrue(overlay.getIconSize() < base.getIconSize(), context + " overlay size");
        }
    }

    private static void assertAmberBadge(Region badge, String context) {
        assertNotNull(badge.getBackground(), context + " is missing a background");
        assertTrue(badge.getBackground().getFills().stream().allMatch(fill ->
                        fill.getFill() instanceof Color color
                                && color.equals(Color.web("#EF9F27"))),
                () -> context + " is not amber: " + badge.getBackground());
    }

    @Test
    void tetrisScreensKeepResponsiveScrollAndTwoPanelContracts() throws Exception {
        String menu = read("/tetris/TetrisMenu.fxml");
        String game = read("/tetris/TetrisGame.fxml");

        assertTrue(menu.contains("fx:id=\"tetrisMenuMain\""));
        assertTrue(menu.contains("fitToWidth=\"true\""));
        assertTrue(menu.contains("vbarPolicy=\"AS_NEEDED\""));
        assertTrue(game.contains("fx:id=\"gameMain\""));
        assertTrue(game.contains("fitToHeight=\"true\""));
        assertTrue(game.contains("fitToWidth=\"true\""));
        assertTrue(game.contains("vbarPolicy=\"AS_NEEDED\""));
    }

    private static String read(String resource) throws Exception {
        try (InputStream input = TetrisUiContractTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, () -> "Missing resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static TetrisGameState controllerState(TetrisGameController controller)
            throws ReflectiveOperationException {
        Field field = TetrisGameController.class.getDeclaredField("gameState");
        field.setAccessible(true);
        return (TetrisGameState) field.get(controller);
    }

    private static void assertWideGrid(GridPane grid) {
        int maxColumn = grid.getChildren().stream()
                .mapToInt(node -> GridPane.getColumnIndex(node) == null ? 0 : GridPane.getColumnIndex(node))
                .max()
                .orElse(-1);
        int maxRow = grid.getChildren().stream()
                .mapToInt(node -> GridPane.getRowIndex(node) == null ? 0 : GridPane.getRowIndex(node))
                .max()
                .orElse(-1);

        assertEquals(19, maxColumn);
        assertEquals(9, maxRow);
    }

    private static void assertLocalBoardPresentation(double width, double height, boolean horizontal)
            throws Exception {
        FXMLLoader loader = new FXMLLoader(
                TetrisUiContractTest.class.getResource("/tetris/TetrisGame.fxml"));
        Parent root = loader.load();
        TetrisGameController controller = loader.getController();
        try {
            controller.setRouteData(new TetrisGameSetup(
                    "Bottom",
                    "Top",
                    new TetrisGameConfig(
                            List.of("Standard"),
                            List.of(),
                            TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                            false,
                            horizontal)));

            ResponsiveViewport viewport = new ResponsiveViewport(root);
            Scene scene = new Scene(viewport, width, height);
            scene.getStylesheets().add(
                    TetrisUiContractTest.class.getResource("/Theme.css").toExternalForm());
            viewport.resize(width, height);
            for (int pulse = 0; pulse < 3; pulse++) {
                viewport.applyCss();
                viewport.layout();
            }

            StackPane boardZone = (StackPane) loader.getNamespace().get("boardZone");
            GridPane topBoard = (GridPane) loader.getNamespace().get("topBoardGrid");
            GridPane bottomBoard = (GridPane) loader.getNamespace().get("bottomBoardGrid");
            String context = (horizontal ? "horizontal" : "vertical") + " " + (int) width + "x" + (int) height;

            assertTransparent(boardZone.getBackground(), context);
            assertIdentityTransform(topBoard, context + " top board");
            assertIdentityTransform(bottomBoard, context + " bottom board");
            assertCenteredInSlot(topBoard, context + " top board");
            assertCenteredInSlot(bottomBoard, context + " bottom board");
            assertInsideViewport(topBoard, width, height, context + " top board");
            assertInsideViewport(bottomBoard, width, height, context + " bottom board");
            if (width == 1366.0 && height == 768.0 && !horizontal) {
                ScrollPane objectScroll = (ScrollPane) root.lookup(".object-scroll");
                GridPane objectLegend = (GridPane) loader.getNamespace().get("objectLegendGrid");
                assertTrue(objectScroll.getViewportBounds().getHeight() > 200.0,
                        "Special Objects must get more than the old 200px cap");
                assertTrue(objectLegend.getHeight() <= objectScroll.getViewportBounds().getHeight() + 1.0,
                        "All Special Objects should be visible without scrolling");
            }

            assertPlayerCard((StackPane) topBoard.getParent(), "Top", PlayerSide.TOP,
                    "WASD: A / D move, W drop, S rotate", context + " top card");
            assertPlayerCard((StackPane) bottomBoard.getParent(), "Bottom", PlayerSide.BOTTOM,
                    "Arrow keys: ← / → move, ↓ drop, ↑ rotate", context + " bottom card");
            assertCardDoesNotOverlapBoard(topBoard, context + " top card");
            assertCardDoesNotOverlapBoard(bottomBoard, context + " bottom card");

            // Recorded from the pre-overlay implementation at these same viewports.
            if (width == 1366.0 && height == 768.0) {
                assertBoardSize(topBoard, horizontal ? 466.0 : 169.0,
                        horizontal ? 239.0 : 329.0, context);
                assertBoardSize(bottomBoard, horizontal ? 466.0 : 169.0,
                        horizontal ? 239.0 : 329.0, context);
            } else if (width == 1920.0 && height == 1080.0) {
                assertBoardSize(topBoard, horizontal ? 689.0 : 249.0,
                        horizontal ? 349.0 : 485.0, context);
                assertBoardSize(bottomBoard, horizontal ? 689.0 : 249.0,
                        horizontal ? 349.0 : 485.0, context);
            }

            StackPane topSlot = (StackPane) topBoard.getParent();
            StackPane bottomSlot = (StackPane) bottomBoard.getParent();
            assertCardsDoNotOverlap(topSlot, bottomSlot, context);
            if (horizontal) {
                assertTrue(topSlot.getParent() instanceof HBox, context);
                assertEquals(bottomSlot.getWidth(), topSlot.getWidth(), 1.0, context);
            } else {
                assertTrue(topSlot.getParent() instanceof VBox, context);
                assertEquals(bottomSlot.getHeight(), topSlot.getHeight(), 1.0, context);
            }

            Method addOverlay = TetrisGameController.class.getDeclaredMethod(
                    "addGameOverOverlay", GridPane.class, PlayerSide.class);
            addOverlay.setAccessible(true);
            addOverlay.invoke(controller, topBoard, PlayerSide.TOP);
            addOverlay.invoke(controller, bottomBoard, PlayerSide.BOTTOM);
            assertOverlayUpright(topBoard, context + " top overlay");
            assertOverlayUpright(bottomBoard, context + " bottom overlay");
        } finally {
            stopGameLoop(controller);
        }
    }

    private static void assertNetworkBoardPresentation(boolean horizontal) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                TetrisUiContractTest.class.getResource("/tetris/TetrisGame.fxml"));
        Parent root = loader.load();
        TetrisGameController controller = loader.getController();
        try {
            TetrisGameConfig config = new TetrisGameConfig(
                    List.of("Standard"),
                    List.of(),
                    TetrisGameConfig.DEFAULT_GRAVITY_MILLIS,
                    false,
                    horizontal);
            controller.setRouteData(new TetrisGameRouteData(
                    TetrisGameSetup.host("Bottom", "Top", config),
                    null,
                    null));

            ResponsiveViewport viewport = new ResponsiveViewport(root);
            Scene scene = new Scene(viewport, 1366, 768);
            scene.getStylesheets().add(
                    TetrisUiContractTest.class.getResource("/Theme.css").toExternalForm());
            viewport.resize(1366, 768);
            for (int pulse = 0; pulse < 3; pulse++) {
                viewport.applyCss();
                viewport.layout();
            }

            StackPane boardZone = (StackPane) loader.getNamespace().get("boardZone");
            GridPane topBoard = (GridPane) loader.getNamespace().get("topBoardGrid");
            GridPane bottomBoard = (GridPane) loader.getNamespace().get("bottomBoardGrid");
            String context = "network " + (horizontal ? "horizontal" : "vertical");

            assertTransparent(boardZone.getBackground(), context);
            assertTrue(topBoard.getWidth() > 0.0, context + " top board is visible");
            assertEquals(bottomBoard.getWidth(), topBoard.getWidth(), 1.0,
                    context + " board widths");
            assertEquals(bottomBoard.getHeight(), topBoard.getHeight(), 1.0,
                    context + " board heights");

            StackPane topSlot = (StackPane) topBoard.getParent();
            StackPane bottomSlot = (StackPane) bottomBoard.getParent();
            assertEquals(horizontal ? HBox.class : VBox.class,
                    topSlot.getParent().getClass(), context + " arrangement");
            assertEquals(bottomSlot.getWidth(), topSlot.getWidth(), 1.0,
                    context + " slot widths");
            assertEquals(bottomSlot.getHeight(), topSlot.getHeight(), 1.0,
                    context + " slot heights");
            assertPlayerCard(topSlot, "Top", PlayerSide.TOP,
                    "WASD: A / D move, W drop, S rotate", context + " top card");
            assertPlayerCard(bottomSlot, "Bottom", PlayerSide.BOTTOM,
                    "Arrow keys: ← / → move, ↓ drop, ↑ rotate", context + " bottom card");
        } finally {
            stopGameLoop(controller);
        }
    }

    private static void assertTransparent(Background background, String context) {
        if (background == null || background.getFills().isEmpty()) {
            return;
        }
        assertTrue(background.getFills().stream().allMatch(fill ->
                        fill.getFill() instanceof Color color && color.getOpacity() == 0.0),
                () -> context + " board zone has a visible background");
    }

    private static void assertIdentityTransform(Region node, String context) {
        assertEquals(0.0, node.getRotate(), 0.0, context);
        assertEquals(1.0, node.getScaleX(), 0.0, context);
        assertEquals(1.0, node.getScaleY(), 0.0, context);
    }

    private static void assertCenteredInSlot(GridPane board, String context) {
        StackPane slot = (StackPane) board.getParent();
        Bounds boardBounds = board.getBoundsInParent();
        Bounds slotBounds = slot.getLayoutBounds();
        assertEquals(slotBounds.getCenterX(), boardBounds.getCenterX(), 1.0, context + " x-center");
        assertEquals(slotBounds.getCenterY(), boardBounds.getCenterY(), 1.0, context + " y-center");
        assertTrue(boardBounds.getWidth() <= slotBounds.getWidth() + 1.0, context + " width");
        assertTrue(boardBounds.getHeight() <= slotBounds.getHeight() + 1.0, context + " height");
        // JavaFX can snap the styled grid's outer border by up to two pixels per edge.
        assertEquals(board.prefWidth(-1), boardBounds.getWidth(), 5.0, context + " natural width");
        assertEquals(board.prefHeight(-1), boardBounds.getHeight(), 5.0, context + " natural height");
    }

    private static void assertInsideViewport(Region node, double width, double height, String context) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinX() >= -1.0 && bounds.getMaxX() <= width + 1.0, context + " scene width");
        assertTrue(bounds.getMinY() >= -1.0 && bounds.getMaxY() <= height + 1.0, context + " scene height");
    }

    private static void assertBoardSize(GridPane board, double expectedWidth, double expectedHeight,
                                        String context) {
        assertEquals(expectedWidth, board.getWidth(), 0.1, context + " width");
        assertEquals(expectedHeight, board.getHeight(), 0.1, context + " height");
    }

    private static void assertPlayerCard(StackPane slot, String expectedPlayer, PlayerSide expectedSide,
                                         String expectedControls, String context) {
        List<StackPane> cards = slot.getChildren().stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .filter(card -> card.getStyleClass().contains("player-card"))
                .toList();
        assertEquals(1, cards.size(), context + " count");

        StackPane card = cards.getFirst();
        assertFalse(card.isManaged(), context + " must not participate in sizing");
        assertEquals(340.0, card.getWidth(), 0.1, context + " width");
        assertEquals(4.0, card.getLayoutX(), 0.1, context + " inset x");
        assertEquals(4.0, card.getLayoutY(), 0.1, context + " inset y");
        assertEquals(expectedPlayer, ((Label) card.lookup(".player-card-name")).getText(), context + " player");
        assertEquals("Score: 0",
                ((Label) card.lookup(".player-card-stats")).getText(), context + " stats");
        assertEquals(expectedControls, ((Label) card.lookup(".player-card-controls")).getText(),
                context + " controls");

        String accent = TetrisGameController.blockColor(expectedSide, 0);
        assertTrue(card.getStyle().contains(accent + "; -fx-border-width: 1 1 1 4;"),
                context + " left strip must use the board piece color");
        assertTrue(card.getStyle().contains("-fx-background-color:"), context + " background tint");
        String nameStyle = card.lookup(".player-card-name").getStyle();
        assertTrue(nameStyle.contains("-fx-text-fill:"), context + " name color");
        assertFalse(nameStyle.contains(accent), context + " name must use a darker shade");
    }

    private static void assertCardsDoNotOverlap(StackPane topSlot, StackPane bottomSlot,
                                                String context) {
        StackPane topCard = topSlot.getChildren().stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .filter(card -> card.getStyleClass().contains("player-card"))
                .findFirst()
                .orElseThrow();
        StackPane bottomCard = bottomSlot.getChildren().stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .filter(card -> card.getStyleClass().contains("player-card"))
                .findFirst()
                .orElseThrow();
        assertFalse(topCard.localToScene(topCard.getBoundsInLocal())
                        .intersects(bottomCard.localToScene(bottomCard.getBoundsInLocal())),
                context + " cards overlap");
    }

    private static void assertCardDoesNotOverlapBoard(GridPane board, String context) {
        StackPane slot = (StackPane) board.getParent();
        StackPane card = slot.getChildren().stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .filter(candidate -> candidate.getStyleClass().contains("player-card"))
                .findFirst()
                .orElseThrow();
        assertFalse(
                card.localToScene(card.getBoundsInLocal())
                        .intersects(board.localToScene(board.getBoundsInLocal())),
                context + " overlaps playable grid cells");
    }

    private static TetrisPlayerState withRichPlayerCard(
            TetrisPlayerState player,
            TetrisEffectState effects) {
        return new TetrisPlayerState(
                "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW",
                player.side(),
                player.board(),
                player.activePiece(),
                player.score(),
                player.status(),
                player.finalScore(),
                player.boardObject(),
                effects,
                player.queuedShapes());
    }

    private static void setControllerState(
            TetrisGameController controller,
            TetrisGameState state) throws ReflectiveOperationException {
        Field field = TetrisGameController.class.getDeclaredField("gameState");
        field.setAccessible(true);
        field.set(controller, state);
    }

    private static void render(TetrisGameController controller) throws ReflectiveOperationException {
        Method method = TetrisGameController.class.getDeclaredMethod("render");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void pulse(ResponsiveViewport viewport) {
        for (int pulse = 0; pulse < 3; pulse++) {
            viewport.applyCss();
            viewport.layout();
        }
    }

    private static void assertOverlayUpright(GridPane board, String context) {
        Label overlay = board.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .filter(label -> label.getStyleClass().contains("board-game-over"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(context + " is missing"));
        assertIdentityTransform(overlay, context);
        assertEquals(1.0, overlay.getLocalToSceneTransform().getMxx(), 0.0001, context);
        assertEquals(1.0, overlay.getLocalToSceneTransform().getMyy(), 0.0001, context);
    }

    private static void stopGameLoop(TetrisGameController controller) {
        if (controller == null) {
            return;
        }
        try {
            var method = TetrisGameController.class.getDeclaredMethod("stopGameLoop");
            method.setAccessible(true);
            method.invoke(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not stop Tetris test loop.", exception);
        }
    }
}
