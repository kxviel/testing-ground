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
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
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
        assertTrue(fxml.contains("fx:id=\"speedChoiceBox\""));
        assertTrue(fxml.contains("fx:id=\"dualPieceCheckBox\""));
        assertTrue(fxml.contains("fx:id=\"horizontalModeCheckBox\""));
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
    void opponentBoardUsesTheBlueThemePalette() throws Exception {
        String css = read("/tetris/TetrisMenu.css");

        assertTrue(css.contains("-opponent-accent: #5d7fbd"));
        assertTrue(css.contains(".tetris-board-opponent .board-cell"));
        assertEquals("#5D7FBD", TetrisGameController.blockColor(PlayerSide.TOP, 0));
        assertEquals("#7873B8", TetrisGameController.blockColor(PlayerSide.BOTTOM, 0));

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
    void gameSidebarShowsLiveSpeedForBothPlayersAndEverySpecialObject() throws Exception {
        String fxml = read("/tetris/TetrisGame.fxml");

        assertTrue(fxml.contains("fx:id=\"topSpeedLabel\""));
        assertTrue(fxml.contains("fx:id=\"bottomSpeedLabel\""));
        assertEquals("Speed: 320 ms/step", TetrisGameController.speedText(320));

        for (TetrisItemType type : TetrisItemType.values()) {
            assertNotNull(type.icon(), () -> type + " must have a vector icon");
        }

        String css = read("/tetris/TetrisMenu.css");
        assertTrue(css.contains("-fx-background-color: #EF9F27"));
        assertTrue(css.contains(".object-icon"));
        assertTrue(css.contains("-fx-icon-color: #000000"));
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

            StackPane topSlot = (StackPane) topBoard.getParent();
            StackPane bottomSlot = (StackPane) bottomBoard.getParent();
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
