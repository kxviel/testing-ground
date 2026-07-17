package seda_project.control_alt_defeat.gamebox.controller.tetris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;

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
    void gameSidebarShowsLiveSpeedForBothPlayersAndEverySpecialObject() throws Exception {
        String fxml = read("/tetris/TetrisGame.fxml");

        assertTrue(fxml.contains("fx:id=\"topSpeedLabel\""));
        assertTrue(fxml.contains("fx:id=\"bottomSpeedLabel\""));
        assertEquals("Speed: 320 ms/step", TetrisGameController.speedText(320));

        for (String symbol : new String[] {"+", "-", "R", "r", "S", "*", "v", "P", "T", "G"}) {
            assertTrue(fxml.contains("text=\"" + symbol + "\""), () -> "Missing special object " + symbol);
        }
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
