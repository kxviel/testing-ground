package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetrisFxmlSmokeTest {

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
    void menuFxmlLoads() throws Exception {
        Parent root = loadOnFxThread("/tetris/TetrisMenu.fxml", null);
        assertNotNull(root);
    }

    @Test
    void menuUsesSharedTwoColumnShell() throws Exception {
        Parent root = loadOnFxThread("/tetris/TetrisMenu.fxml", null);

        assertTrue(root instanceof StackPane);
        StackPane menuRoot = (StackPane) root;
        assertEquals(1080, menuRoot.getPrefWidth(), 0.01);
        assertEquals(720, menuRoot.getPrefHeight(), 0.01);
        assertTrue(menuRoot.getChildren().getFirst() instanceof ScrollPane);
        assertTrue(((ScrollPane) menuRoot.getChildren().getFirst()).getContent() instanceof GridPane);
    }

    @Test
    void menuStartsWithThreeRowsAndHiddenOptionsPanel() throws Exception {
        Parent root = loadOnFxThread("/tetris/TetrisMenu.fxml", null);
        GridPane grid = (GridPane) ((ScrollPane) ((StackPane) root).getChildren().getFirst()).getContent();
        VBox modeChoicePane = find(root, "modeChoicePane", VBox.class);
        VBox optionsPanel = find(root, "optionsPanel", VBox.class);

        assertEquals(3, modeChoicePane.getChildren().stream()
                .filter(Button.class::isInstance)
                .count());
        assertFalse(optionsPanel.isVisible());
        assertFalse(optionsPanel.isManaged());
        assertEquals(100, grid.getColumnConstraints().get(0).getPercentWidth(), 0.01);
        assertEquals(0, grid.getColumnConstraints().get(1).getPercentWidth(), 0.01);
    }

    @Test
    void gameFxmlLoadsAndControllerCanStopLoop() throws Exception {
        FXMLLoader[] loaderRef = new FXMLLoader[1];
        Parent root = loadOnFxThread("/tetris/TetrisGame.fxml", loaderRef);
        assertNotNull(root);

        Object controller = loaderRef[0].getController();
        Method stopGameLoop = controller.getClass().getDeclaredMethod("stopGameLoop");
        stopGameLoop.setAccessible(true);

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                stopGameLoop.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private static Parent loadOnFxThread(String resource, FXMLLoader[] loaderRef) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(TetrisFxmlSmokeTest.class.getResource(resource));
                Parent root = loader.load();
                if (loaderRef != null && loaderRef.length > 0) {
                    loaderRef[0] = loader;
                }
                rootRef.set(root);
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }
        return rootRef.get();
    }

    private static <T extends Node> T find(Node node, String id, Class<T> type) {
        if (type.isInstance(node) && id.equals(node.getId())) {
            return type.cast(node);
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            return find(scrollPane.getContent(), id, type);
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .map(child -> find(child, id, type))
                    .filter(found -> found != null)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
