package seda_project.control_alt_defeat.gamebox.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SoundManagerTest {

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
    void installsWithoutReplacingButtonActions() throws Exception {
        assertNotNull(SoundManager.class.getResource("/sounds/click.wav"));
        assertNotNull(SoundManager.class.getResource("/sounds/game-start.wav"));

        boolean actionPreserved = callOnFxThread(() -> {
            Button button = new Button("Play");
            EventHandler<ActionEvent> handler = event -> {
            };
            button.setOnAction(handler);

            SoundManager.installButtonClickSound(new StackPane(button));

            return button.getOnAction() == handler;
        });

        assertTrue(actionPreserved);
    }

    @Test
    void marksOnlyConfiguredButtonsForTheGameStartSound() throws Exception {
        boolean correctlyMarked = callOnFxThread(() -> {
            Button button = new Button("Start");

            SoundManager.setGameStartButton(button, true);
            boolean marked = button.getStyleClass().contains("game-start-button");

            SoundManager.setGameStartButton(button, false);
            return marked && !button.getStyleClass().contains("game-start-button");
        });

        assertTrue(correctlyMarked);
    }

    private static <T> T callOnFxThread(Callable<T> task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                resultRef.set(task.call());
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
        return resultRef.get();
    }
}
