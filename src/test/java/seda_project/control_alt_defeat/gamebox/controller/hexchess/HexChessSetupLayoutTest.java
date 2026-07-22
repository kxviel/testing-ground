package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveViewport;

class HexChessSetupLayoutTest {

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
    void customPositionLayoutFitsAtMaximizedResolutions() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.runLater(() -> {
            try {
                assertSetupFits(1366, 768);
                assertSetupFits(1920, 1080);
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    private static void assertSetupFits(double width, double height) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                HexChessSetupLayoutTest.class.getResource("/hexchess/HexChessSetup.fxml"));
        Parent root = loader.load();
        ResponsiveViewport viewport = new ResponsiveViewport(root);
        Scene scene = new Scene(viewport, width, height);
        scene.getStylesheets().add(
                HexChessSetupLayoutTest.class.getResource("/Theme.css").toExternalForm());
        viewport.resize(width, height);
        for (int i = 0; i < 4; i++) {
            viewport.applyCss();
            viewport.layout();
        }
        Bounds visibleBounds = viewport.localToScene(viewport.getBoundsInLocal());

        ScrollPane scroll = (ScrollPane) root.lookup(".app-scroll");
        VBox header = (VBox) root.lookup(".hex-setup-header");
        Label title = (Label) root.lookup(".hex-setup-header .title");
        VBox boardEditorPanel = (VBox) loader.getNamespace().get("boardEditorPanel");
        StackPane canvasFrame = (StackPane) loader.getNamespace().get("canvasFrame");
        VBox sidePanel = (VBox) root.lookup(".hex-side-panel");
        HBox actions = sidePanel.getChildren().stream()
                .filter(HBox.class::isInstance)
                .map(HBox.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertNotNull(scroll);
        assertNotNull(header);
        assertNotNull(title);
        assertNotNull(boardEditorPanel);
        assertEquals("Custom Position Builder", title.getText());
        assertTrue(root.lookup(".hex-setup-header .eyebrow") == null,
                "Custom Position heading should not reserve a separate eyebrow line");
        assertTrue(title.getFont().getSize() <= 32.0,
                () -> "Custom Position header is still hero-sized: " + title.getFont());
        assertTrue(header.getHeight() <= 100.0,
                () -> "Custom Position header is too tall: " + header.getHeight());
        assertInside(header, visibleBounds, "Custom Position header at " + width + "x" + height);
        assertInside(boardEditorPanel, visibleBounds,
                "Board Editor container at " + width + "x" + height);
        assertInside(canvasFrame, visibleBounds, "canvas at " + width + "x" + height);
        assertTrue(canvasFrame.getHeight() >= 300.0,
                () -> "board canvas collapsed at " + width + "x" + height
                        + ": height=" + canvasFrame.getHeight());
        assertInside(sidePanel, visibleBounds, "players panel at " + width + "x" + height);
        assertTrue(sidePanel.lookupAll(".separator").stream()
                        .allMatch(separator -> separator.getBoundsInParent().getHeight() >= 8.0),
                () -> "setup sidebar separators need enough vertical breathing room at "
                        + width + "x" + height);
        assertInside(actions, visibleBounds, "Back/Start row at " + width + "x" + height);
        assertTrue(scroll.getVmax() <= 1.0,
                () -> "setup should fit without vertical scrolling at " + width + "x" + height
                        + ": vmax=" + scroll.getVmax());
        Bounds content = scroll.getContent().localToScene(scroll.getContent().getBoundsInLocal());
        Bounds viewportBounds = scroll.localToScene(scroll.getViewportBounds());
        assertTrue(content.getMaxY() <= viewportBounds.getMaxY() + 1.0,
                () -> "setup content needs scrolling at " + width + "x" + height + ": " + content
                        + " viewport=" + viewportBounds);
    }

    private static void assertInside(Node node, Bounds visibleBounds, String message) {
        assertNotNull(node, message);
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinY() >= visibleBounds.getMinY() - 0.5
                        && bounds.getMaxY() <= visibleBounds.getMaxY() + 0.5,
                () -> message + ": bounds=" + bounds
                        + ", visible=" + visibleBounds
                        + ", bottomY=" + bounds.getMaxY());
    }
}
