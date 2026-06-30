package seda_project.control_alt_defeat.gamebox.controller.memory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;

class MemoryFxmlSmokeTest {

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
        assertNotNull(loadOnFxThread("/memory/MemoryMenu.fxml"));
    }

    @Test
    void gameBoardFxmlLoads() throws Exception {
        assertNotNull(loadOnFxThread("/memory/GameBoard.fxml"));
    }

    @Test
    void revealedCardShowsSymbolGraphic() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(MemoryFxmlSmokeTest.class.getResource("/memory/GameBoard.fxml"));
            Parent root = loader.load();
            GameController controller = loader.getController();
            controller.setRouteData(MemoryGameRouteData.local(new BoardVariant(2, 2, "Test Board")));

            Button card = findButton(root, "card_0");
            assertNotNull(card);
            card.fire();

            assertTrue(card.getGraphic() instanceof Label);
            assertTrue(!((Label) card.getGraphic()).getText().isBlank());
        });
    }

    @Test
    void largestBoardBuildsAllCards() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(MemoryFxmlSmokeTest.class.getResource("/memory/GameBoard.fxml"));
            Parent root = loader.load();
            GameController controller = loader.getController();
            controller.setRouteData(MemoryGameRouteData.local(new BoardVariant(1, 45, "Large Board")));

            Button lastCard = findButton(root, "card_44");
            assertNotNull(lastCard);
            assertTrue(lastCard.getPrefWidth() > 0);
        });
    }

    private static Parent loadOnFxThread(String resource) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MemoryFxmlSmokeTest.class.getResource(resource));
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

    private static Button findButton(Node node, String id) {
        if (node instanceof Button button && id.equals(button.getId())) {
            return button;
        }
        if (node instanceof ScrollPane scrollPane) {
            return findButton(scrollPane.getContent(), id);
        }
        if (!(node instanceof Parent parent)) {
            return null;
        }
        return parent.getChildrenUnmodifiable().stream()
                .map(child -> findButton(child, id))
                .filter(button -> button != null)
                .findFirst()
                .orElse(null);
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
