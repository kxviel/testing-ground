package seda_project.control_alt_defeat.gamebox.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.controller.memory.GameController;
import seda_project.control_alt_defeat.gamebox.controller.memory.MemoryGameRouteData;
import seda_project.control_alt_defeat.gamebox.controller.tetris.TetrisGameController;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessProtocol;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveLayout;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveViewport;
import seda_project.control_alt_defeat.gamebox.util.WindowManager;

class GameChoiceFxmlSmokeTest {
    private static final double[][] RESPONSIVE_VIEWPORTS = {
            {640.0, 360.0},
            {800.0, 600.0},
            {1_024.0, 768.0},
            {1_280.0, 720.0},
            {1_366.0, 768.0},
            {1_600.0, 900.0},
            {1_920.0, 1_080.0}
    };
    private static final List<ResponsiveScreen> RESPONSIVE_SCREENS = List.of(
            new ResponsiveScreen("/GameChoice.fxml",
                    ".game-choice-frame"),
            new ResponsiveScreen("/memory/MemoryMenu.fxml",
                    ".memory-setup-card", ".memory-network-card"),
            new ResponsiveScreen("/memory/GameBoard.fxml",
                    ".memory-board-panel", ".memory-game-sidebar"),
            new ResponsiveScreen("/tetris/TetrisMenu.fxml",
                    ".tetris-setup", ".tetris-options-panel"),
            new ResponsiveScreen("/tetris/TetrisGame.fxml",
                    ".tetris-board-zone", ".tetris-game-info-panel"),
            new ResponsiveScreen("/hexchess/HexChessMenu.fxml",
                    ".hex-setup", ".hex-network-panel"),
            new ResponsiveScreen("/hexchess/HexChessGame.fxml",
                    ".hex-board-zone", ".hex-game-info-panel"),
            new ResponsiveScreen("/hexchess/HexChessSetup.fxml",
                    ".canvas-frame", ".hex-side-panel"));

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
    void gameChoiceFxmlLoads() throws Exception {
        Parent root = loadOnFxThread("/GameChoice.fxml");

        assertNotNull(root);
        assertTrue(root.getStyleClass().contains("game-choice-root"));
    }

    @Test
    void memoryGameBoardFxmlLoads() throws Exception {
        Parent root = loadOnFxThread("/memory/GameBoard.fxml");

        assertNotNull(root);
        assertTrue(root.getStyleClass().contains("memory-game-root"));
    }

    @Test
    void everyMenuAndGameFxmlLoads() throws Exception {
        String[] screens = {
                "/GameChoice.fxml",
                "/memory/MemoryMenu.fxml",
                "/memory/GameBoard.fxml",
                "/tetris/TetrisMenu.fxml",
                "/tetris/TetrisGame.fxml",
                "/hexchess/HexChessMenu.fxml",
                "/hexchess/HexChessGame.fxml",
                "/hexchess/HexChessSetup.fxml"
        };

        for (String screen : screens) {
            assertNotNull(loadOnFxThread(screen), () -> "FXML did not load: " + screen);
        }
    }

    @Test
    void tetrisAndHexChessHostingCanBeCancelled() throws Exception {
        assertHostingCanBeCancelled("/tetris/TetrisMenu.fxml");
        assertHostingCanBeCancelled("/hexchess/HexChessMenu.fxml");
    }

    @Test
    void memoryAndTetrisGameContentFillsViewportAndStartsAtTop() throws Exception {
        assertGameViewport("/memory/GameBoard.fxml", "gameLayout", 800, 600);
        assertGameViewport("/memory/GameBoard.fxml", "gameLayout", 1920, 1080);
        assertGameViewport("/tetris/TetrisGame.fxml", "gameMain", 800, 600);
        assertGameViewport("/tetris/TetrisGame.fxml", "gameMain", 1920, 1080);
    }

    @Test
    void startButtonsStayAlignedAndPlayerIconsUseTheLargerSize() throws Exception {
        assertMenuPresentation("/memory/MemoryMenu.fxml", "memory-action-row", "memory-action-row", 1);
        assertMenuPresentation("/tetris/TetrisMenu.fxml", "btn-primary", "tetris-action-row", 1);
        assertMenuPresentation("/hexchess/HexChessMenu.fxml", "hex-action-row", "hex-action-row", 2);
        assertMenuPresentation("/hexchess/HexChessSetup.fxml", "btn-primary", "btn-primary", 1);
    }

    @Test
    void memoryRestartApplyGuardAndHexLanRestartArePresent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader boardLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/memory/GameBoard.fxml"));
                boardLoader.load();
                assertNotNull(boardLoader.getNamespace().get("memoryRestartButton"));

                FXMLLoader menuLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/memory/MemoryMenu.fxml"));
                menuLoader.load();
                TextField kField = (TextField) menuLoader.getNamespace().get("kField");
                Button applyK = (Button) menuLoader.getNamespace().get("btnApplyK");
                Button localGame = (Button) menuLoader.getNamespace().get("btnLocalGame");
                Label kError = (Label) menuLoader.getNamespace().get("kErrorLabel");
                kField.setText("3");
                localGame.fire();
                assertTrue(kError.getText().contains("Click Apply"));

                kField.selectAll();
                kField.replaceSelection("-1");
                assertEquals("-1", kField.getText());
                applyK.fire();
                assertTrue(kError.getText().contains("between 1 and 45"));

                kField.selectAll();
                kField.replaceSelection("abc");
                assertEquals("abc", kField.getText());
                applyK.fire();
                assertTrue(kError.getText().contains("whole number"));

                FXMLLoader hexLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/hexchess/HexChessGame.fxml"));
                hexLoader.load();
                assertNotNull(hexLoader.getNamespace().get("restartButton"));
                String restartMessage = HexChessProtocol.simple(HexChessProtocol.RESTART);
                assertEquals(HexChessProtocol.RESTART, HexChessProtocol.type(restartMessage));
                assertTrue(HexChessProtocol.fields(restartMessage).isEmpty());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void memoryInstructionReflectsConfiguredGroupSize() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                assertMemoryInstruction(1, "Find every unique card.");
                assertMemoryInstruction(2, "Find all the matching pairs.");
                assertMemoryInstruction(3, "Find all matching groups of 3.");
                assertMemoryInstruction(45, "Find all matching groups of 45.");
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void allFortyFiveMemoryCardFacesLoadThroughJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                GameModel model = new GameModel(new BoardVariant(1, 45, "All Icons"));
                List<String> symbols = model.getCards().stream()
                        .map(Card::getSymbol)
                        .distinct()
                        .toList();
                assertEquals(45, symbols.size());

                for (String symbol : symbols) {
                    var resource = GameChoiceFxmlSmokeTest.class.getResource("/icons/" + symbol);
                    assertNotNull(resource, () -> "Missing card-face resource: " + symbol);
                    Image image = new Image(resource.toExternalForm(), false);
                    assertFalse(image.isError(), () -> "Unreadable PNG resource: " + symbol);
                    assertEquals(50.0, image.getWidth(), () -> "Unexpected icon width: " + symbol);
                    assertEquals(50.0, image.getHeight(), () -> "Unexpected icon height: " + symbol);
                }
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    private static void assertMemoryInstruction(int k, String expected) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                GameChoiceFxmlSmokeTest.class.getResource("/memory/GameBoard.fxml"));
        loader.load();
        GameController controller = loader.getController();
        controller.setRouteData(MemoryGameRouteData.local(
                new BoardVariant(k, 1, "Instruction Test"), "Player One", "Player Two"));
        Label instruction = (Label) loader.getNamespace().get("matchingInstructionLabel");
        assertEquals(expected, instruction.getText());
    }

    private static void assertGameViewport(
            String resource,
            String contentId,
            double width,
            double height) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = null;
            Object controller = null;
            try {
                FXMLLoader loader = new FXMLLoader(GameChoiceFxmlSmokeTest.class.getResource(resource));
                Parent root = loader.load();
                controller = loader.getController();
                stage = new Stage();
                Scene scene = new Scene(root, width, height);
                scene.getStylesheets().add(
                        GameChoiceFxmlSmokeTest.class.getResource("/Theme.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                ScrollPane scrollPane = (ScrollPane) loader.getNamespace().get("gameScrollPane");
                Region content = (Region) loader.getNamespace().get(contentId);
                assertNotNull(scrollPane);
                assertNotNull(content);
                assertEquals(0.0, scrollPane.getVvalue(), 0.001,
                        () -> resource + " did not open at the top");
                assertTrue(content.getLayoutBounds().getHeight()
                                >= scrollPane.getViewportBounds().getHeight() - 1,
                        () -> resource + " did not fill the available viewport");
                assertTrue(content.getLayoutBounds().getWidth()
                                <= scrollPane.getViewportBounds().getWidth() + 2,
                        () -> resource + " overflows horizontally at " + width + "x" + height);

                boolean compact = ResponsiveLayout.isCompact(content.getWidth());
                assertEquals(compact, content.getStyleClass().contains("responsive-stacked"));
                Node secondaryPanel = ((GridPane) content).getChildren().stream()
                        .filter(node -> Integer.valueOf(1).equals(GridPane.getRowIndex(node)))
                        .findFirst()
                        .orElse(null);
                if (compact) {
                    assertNotNull(secondaryPanel,
                            () -> resource + " did not stack its secondary panel");
                }

                String headerSelector = resource.contains("tetris") ? ".status-line" : ".memory-game-title";
                Node header = root.lookup(headerSelector);
                assertNotNull(header);
                if (!compact) {
                    Bounds headerBounds = header.localToScene(header.getBoundsInLocal());
                    assertTrue(headerBounds.getMinY() >= 0 && headerBounds.getMaxY() <= scene.getHeight(),
                            () -> resource + " header is clipped at " + width + "x" + height);
                }
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                if (stage != null) {
                    stage.close();
                }
                stopTetrisGameController(controller);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    private static void assertHostingCanBeCancelled(String resource) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(GameChoiceFxmlSmokeTest.class.getResource(resource));
                Parent root = loader.load();
                Stage stage = new Stage();
                stage.setScene(new Scene(root));

                Button hostButton = (Button) loader.getNamespace().get("hostLanButton");
                Button cancelButton = (Button) loader.getNamespace().get("cancelHostingButton");
                Label statusLabel = (Label) loader.getNamespace().get("statusLabel");
                assertNotNull(hostButton);
                assertNotNull(cancelButton);
                assertNotNull(statusLabel);
                assertTrue(!cancelButton.isVisible() && !cancelButton.isManaged());

                hostButton.fire();
                assertTrue(cancelButton.isVisible() && cancelButton.isManaged());

                cancelButton.fire();
                assertTrue(!cancelButton.isVisible() && !cancelButton.isManaged());
                assertTrue(statusLabel.getText().contains("Hosting cancelled"));
                stage.close();
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    private static void assertMenuPresentation(
            String resource,
            String startLayoutClass,
            String alignedRowClass,
            int expectedStartButtons) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = new FXMLLoader(GameChoiceFxmlSmokeTest.class.getResource(resource)).load();
                Scene scene = new Scene(root);
                scene.getStylesheets().add(
                        GameChoiceFxmlSmokeTest.class.getResource("/Theme.css").toExternalForm());
                root.applyCss();
                root.layout();
                List<Node> nodes = descendants(root);
                var startButtons = nodes.stream()
                        .filter(node -> node.getStyleClass().contains("game-start-button"))
                        .toList();
                assertEquals(expectedStartButtons, startButtons.size(),
                        () -> resource + " has the wrong number of start buttons");
                startButtons.forEach(node -> {
                    assertTrue(node.getStyleClass().contains(startLayoutClass));
                    assertTrue(node.getStyleClass().contains("game-start-button"));
                });

                var alignedGraphics = nodes.stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> button.getStyleClass().contains(alignedRowClass))
                        .filter(button -> button.getGraphic() != null)
                        .map(button -> button.getGraphic().localToScene(button.getGraphic().getBoundsInLocal()).getMinX())
                        .toList();
                if (alignedGraphics.size() > 1) {
                    double firstGraphicX = alignedGraphics.getFirst();
                    alignedGraphics.forEach(graphicX -> assertEquals(firstGraphicX, graphicX, 0.5,
                            () -> resource + " action rows are not horizontally aligned"));
                }

                var playerIcons = nodes.stream()
                        .filter(node -> node.getStyleClass().contains("player-field-icon"))
                        .toList();
                assertEquals(2, playerIcons.size(), () -> resource + " should have two player icons");
                playerIcons.forEach(node -> {
                    ImageView icon = (ImageView) node;
                    assertEquals(27.0, icon.getFitWidth());
                    assertEquals(27.0, icon.getFitHeight());
                });
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void memoryRestartButtonResetsACompletedGame() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            GameServer server = new GameServer();
            try {
                FXMLLoader loader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/memory/GameBoard.fxml"));
                loader.load();
                GameController controller = loader.getController();
                controller.setRouteData(MemoryGameRouteData.host(
                        new BoardVariant(1, 1, "Restart Test"),
                        server,
                        null,
                        "Player One"));

                GridPane cardGrid = (GridPane) loader.getNamespace().get("cardGrid");
                Button originalCard = (Button) cardGrid.getChildren().getFirst();
                assertNotNull(originalCard);
                originalCard.fire();
                assertTrue(originalCard.isDisabled());
                assertEquals("Game over", ((Label) loader.getNamespace().get("gamePhaseLabel")).getText());
                assertEquals("RESULT", ((Label) loader.getNamespace().get("turnSectionLabel")).getText());
                assertTrue(((Label) loader.getNamespace().get("statusLabel"))
                        .getText().startsWith("Game over:"));

                Button restartButton = (Button) loader.getNamespace().get("memoryRestartButton");
                ScrollPane gameScrollPane =
                        (ScrollPane) loader.getNamespace().get("gameScrollPane");
                gameScrollPane.setHvalue(1.0);
                gameScrollPane.setVvalue(1.0);
                restartButton.fire();

                Button restartedCard = (Button) cardGrid.getChildren().getFirst();
                assertNotNull(restartedCard);
                assertNotSame(originalCard, restartedCard);
                assertEquals("?", restartedCard.getText());
                assertFalse(restartedCard.isDisabled());
                assertEquals("Match in progress", ((Label) loader.getNamespace().get("gamePhaseLabel")).getText());
                assertEquals("TURN", ((Label) loader.getNamespace().get("turnSectionLabel")).getText());
                assertEquals("0", ((Label) loader.getNamespace().get("p1ScoreLabel")).getText());
                assertEquals("0", ((Label) loader.getNamespace().get("p2ScoreLabel")).getText());
                assertFalse(((HBox) loader.getNamespace().get("postGameBar")).isVisible());
                assertFalse(restartButton.isDisabled());
                assertEquals(0.0, gameScrollPane.getHvalue());
                assertEquals(0.0, gameScrollPane.getVvalue());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                server.close();
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void everyRoutedSceneUsesTheResponsiveViewport() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Scene scene = WindowManager.createScene(new StackPane());
                assertTrue(scene.getRoot() instanceof ResponsiveViewport);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void everyScreenFitsEveryResponsiveViewport() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                for (ResponsiveScreen screen : RESPONSIVE_SCREENS) {
                    for (double[] viewportSize : RESPONSIVE_VIEWPORTS) {
                        assertResponsiveScreen(screen, viewportSize[0], viewportSize[1]);
                    }
                }
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void dynamicScreenStatesFitTheSmallestViewport() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader tetrisLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/tetris/TetrisMenu.fxml"));
                Parent tetrisMenu = tetrisLoader.load();
                CheckBox customPieceCheckBox =
                        (CheckBox) tetrisLoader.getNamespace().get("customPieceCheckBox");
                customPieceCheckBox.fire();
                assertResponsiveContent(
                        new ResponsiveScreen(
                                "/tetris/TetrisMenu.fxml (custom editor)",
                                ".tetris-setup",
                                ".menu-custom-editor",
                                ".tetris-options-panel"),
                        tetrisMenu,
                        640.0,
                        360.0);

                FXMLLoader memoryLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/memory/GameBoard.fxml"));
                Parent memoryGame = memoryLoader.load();
                GameController memoryController = memoryLoader.getController();
                memoryController.setRouteData(MemoryGameRouteData.local(
                        new BoardVariant(1, BoardVariant.MAX_CARDS, "Largest Board"),
                        "Player One",
                        "Player Two"));
                HBox postGameBar = (HBox) memoryLoader.getNamespace().get("postGameBar");
                postGameBar.setVisible(true);
                postGameBar.setManaged(true);
                ((Label) memoryLoader.getNamespace().get("resultLabel"))
                        .setText("A Thirty-Two-Character Player Wins!");
                assertResponsiveContent(
                        new ResponsiveScreen(
                                "/memory/GameBoard.fxml (largest board)",
                                ".memory-board-panel",
                                ".memory-board-grid",
                                ".memory-game-sidebar"),
                        memoryGame,
                        640.0,
                        360.0);
                StackPane boardGridWrapper =
                        (StackPane) memoryLoader.getNamespace().get("boardGridWrapper");
                GridPane cardGrid = (GridPane) memoryLoader.getNamespace().get("cardGrid");
                assertNodeInsideNode(
                        "/memory/GameBoard.fxml (largest board)",
                        ".memory-board-grid",
                        cardGrid,
                        ".memory-grid-wrap",
                        boardGridWrapper);
                for (Node card : cardGrid.getChildren()) {
                    assertNodeInsideNode(
                            "/memory/GameBoard.fxml (largest board)",
                            card.getId(),
                            card,
                            ".memory-grid-wrap",
                            boardGridWrapper);
                }
                for (Node postGameControl : postGameBar.getChildren()) {
                    assertNodeInsideViewport(
                            "/memory/GameBoard.fxml (post-game controls)",
                            postGameControl.getClass().getSimpleName(),
                            postGameControl,
                            640.0,
                            360.0);
                }
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void generatedBoardsStayInsideTheirResponsivePanels() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            Object tetrisController = null;
            try {
                FXMLLoader tetrisLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/tetris/TetrisGame.fxml"));
                Parent tetrisGame = tetrisLoader.load();
                tetrisController = tetrisLoader.getController();
                assertResponsiveContent(
                        new ResponsiveScreen(
                                "/tetris/TetrisGame.fxml (generated boards)",
                                ".tetris-board-zone",
                                ".tetris-game-info-panel"),
                        tetrisGame,
                        640.0,
                        360.0);
                StackPane tetrisBoardZone =
                        (StackPane) tetrisLoader.getNamespace().get("boardZone");
                assertNodeInsideNode(
                        "/tetris/TetrisGame.fxml",
                        "topBoardGrid",
                        (GridPane) tetrisLoader.getNamespace().get("topBoardGrid"),
                        "boardZone",
                        tetrisBoardZone);
                assertNodeInsideNode(
                        "/tetris/TetrisGame.fxml",
                        "bottomBoardGrid",
                        (GridPane) tetrisLoader.getNamespace().get("bottomBoardGrid"),
                        "boardZone",
                        tetrisBoardZone);

                FXMLLoader hexGameLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/hexchess/HexChessGame.fxml"));
                Parent hexGame = hexGameLoader.load();
                assertResponsiveContent(
                        new ResponsiveScreen(
                                "/hexchess/HexChessGame.fxml (generated board)",
                                ".hex-board-zone",
                                ".hex-game-info-panel"),
                        hexGame,
                        640.0,
                        360.0);
                assertNodeInsideNode(
                        "/hexchess/HexChessGame.fxml",
                        "boardCanvas",
                        (Canvas) hexGameLoader.getNamespace().get("boardCanvas"),
                        "boardZone",
                        (StackPane) hexGameLoader.getNamespace().get("boardZone"));

                FXMLLoader hexSetupLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/hexchess/HexChessSetup.fxml"));
                Parent hexSetup = hexSetupLoader.load();
                assertResponsiveContent(
                        new ResponsiveScreen(
                                "/hexchess/HexChessSetup.fxml (generated board)",
                                ".canvas-frame",
                                ".hex-side-panel"),
                        hexSetup,
                        640.0,
                        360.0);
                assertNodeInsideNode(
                        "/hexchess/HexChessSetup.fxml",
                        "boardCanvas",
                        (Canvas) hexSetupLoader.getNamespace().get("boardCanvas"),
                        "canvasFrame",
                        (StackPane) hexSetupLoader.getNamespace().get("canvasFrame"));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                stopTetrisGameController(tetrisController);
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void responsiveViewportHandlesRepeatedShrinkAndExpandCycles() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent content = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/GameChoice.fxml")).load();
                ResponsiveViewport viewport = new ResponsiveViewport(content);
                new Scene(viewport, 1_920.0, 1_080.0).getStylesheets().add(
                        GameChoiceFxmlSmokeTest.class.getResource("/Theme.css").toExternalForm());

                double[][] resizeCycle = {
                        {1_920.0, 1_080.0},
                        {640.0, 360.0},
                        {1_024.0, 768.0},
                        {800.0, 600.0},
                        {1_600.0, 900.0},
                        {640.0, 360.0},
                        {1_920.0, 1_080.0}
                };
                ResponsiveScreen screen =
                        new ResponsiveScreen("/GameChoice.fxml (resize cycle)", ".game-choice-frame");

                for (double[] size : resizeCycle) {
                    layoutResponsiveViewport(viewport, content, size[0], size[1]);
                    assertFillsViewport(screen.resource(), content, size[0], size[1]);
                    assertOuterScrollBarsHidden(screen.resource(), content, size[0], size[1]);
                    assertNodeInsideViewport(
                            screen.resource(),
                            screen.anchorSelectors()[0],
                            content.lookup(screen.anchorSelectors()[0]),
                            size[0],
                            size[1]);
                    assertCenteredBox(
                            screen.resource(),
                            content.lookup(".game-choice-frame"),
                            content.lookup(".game-choice-main"));
                }

                FXMLLoader hexLoader = new FXMLLoader(
                        GameChoiceFxmlSmokeTest.class.getResource("/hexchess/HexChessGame.fxml"));
                Parent hexGame = hexLoader.load();
                ResponsiveViewport hexViewport = new ResponsiveViewport(hexGame);
                new Scene(hexViewport, 1_920.0, 1_080.0).getStylesheets().add(
                        GameChoiceFxmlSmokeTest.class.getResource("/Theme.css").toExternalForm());
                Canvas boardCanvas = (Canvas) hexLoader.getNamespace().get("boardCanvas");
                StackPane boardZone = (StackPane) hexLoader.getNamespace().get("boardZone");

                for (double[] size : resizeCycle) {
                    layoutResponsiveViewport(hexViewport, hexGame, size[0], size[1]);
                    assertNodeInsideNode(
                            "/hexchess/HexChessGame.fxml (resize cycle)",
                            "boardCanvas",
                            boardCanvas,
                            "boardZone",
                            boardZone);
                    assertNodeInsideViewport(
                            "/hexchess/HexChessGame.fxml (resize cycle)",
                            "boardCanvas",
                            boardCanvas,
                            size[0],
                            size[1]);
                }
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    private static List<Node> descendants(Parent root) {
        List<Node> nodes = new ArrayList<>();
        collectDescendants(root, nodes);
        return nodes;
    }

    private static void collectDescendants(Node node, List<Node> nodes) {
        nodes.add(node);
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectDescendants(child, nodes));
        }
    }

    private static void assertResponsiveScreen(
            ResponsiveScreen screen,
            double width,
            double height) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                GameChoiceFxmlSmokeTest.class.getResource(screen.resource()));
        Parent content = loader.load();
        try {
            assertResponsiveContent(screen, content, width, height);
        } finally {
            stopTetrisGameController(loader.getController());
        }
    }

    private static void assertResponsiveContent(
            ResponsiveScreen screen,
            Parent content,
            double width,
            double height) {
        ResponsiveViewport viewport = new ResponsiveViewport(content);
        Scene scene = new Scene(viewport, width, height);
        scene.getStylesheets().add(
                GameChoiceFxmlSmokeTest.class.getResource("/Theme.css").toExternalForm());

        layoutResponsiveViewport(viewport, content, width, height);

        assertFillsViewport(screen.resource(), content, width, height);
        assertOuterScrollBarsHidden(screen.resource(), content, width, height);
        for (String selector : screen.anchorSelectors()) {
            Node anchor = content.lookup(selector);
            assertNotNull(anchor, () -> screen.resource() + " is missing " + selector);
            assertNodeInsideViewport(screen.resource(), selector, anchor, width, height);
        }
    }

    private static void layoutResponsiveViewport(
            ResponsiveViewport viewport,
            Parent content,
            double width,
            double height) {
        viewport.resize(width, height);
        viewport.applyCss();
        viewport.layout();
        content.applyCss();
        content.layout();
    }

    private static void assertFillsViewport(
            String resource,
            Parent content,
            double width,
            double height) {
        Bounds bounds = content.localToScene(content.getBoundsInLocal());
        assertEquals(0.0, bounds.getMinX(), 1.0,
                () -> resource + " leaves space on the left at " + width + "x" + height);
        assertEquals(0.0, bounds.getMinY(), 1.0,
                () -> resource + " leaves space at the top at " + width + "x" + height);
        assertEquals(width, bounds.getMaxX(), 1.0,
                () -> resource + " does not fill the width at " + width + "x" + height);
        assertEquals(height, bounds.getMaxY(), 1.0,
                () -> resource + " does not fill the height at " + width + "x" + height);
    }

    private static void assertOuterScrollBarsHidden(
            String resource,
            Parent content,
            double width,
            double height) {
        ScrollPane outerScroll = descendants(content).stream()
                .filter(ScrollPane.class::isInstance)
                .map(ScrollPane.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(resource + " has no outer ScrollPane"));

        descendants(outerScroll).stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(scrollBar -> scrollBar.getOrientation() == Orientation.HORIZONTAL)
                .forEach(scrollBar -> assertTrue(!scrollBar.isVisible(),
                        () -> resource + " has horizontal overflow at " + width + "x" + height));
    }

    private static void assertNodeInsideViewport(
            String resource,
            String selector,
            Node node,
            double width,
            double height) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinX() >= -1.0 && bounds.getMaxX() <= width + 1.0,
                () -> resource + " clips " + selector + " horizontally at " + width + "x" + height
                        + ": " + bounds);
        assertTrue(bounds.getMinY() >= -1.0 && bounds.getMaxY() <= height + 1.0,
                () -> resource + " clips " + selector + " vertically at " + width + "x" + height
                        + ": " + bounds);
    }

    private static void assertNodeInsideNode(
            String resource,
            String childName,
            Node child,
            String parentName,
            Node parent) {
        assertNotNull(child, () -> resource + " is missing " + childName);
        assertNotNull(parent, () -> resource + " is missing " + parentName);
        Bounds childBounds = child.localToScene(child.getBoundsInLocal());
        Bounds parentBounds = parent.localToScene(parent.getBoundsInLocal());
        assertTrue(
                childBounds.getMinX() >= parentBounds.getMinX() - 1.0
                        && childBounds.getMaxX() <= parentBounds.getMaxX() + 1.0
                        && childBounds.getMinY() >= parentBounds.getMinY() - 1.0
                        && childBounds.getMaxY() <= parentBounds.getMaxY() + 1.0,
                () -> resource + " places " + childName + " outside " + parentName
                        + ": child=" + childBounds + ", parent=" + parentBounds);
    }

    private static void assertCenteredBox(String resource, Node child, Node parent) {
        assertNotNull(child, () -> resource + " is missing its centered box");
        assertNotNull(parent, () -> resource + " is missing the box container");
        Bounds childBounds = child.localToScene(child.getBoundsInLocal());
        Bounds parentBounds = parent.localToScene(parent.getBoundsInLocal());

        assertTrue(childBounds.getHeight() < parentBounds.getHeight() - 1.0,
                () -> resource + " stretches its centered box vertically");
        assertEquals(
                (parentBounds.getMinX() + parentBounds.getMaxX()) / 2.0,
                (childBounds.getMinX() + childBounds.getMaxX()) / 2.0,
                3.0,
                () -> resource + " does not center its box horizontally");
        assertEquals(
                (parentBounds.getMinY() + parentBounds.getMaxY()) / 2.0,
                (childBounds.getMinY() + childBounds.getMaxY()) / 2.0,
                3.0,
                () -> resource + " does not center its box vertically");
    }

    private static Parent loadOnFxThread(String resource) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            FXMLLoader loader = new FXMLLoader(GameChoiceFxmlSmokeTest.class.getResource(resource));
            try {
                rootRef.set(loader.load());
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                stopTetrisGameController(loader.getController());
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }
        return rootRef.get();
    }

    private static void stopTetrisGameController(Object controller) {
        if (!(controller instanceof TetrisGameController)) {
            return;
        }
        try {
            var stopGameLoop = TetrisGameController.class.getDeclaredMethod("stopGameLoop");
            stopGameLoop.setAccessible(true);
            stopGameLoop.invoke(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not stop the Tetris layout-test game loop.", exception);
        }
    }

    private record ResponsiveScreen(String resource, String... anchorSelectors) {
    }
}
