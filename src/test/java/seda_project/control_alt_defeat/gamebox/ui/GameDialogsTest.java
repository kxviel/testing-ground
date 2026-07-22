package seda_project.control_alt_defeat.gamebox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameDialogsTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(latch::countDown);
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void confirmationUsesTheSharedSkinAndSafeButtonDefaults() throws Exception {
        runOnFxThread(() -> {
            Alert dialog = GameDialogs.createConfirmation(null, "Leave Match", "Leave this match?");

            assertEquals("Leave Match", dialog.getTitle());
            assertNull(dialog.getHeaderText());
            assertTrue(dialog.getDialogPane().getStyleClass().contains("game-dialog"));
            assertTrue(dialog.getDialogPane().getStylesheets().stream()
                    .anyMatch(path -> path.endsWith("/Theme.css")));
            assertTrue(dialog.getDialogPane().getStylesheets().stream()
                    .anyMatch(path -> path.endsWith("/Dialog.css")));
            assertTrue(dialog.getGraphic() instanceof Label);
            assertTrue(dialog.getGraphic().getStyleClass().contains("game-dialog-question-icon"));

            Button yes = (Button) dialog.getDialogPane().lookupButton(ButtonType.YES);
            Button no = (Button) dialog.getDialogPane().lookupButton(ButtonType.NO);
            assertTrue(yes.getStyleClass().contains("game-dialog-primary"));
            assertTrue(no.getStyleClass().contains("game-dialog-secondary"));
            assertFalse(yes.isDefaultButton());
            assertTrue(no.isDefaultButton());
        });
    }

    @Test
    void warningsAndChoiceDialogsShareTheSameVisualSystem() throws Exception {
        runOnFxThread(() -> {
            Alert warning = GameDialogs.createWarning(null, "Connection Lost", "Connection lost.");
            assertTrue(warning.getDialogPane().getStyleClass().contains("game-dialog"));
            assertTrue(warning.getGraphic().getStyleClass().contains("game-dialog-warning-icon"));
            Button ok = (Button) warning.getDialogPane().lookupButton(ButtonType.OK);
            assertTrue(ok.getStyleClass().contains("game-dialog-primary"));

            ChoiceDialog<String> choice = new ChoiceDialog<>("Queen", "Queen", "Rook");
            GameDialogs.style(choice, null);
            assertTrue(choice.getDialogPane().getStyleClass().contains("game-dialog"));
            assertTrue(choice.getDialogPane().getStylesheets().stream()
                    .anyMatch(path -> path.endsWith("/Dialog.css")));
        });
    }

    private static void runOnFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
