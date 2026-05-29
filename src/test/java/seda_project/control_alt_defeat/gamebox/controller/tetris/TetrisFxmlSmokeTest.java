package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
}
