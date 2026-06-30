package seda_project.control_alt_defeat.gamebox.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindowManagerTest {

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
    void createSceneAddsGlobalTheme() throws Exception {
        boolean hasTheme = callOnFxThread(() -> WindowManager.createScene(new StackPane())
                .getStylesheets()
                .stream()
                .anyMatch(path -> path.endsWith("Theme.css")));

        assertTrue(hasTheme);
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
