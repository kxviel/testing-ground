package seda_project.control_alt_defeat.gamebox.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import seda_project.control_alt_defeat.gamebox.settings.WindowMode;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettings;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;

class UiAndSettingsRobustnessTest {

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
    void windowSettingsRejectInvalidSizesAndPreserveValidTransitions() {
        assertThrows(NullPointerException.class,
                () -> new WindowSettings(null, 1280, 720));
        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, Double.NaN, 720));
        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, 1280, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, WindowSettings.MIN_WINDOWED_WIDTH - 1, 720));
        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, 1280, WindowSettings.MIN_WINDOWED_HEIGHT - 1));

        WindowSettings defaults = WindowSettings.defaults();
        WindowSettings windowed = defaults.withWindowedSize(800, 600);
        assertEquals(WindowMode.WINDOWED, windowed.mode());
        assertEquals(800, windowed.windowedWidth());
        assertEquals(600, windowed.windowedHeight());
        assertEquals(WindowMode.MAXIMIZED, windowed.withMode(WindowMode.MAXIMIZED).mode());
    }

    @Test
    void safeTextSanitizesFallbacksNamesAndLengthBoundaries() {
        assertEquals("The Old Monk", SafeText.playerName(" \n\t", "The Old Monk"));
        assertEquals("Alice Bob", SafeText.playerName("  Alice\r\nBob  ", "Fallback"));
        assertEquals("abc", SafeText.singleLine("abcdef", "", 3));
        assertEquals("", SafeText.singleLine(null, null, 10));
        assertEquals(SafeText.MAX_PLAYER_NAME_CHARS,
                SafeText.playerName("x".repeat(100), "Fallback").length());
    }

    @Test
    void wholeNumberGuardTruncatesExistingTextAndRejectsInvalidEdits() throws Exception {
        runOnFxThread(() -> {
            TextField field = new TextField("123456");
            UiInputGuards.limitWholeNumber(field, 3);
            assertEquals("123", field.getText());

            field.replaceText(0, field.getLength(), "9a");
            assertEquals("123", field.getText());
            field.replaceText(0, field.getLength(), "45");
            assertEquals("45", field.getText());

            TextField disabled = new TextField("123");
            UiInputGuards.limitWholeNumber(disabled, -1);
            assertEquals("", disabled.getText());
            disabled.replaceText(0, 0, "1");
            assertEquals("", disabled.getText());
        });
    }

    @Test
    void textGuardHandlesPasteAndSelectionReplacementAtTheBoundary() throws Exception {
        runOnFxThread(() -> {
            TextField field = new TextField("abcdef");
            UiInputGuards.limitText(field, 5);
            assertEquals("abcde", field.getText());

            field.selectRange(1, 3);
            field.replaceSelection("12345");
            assertEquals("a12de", field.getText(),
                    "The formatter must account for the selected text before trimming a paste");
            assertEquals(5, field.getText().length());
        });
    }

    @Test
    void visibilityHelpersKeepVisibleAndManagedStateInSync() throws Exception {
        runOnFxThread(() -> {
            Label label = new Label();
            UiVisibility.setVisibleManaged(label, false);
            assertFalse(label.isVisible());
            assertFalse(label.isManaged());

            UiVisibility.setVisibleManaged(label, true);
            assertTrue(label.isVisible());
            assertTrue(label.isManaged());

            Label bound = new Label();
            UiVisibility.bindVisibleWhenTextPresent(bound);
            assertFalse(bound.isVisible());
            assertFalse(bound.isManaged());
            bound.setText("Ready");
            assertTrue(bound.isVisible());
            assertTrue(bound.isManaged());
            bound.setText("   ");
            assertFalse(bound.isVisible());
            assertFalse(bound.isManaged());
        });
    }

    @Test
    void responsiveHelpersRejectInvalidContractsAndClampSidebarWidth() throws Exception {
        runOnFxThread(() -> {
            assertTrue(ResponsiveLayout.isCompact(ResponsiveLayout.COMPACT_BREAKPOINT - 1));
            assertFalse(ResponsiveLayout.isCompact(ResponsiveLayout.COMPACT_BREAKPOINT));
            assertThrows(IllegalArgumentException.class,
                    () -> ResponsiveLayout.bindTwoColumnGrid(null, 50));

            GridPane incomplete = new GridPane();
            incomplete.add(new StackPane(), 0, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> ResponsiveLayout.bindTwoColumnGrid(incomplete, 50));

            GridPane grid = new GridPane();
            StackPane primary = new StackPane();
            StackPane sidebar = new StackPane();
            grid.add(primary, 0, 0);
            grid.add(sidebar, 1, 0);
            grid.resize(1920, 900);
            ResponsiveLayout.bindSidebarGrid(grid, 300, 400);
            assertEquals(400, grid.getColumnConstraints().get(1).getPrefWidth(), 0.001);

            grid.resize(1200, 900);
            assertEquals(300, grid.getColumnConstraints().get(1).getPrefWidth(), 0.001);
        });
    }

    @Test
    void responsiveViewportHandlesInvalidDimensionsAndNonResizableContent() {
        assertEquals(1.0, ResponsiveViewport.fitScale(0, 600));
        assertEquals(1.0, ResponsiveViewport.fitScale(Double.NaN, 600));
        assertEquals(1.0, ResponsiveViewport.fitScale(800, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new ResponsiveViewport(new Group()));
        assertThrows(NullPointerException.class, () -> new ResponsiveViewport(null));
    }

    @Test
    void discoveredGameListUpdatesSessionsPreservesSelectionAndRemovesStaleEntries() throws Exception {
        runOnFxThread(() -> {
            ListView<LanDiscoveryService.DiscoveredGame> list = new ListView<>();
            DiscoveredGameListController controller = new DiscoveredGameListController(list);
            long now = System.currentTimeMillis();
            LanDiscoveryService.DiscoveredGame first = discovered("one", "Alice", now);
            LanDiscoveryService.DiscoveredGame second = discovered("two", "Bob", now);

            controller.upsert(first);
            controller.upsert(second);
            assertEquals(2, list.getItems().size());
            assertEquals("one", controller.selectedGame().sessionId());

            list.getSelectionModel().select(second);
            controller.upsert(discovered("two", "Robert", now + 1));
            assertEquals(2, list.getItems().size());
            assertEquals("two", controller.selectedGame().sessionId());
            assertEquals("Robert", controller.selectedGame().playerName());

            controller.upsert(discovered("stale", "Old", now - 20_000));
            controller.removeStale(5_000);
            assertTrue(list.getItems().stream().noneMatch(game -> game.sessionId().equals("stale")));

            controller.clear();
            assertTrue(list.getItems().isEmpty());
            assertEquals(null, controller.selectedGame());
            controller.stop();
        });
    }

    @Test
    void discoveredGameListCapsUntrustedAdvertisementsAtOneHundred() throws Exception {
        runOnFxThread(() -> {
            ListView<LanDiscoveryService.DiscoveredGame> list = new ListView<>();
            DiscoveredGameListController controller = new DiscoveredGameListController(list);
            long now = System.currentTimeMillis();

            IntStream.range(0, 105).forEach(index -> controller.upsert(
                    discovered("session-" + index, "Player " + index, now + index)));

            assertEquals(100, list.getItems().size());
            assertTrue(list.getItems().stream().noneMatch(game -> game.sessionId().equals("session-0")));
            assertTrue(list.getItems().stream().anyMatch(game -> game.sessionId().equals("session-104")));
            controller.stop();
        });
    }

    private static LanDiscoveryService.DiscoveredGame discovered(
            String sessionId,
            String playerName,
            long timestamp) {
        return new LanDiscoveryService.DiscoveredGame(
                playerName,
                "TETRIS",
                "127.0.0.1",
                54321,
                sessionId,
                timestamp);
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
