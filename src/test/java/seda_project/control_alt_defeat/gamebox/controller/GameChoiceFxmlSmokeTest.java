package seda_project.control_alt_defeat.gamebox.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameChoiceFxmlSmokeTest {

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
    void gameChoiceFxmlLoads() throws Exception {
        assertNotNull(loadOnFxThread("/GameChoice.fxml"));
    }

    @Test
    void settingsPanelIsRemovedAndGameChoicesAreCentered() throws Exception {
        runOnFxThread(() -> {
            Parent root = load("/GameChoice.fxml");
            VBox gameChoices = findByStyleClass(root, "game-choices", VBox.class);

            assertNotNull(gameChoices);
            assertEquals(760, gameChoices.getMaxWidth());
            assertFalse(hasStyleClass(root, "settings-panel"));
            assertFalse(hasId(root, "resolutionBox"));
            assertFalse(hasId(root, "maximizedRadio"));
        });
    }

    private static Parent loadOnFxThread(String resource) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(GameChoiceFxmlSmokeTest.class.getResource(resource));
                rootRef.set(loader.load());
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

    private static Parent load(String resource) throws Exception {
        FXMLLoader loader = new FXMLLoader(GameChoiceFxmlSmokeTest.class.getResource(resource));
        return loader.load();
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

    private static <T extends Node> T findByStyleClass(Node node, String styleClass, Class<T> type) {
        if (type.isInstance(node) && node.getStyleClass().contains(styleClass)) {
            return type.cast(node);
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            return findByStyleClass(scrollPane.getContent(), styleClass, type);
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .map(child -> findByStyleClass(child, styleClass, type))
                    .filter(found -> found != null)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static boolean hasStyleClass(Node node, String styleClass) {
        return findByStyleClass(node, styleClass, Node.class) != null;
    }

    private static boolean hasId(Node node, String id) {
        return find(node, id, Node.class) != null;
    }

    private static void runOnFxThread(ThrowingRunnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                task.run();
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
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
