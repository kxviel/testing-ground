package seda_project.control_alt_defeat.gamebox.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
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
    void windowSettingsControlsInitializeExclusively() throws Exception {
        runOnFxThread(() -> {
            Parent root = load("/GameChoice.fxml");
            RadioButton maximized = find(root, "maximizedRadio", RadioButton.class);
            RadioButton windowed = find(root, "windowedRadio", RadioButton.class);
            RadioButton fullscreen = find(root, "fullscreenRadio", RadioButton.class);
            RadioButton resolution1280 = find(root, "resolution1280Radio", RadioButton.class);
            RadioButton resolution1920 = find(root, "resolution1920Radio", RadioButton.class);
            VBox resolutionBox = find(root, "resolutionBox", VBox.class);
            Label resolutionHint = find(root, "resolutionHint", Label.class);

            assertSame(maximized.getToggleGroup(), windowed.getToggleGroup());
            assertSame(maximized.getToggleGroup(), fullscreen.getToggleGroup());
            assertSame(resolution1280.getToggleGroup(), resolution1920.getToggleGroup());
            assertTrue(maximized.isSelected());
            assertTrue(resolution1280.isSelected());
            assertTrue(resolutionBox.isDisabled());
            assertTrue(resolutionHint.isVisible());

            windowed.setSelected(true);
            assertFalse(maximized.isSelected());
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
