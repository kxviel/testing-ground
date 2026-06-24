package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoard;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameStatus;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessLanDiscoveryService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexChessEndToEndSmokeTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ignored) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void localUiAcceptsAlternatingMoves() throws Exception {
        LoadedFxml<HexChessGameController> game = loadFxml("/hexchess/HexChessGame.fxml");

        clickGameCell(game.controller(), "b1");
        clickGameCell(game.controller(), "b3");
        assertLastMove(game.controller(), "b1-b3");
        assertEquals(HexPieceColor.BLACK, state(game.controller()).turn());

        clickGameCell(game.controller(), "b7");
        clickGameCell(game.controller(), "b5");
        assertLastMove(game.controller(), "b7-b5");
        assertEquals(HexPieceColor.WHITE, state(game.controller()).turn());
    }

    @Test
    void botUiRespondsAfterWhiteMove() throws Exception {
        LoadedFxml<HexChessGameController> game = loadFxml("/hexchess/HexChessGame.fxml");
        runOnFx(() -> game.controller().setRouteData(new HexChessGameSetup("White", "Bot", HexGameMode.BOT)));

        clickGameCell(game.controller(), "b1");
        clickGameCell(game.controller(), "b3");

        waitUntil("bot move to finish", () -> {
            HexGameState state = state(game.controller());
            return state.turn() == HexPieceColor.WHITE || !state.isActive();
        });

        assertFalse(state(game.controller()).statusMessage().equals("Bot thinking..."));
        assertNotNull(state(game.controller()).lastMove());
    }

    @Test
    void localUiHandlesDrawAcceptanceRestartAndResignation() throws Exception {
        LoadedFxml<HexChessGameController> game = loadFxml("/hexchess/HexChessGame.fxml");

        runOnFx(() -> invoke(game.controller(), "onOfferDraw"));
        assertEquals(HexPieceColor.WHITE, state(game.controller()).drawOfferBy());
        assertTrue(buttonVisible(game.controller(), "acceptDrawButton"));
        assertTrue(buttonVisible(game.controller(), "declineDrawButton"));
        assertTrue(buttonDisabled(game.controller(), "offerDrawButton"));

        runOnFx(() -> invoke(game.controller(), "onAcceptDraw"));
        assertEquals(HexGameStatus.DRAW, state(game.controller()).status());
        assertEquals(0.5, state(game.controller()).whiteScore());
        assertEquals(0.5, state(game.controller()).blackScore());
        assertTrue(buttonDisabled(game.controller(), "offerDrawButton"));
        assertTrue(buttonDisabled(game.controller(), "resignButton"));

        runOnFx(() -> invoke(game.controller(), "onRestart"));
        assertEquals(HexGameStatus.RUNNING, state(game.controller()).status());
        assertEquals(HexPieceColor.WHITE, state(game.controller()).turn());
        assertNull(state(game.controller()).lastMove());
        assertFalse(buttonDisabled(game.controller(), "offerDrawButton"));
        assertFalse(buttonDisabled(game.controller(), "resignButton"));

        runOnFx(() -> invoke(game.controller(), "onResign"));
        assertEquals(HexGameStatus.RESIGNED, state(game.controller()).status());
        assertEquals(0, state(game.controller()).whiteScore());
        assertEquals(1, state(game.controller()).blackScore());
    }

    @Test
    void botUiAutomaticallyDeclinesWhiteDrawOffer() throws Exception {
        LoadedFxml<HexChessGameController> game = loadFxml("/hexchess/HexChessGame.fxml");
        runOnFx(() -> game.controller().setRouteData(new HexChessGameSetup("White", "Bot", HexGameMode.BOT)));

        runOnFx(() -> invoke(game.controller(), "onOfferDraw"));
        assertEquals(HexPieceColor.WHITE, state(game.controller()).drawOfferBy());

        waitUntil("bot draw decline", () -> state(game.controller()).drawOfferBy() == null
                && state(game.controller()).statusMessage().contains("declined"));
    }

    @Test
    void customSetupUiValidatesAndEditsPosition() throws Exception {
        LoadedFxml<HexChessSetupController> setup = loadFxml("/hexchess/HexChessSetup.fxml");

        runOnFx(() -> invoke(setup.controller(), "onClearBoard"));
        assertTrue(startButton(setup.controller()).isDisabled());

        selectPiece(setup.controller(), HexPieceColor.WHITE, HexPieceType.KING);
        clickSetupCell(setup.controller(), "g1", MouseButton.PRIMARY);
        clickSetupCell(setup.controller(), "h1", MouseButton.PRIMARY);
        assertPiece(setup.controller(), "h1", HexPieceColor.WHITE, HexPieceType.KING);
        assertEmpty(setup.controller(), "g1");

        selectPiece(setup.controller(), HexPieceColor.BLACK, HexPieceType.KING);
        clickSetupCell(setup.controller(), "g10", MouseButton.PRIMARY);
        selectPiece(setup.controller(), HexPieceColor.WHITE, HexPieceType.PAWN);
        clickSetupCell(setup.controller(), "b1", MouseButton.PRIMARY);
        runOnFx(() -> comboBox(setup.controller(), "turnChoiceBox", HexPieceColor.class)
                .getSelectionModel()
                .select(HexPieceColor.BLACK));

        assertFalse(startButton(setup.controller()).isDisabled());

        clickSetupCell(setup.controller(), "b1", MouseButton.SECONDARY);
        assertTrue(startButton(setup.controller()).isDisabled());

        runOnFx(() -> invoke(setup.controller(), "onLoadStandard"));
        assertFalse(startButton(setup.controller()).isDisabled());
        assertEquals(HexPieceColor.WHITE, selectedComboValue(setup.controller(), "turnChoiceBox", HexPieceColor.class));

        runOnFx(() -> invoke(setup.controller(), "onClearBoard"));
        assertTrue(labelText(setup.controller(), "validationLabel").contains("exactly one king"));
        runOnFx(() -> invoke(setup.controller(), "onRemovePiece"));
        assertEquals("Select a cell first.", labelText(setup.controller(), "validationLabel"));
    }

    @Test
    void lanDiscoveryRefreshKeepsSelectedHost() throws Exception {
        LoadedFxml<HexChessMenuController> menu = loadFxml("/hexchess/HexChessMenu.fxml");

        try {
            runOnFx(() -> {
                HexChessLanDiscoveryService.DiscoveredGame first = new HexChessLanDiscoveryService.DiscoveredGame(
                        "Host",
                        "HEX_CHESS",
                        "127.0.0.1",
                        54321,
                        "session-1",
                        System.currentTimeMillis());
                invoke(menu.controller(), "rememberDiscoveredGame", first);

                @SuppressWarnings("unchecked")
                ListView<HexChessLanDiscoveryService.DiscoveredGame> lanGamesList =
                        (ListView<HexChessLanDiscoveryService.DiscoveredGame>) field(
                                menu.controller(),
                                "lanGamesList",
                                ListView.class);
                Button joinButton = field(menu.controller(), "joinSelectedLanButton", Button.class);
                lanGamesList.getSelectionModel().selectFirst();

                assertFalse(joinButton.isDisabled());

                HexChessLanDiscoveryService.DiscoveredGame refresh = new HexChessLanDiscoveryService.DiscoveredGame(
                        "Host",
                        "HEX_CHESS",
                        "127.0.0.1",
                        54321,
                        "session-1",
                        System.currentTimeMillis() + 1_000);
                invoke(menu.controller(), "rememberDiscoveredGame", refresh);

                assertNotNull(lanGamesList.getSelectionModel().getSelectedItem());
                assertEquals("session-1", lanGamesList.getSelectionModel().getSelectedItem().sessionId());
                assertFalse(joinButton.isDisabled());
            });
        } finally {
            runOnFx(() -> invoke(menu.controller(), "closeDiscovery"));
        }
    }

    @Test
    void lanMenuValidatesDirectJoinAndRefreshesDiscoveredGames() throws Exception {
        LoadedFxml<HexChessMenuController> menu = loadFxml("/hexchess/HexChessMenu.fxml");

        try {
            runOnFx(() -> {
                invoke(menu.controller(), "onJoinLan", (Object) null);
                assertEquals("Enter the host IP address first.", labelTextNow(menu.controller(), "statusLabel"));

                field(menu.controller(), "lanHostField", TextField.class).setText("127.0.0.1");
                field(menu.controller(), "lanPortField", TextField.class).setText("70000");
                invoke(menu.controller(), "onJoinLan", (Object) null);
                assertEquals("Enter a valid TCP port from 1 to 65535.",
                        labelTextNow(menu.controller(), "statusLabel"));

                HexChessLanDiscoveryService.DiscoveredGame game = new HexChessLanDiscoveryService.DiscoveredGame(
                        "Host",
                        "HEX_CHESS",
                        "127.0.0.1",
                        54321,
                        "session-1",
                        System.currentTimeMillis());
                invoke(menu.controller(), "rememberDiscoveredGame", game);

                @SuppressWarnings("unchecked")
                ListView<HexChessLanDiscoveryService.DiscoveredGame> lanGamesList =
                        (ListView<HexChessLanDiscoveryService.DiscoveredGame>) field(
                                menu.controller(),
                                "lanGamesList",
                                ListView.class);
                assertEquals(1, lanGamesList.getItems().size());

                invoke(menu.controller(), "onRefreshLan");
                assertTrue(lanGamesList.getItems().isEmpty());
                assertEquals("Looking for Hex Chess LAN games...", labelTextNow(menu.controller(), "statusLabel"));
            });
        } finally {
            runOnFx(() -> invoke(menu.controller(), "closeDiscovery"));
        }
    }

    @Test
    void lanHostAndJoinerControllersPlayOverRealLocalhostTcp() throws Exception {
        try (ConnectedHexGame game = connectLanControllers()) {
            HexChessGameController host = game.host().controller();
            HexChessGameController joiner = game.joiner().controller();

            runOnFx(() -> invoke(host, "onRestart"));
            assertEquals("Network restart is not supported. Return to menu to start again.",
                    state(host).statusMessage());

            clickGameCell(host, "b1");
            clickGameCell(host, "b3");
            waitUntil("joiner receives host move", () -> hasLastMove(joiner, "b1-b3"));

            clickGameCell(joiner, "b7");
            clickGameCell(joiner, "b5");
            waitUntil("host receives joiner move", () -> hasLastMove(host, "b7-b5"));
            waitUntil("joiner receives own broadcast move", () -> hasLastMove(joiner, "b7-b5"));

            runOnFx(() -> invoke(host, "onOfferDraw"));
            waitUntil("joiner receives draw offer", () -> state(joiner).drawOfferBy() == HexPieceColor.WHITE);

            runOnFx(() -> invoke(joiner, "onDeclineDraw"));
            waitUntil("host receives draw decline", () -> state(host).drawOfferBy() == null
                    && state(host).statusMessage().contains("declined"));
            waitUntil("joiner receives draw decline", () -> state(joiner).drawOfferBy() == null
                    && state(joiner).statusMessage().contains("declined"));

            runOnFx(() -> invoke(joiner, "onResign"));
            waitUntil("host receives resignation", () -> state(host).status() == HexGameStatus.RESIGNED);
            waitUntil("joiner receives resignation", () -> state(joiner).status() == HexGameStatus.RESIGNED);

            assertEquals(1, state(host).whiteScore());
            assertEquals(0, state(host).blackScore());
        }
    }

    @Test
    void lanDrawAcceptanceSynchronizesOverRealLocalhostTcp() throws Exception {
        try (ConnectedHexGame game = connectLanControllers()) {
            HexChessGameController host = game.host().controller();
            HexChessGameController joiner = game.joiner().controller();

            runOnFx(() -> invoke(host, "onOfferDraw"));
            waitUntil("joiner receives draw offer", () -> state(joiner).drawOfferBy() == HexPieceColor.WHITE);

            runOnFx(() -> invoke(joiner, "onAcceptDraw"));
            waitUntil("host receives accepted draw", () -> state(host).status() == HexGameStatus.DRAW);
            waitUntil("joiner receives accepted draw", () -> state(joiner).status() == HexGameStatus.DRAW);

            assertEquals(0.5, state(host).whiteScore());
            assertEquals(0.5, state(host).blackScore());
        }
    }

    private static ConnectedHexGame connectLanControllers() throws Exception {
        GameServer server = new GameServer();
        GameClient client = new GameClient();
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicReference<Exception> serverError = new AtomicReference<>();

        try {
            server.listen(0, message -> {
            }, () -> {
            });
            Thread acceptThread = new Thread(() -> {
                try {
                    server.waitForClient();
                } catch (Exception e) {
                    serverError.set(e);
                } finally {
                    accepted.countDown();
                }
            }, "hexchess-e2e-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            client.connect("127.0.0.1", server.localPort(), message -> {
            }, () -> {
            });
            assertTrue(accepted.await(5, TimeUnit.SECONDS));
            assertNull(serverError.get());

            LoadedFxml<HexChessGameController> host = loadFxml("/hexchess/HexChessGame.fxml");
            LoadedFxml<HexChessGameController> joiner = loadFxml("/hexchess/HexChessGame.fxml");
            HexChessGameSetup setup = new HexChessGameSetup("Host", "Joiner", HexGameMode.LOCAL);

            runOnFx(() -> host.controller().setRouteData(HexChessGameRouteData.host(setup, server)));
            runOnFx(() -> joiner.controller().setRouteData(HexChessGameRouteData.join(setup, client)));
            waitUntil("joiner receives start state", () -> state(joiner.controller()).turn() == HexPieceColor.WHITE);

            return new ConnectedHexGame(server, client, host, joiner);
        } catch (Exception e) {
            client.close();
            server.close();
            throw e;
        }
    }

    private static void clickGameCell(HexChessGameController controller, String notation) throws Exception {
        runOnFx(() -> invoke(controller, "onCellClicked", HexCoordinate.of(notation)));
    }

    private static void clickSetupCell(
            HexChessSetupController controller,
            String notation,
            MouseButton button) throws Exception {
        runOnFx(() -> invoke(controller, "onCellClicked", HexCoordinate.of(notation), button));
    }

    private static void selectPiece(
            HexChessSetupController controller,
            HexPieceColor color,
            HexPieceType type) throws Exception {
        runOnFx(() -> {
            comboBox(controller, "colorChoiceBox", HexPieceColor.class).getSelectionModel().select(color);
            comboBox(controller, "typeChoiceBox", HexPieceType.class).getSelectionModel().select(type);
        });
    }

    private static void assertLastMove(HexChessGameController controller, String notation) throws Exception {
        assertTrue(hasLastMove(controller, notation));
    }

    private static void assertPiece(
            HexChessSetupController controller,
            String notation,
            HexPieceColor color,
            HexPieceType type) throws Exception {
        assertTrue(setupBoard(controller)
                .pieceAt(HexCoordinate.of(notation))
                .filter(piece -> piece.color() == color)
                .filter(piece -> piece.type() == type)
                .isPresent());
    }

    private static void assertEmpty(HexChessSetupController controller, String notation) throws Exception {
        assertTrue(setupBoard(controller).pieceAt(HexCoordinate.of(notation)).isEmpty());
    }

    private static boolean hasLastMove(HexChessGameController controller, String notation) throws Exception {
        HexGameState state = state(controller);
        return state.lastMove() != null && state.lastMove().move().notation().equals(notation);
    }

    private static HexGameState state(HexChessGameController controller) throws Exception {
        return callOnFx(() -> field(controller, "gameState", HexGameState.class));
    }

    private static Button startButton(HexChessSetupController controller) throws Exception {
        return callOnFx(() -> field(controller, "startButton", Button.class));
    }

    private static HexBoard setupBoard(HexChessSetupController controller) throws Exception {
        return callOnFx(() -> field(controller, "board", HexBoard.class));
    }

    private static boolean buttonVisible(Object controller, String name) throws Exception {
        return callOnFx(() -> field(controller, name, Button.class).isVisible());
    }

    private static boolean buttonDisabled(Object controller, String name) throws Exception {
        return callOnFx(() -> field(controller, name, Button.class).isDisabled());
    }

    private static String labelText(Object controller, String name) throws Exception {
        return callOnFx(() -> labelTextNow(controller, name));
    }

    private static String labelTextNow(Object controller, String name) throws Exception {
        return field(controller, name, Label.class).getText();
    }

    @SuppressWarnings("unchecked")
    private static <T> ComboBox<T> comboBox(Object controller, String name, Class<T> ignored) throws Exception {
        return (ComboBox<T>) field(controller, name, ComboBox.class);
    }

    private static <T> T selectedComboValue(Object controller, String name, Class<T> type) throws Exception {
        return callOnFx(() -> type.cast(comboBox(controller, name, type).getSelectionModel().getSelectedItem()));
    }

    private static void waitUntil(String description, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (callOnFx(condition)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static <T> LoadedFxml<T> loadFxml(String resource) throws Exception {
        return callOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(HexChessEndToEndSmokeTest.class.getResource(resource));
            Parent root = loader.load();
            return new LoadedFxml<>(root, loader.getController());
        });
    }

    private static void runOnFx(ThrowingRunnable action) throws Exception {
        callOnFx(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T callOnFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static void invoke(Object target, String name, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), name, args.length);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String name, int argumentCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == argumentCount) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + name + " with " + argumentCount + " arguments.");
    }

    private record LoadedFxml<T>(Parent root, T controller) {
    }

    private record ConnectedHexGame(
            GameServer server,
            GameClient client,
            LoadedFxml<HexChessGameController> host,
            LoadedFxml<HexChessGameController> joiner) implements AutoCloseable {

        @Override
        public void close() {
            client.close();
            server.close();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
