package seda_project.control_alt_defeat.gamebox.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.settings.WindowMode;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettings;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveLayout;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveViewport;

class ResponsiveFxmlLayoutTest {
    private static final Pattern OUTER_SCROLL_CONTENT = Pattern.compile(
            "<ScrollPane\\b([^>]*)>\\s*<([A-Za-z]+)\\b([^>]*)>", Pattern.DOTALL);
    private static final List<ScreenContract> SCREENS = List.of(
            new ScreenContract("/GameChoice.fxml", 620, false),
            new ScreenContract("/memory/MemoryMenu.fxml", 580, true),
            new ScreenContract("/memory/GameBoard.fxml", 480, true),
            new ScreenContract("/tetris/TetrisMenu.fxml", 580, true),
            new ScreenContract("/tetris/TetrisGame.fxml", 520, true),
            new ScreenContract("/hexchess/HexChessMenu.fxml", 560, true),
            new ScreenContract("/hexchess/HexChessGame.fxml", 480, true),
            new ScreenContract("/hexchess/HexChessSetup.fxml", 580, true));

    private static final int[][] REQUIRED_RESOLUTIONS = {
            {640, 360},
            {800, 600},
            {1024, 768},
            {1280, 720},
            {1600, 900},
            {1920, 1080}
    };

    @Test
    void everyScreenHasAWorkingOuterOverflowBoundary() throws Exception {
        for (ScreenContract screen : SCREENS) {
            String fxml = read(screen.resource());
            Matcher outerScroll = OUTER_SCROLL_CONTENT.matcher(fxml);
            assertTrue(outerScroll.find(), () -> "Missing ScrollPane content: " + screen.resource());

            String scrollAttributes = outerScroll.group(1);
            String contentAttributes = outerScroll.group(3);
            assertEquals(Boolean.toString(screen.fillsViewport()),
                    attribute(scrollAttributes, "fitToHeight"),
                    () -> "Wrong viewport-fill behavior: " + screen.resource());
            assertEquals("true", attribute(scrollAttributes, "fitToWidth"));
            assertEquals("AS_NEEDED", attribute(scrollAttributes, "vbarPolicy"));
            assertEquals(screen.minimumContentHeight(),
                    Double.parseDouble(attribute(contentAttributes, "minHeight")),
                    () -> "Wrong minimum content height: " + screen.resource());
        }
    }

    @Test
    void hexSetupHeaderScrollsWithTheRestOfTheScreen() throws Exception {
        String fxml = read("/hexchess/HexChessSetup.fxml");
        assertFalse(fxml.contains("<top>"));
        assertTrue(fxml.indexOf("POSITION BUILDER") > fxml.indexOf("<ScrollPane"));
        assertTrue(fxml.indexOf("POSITION BUILDER") < fxml.lastIndexOf("</ScrollPane>"));
    }

    @Test
    void requiredResolutionRangeIsSupported() {
        assertEquals(WindowMode.MAXIMIZED, WindowSettings.defaults().mode());
        assertEquals(640, WindowSettings.MIN_WINDOWED_WIDTH);
        assertEquals(360, WindowSettings.MIN_WINDOWED_HEIGHT);
        assertEquals(6, REQUIRED_RESOLUTIONS.length);

        for (int[] resolution : REQUIRED_RESOLUTIONS) {
            assertTrue(resolution[0] >= WindowSettings.MIN_WINDOWED_WIDTH);
            assertTrue(resolution[1] >= WindowSettings.MIN_WINDOWED_HEIGHT);
            assertTrue(resolution[0] <= 1920);
            assertTrue(resolution[1] <= 1080);
        }
    }

    @Test
    void constrainedViewportsScaleTheWholeSceneWithoutDistortion() {
        assertViewport(640.0, 360.0);
        assertViewport(800.0, 600.0);
        assertViewport(1_024.0, 768.0);
        assertViewport(1_920.0, 1_080.0);
    }

    @Test
    void twoPanelLayoutsStackAndReturnToDesktopColumns() {
        GridPane grid = new GridPane();
        grid.setHgap(20.0);
        grid.getColumnConstraints().setAll(percentColumn(60.0), percentColumn(40.0));
        StackPane primary = new StackPane();
        StackPane secondary = new StackPane();
        grid.add(primary, 0, 0);
        grid.add(secondary, 1, 0);

        grid.resize(800.0, 600.0);
        ResponsiveLayout.bindTwoColumnGrid(grid, 60.0);

        assertEquals(0, GridPane.getColumnIndex(secondary));
        assertEquals(1, GridPane.getRowIndex(secondary));
        assertEquals(1, grid.getColumnConstraints().size());
        assertTrue(grid.getStyleClass().contains("responsive-stacked"));

        grid.resize(1_280.0, 720.0);

        assertEquals(1, GridPane.getColumnIndex(secondary));
        assertEquals(0, GridPane.getRowIndex(secondary));
        assertEquals(2, grid.getColumnConstraints().size());
        assertEquals(60.0, grid.getColumnConstraints().getFirst().getPercentWidth());
        assertEquals(40.0, grid.getColumnConstraints().getLast().getPercentWidth());
        assertFalse(grid.getStyleClass().contains("responsive-stacked"));
    }

    private static String read(String resource) throws Exception {
        try (InputStream input = ResponsiveFxmlLayoutTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, () -> "Missing resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String attribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=\\\"([^\\\"]+)\\\"")
                .matcher(attributes);
        if (!matcher.find()) {
            throw new AssertionError("Missing " + name + " in: " + attributes);
        }
        return matcher.group(1);
    }

    private static ColumnConstraints percentColumn(double percentWidth) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(percentWidth);
        return constraints;
    }

    private static void assertViewport(double viewportWidth, double viewportHeight) {
        StackPane content = new StackPane();
        ResponsiveViewport viewport = new ResponsiveViewport(content);
        viewport.resize(viewportWidth, viewportHeight);
        viewport.layout();

        double expectedScale = Math.min(1.0, Math.min(
                viewportWidth / ResponsiveViewport.DESIGN_WIDTH,
                viewportHeight / ResponsiveViewport.DESIGN_HEIGHT));
        assertEquals(expectedScale,
                ResponsiveViewport.fitScale(viewportWidth, viewportHeight),
                0.0001);
        assertEquals(viewportWidth / expectedScale, content.getWidth(), 0.0001);
        assertEquals(viewportHeight / expectedScale, content.getHeight(), 0.0001);
        assertTrue(content.getWidth() >= ResponsiveViewport.DESIGN_WIDTH - 0.0001);
        assertTrue(content.getHeight() >= ResponsiveViewport.DESIGN_HEIGHT - 0.0001);
        assertEquals(viewportWidth, content.getParent().getBoundsInParent().getWidth(), 0.0001);
        assertEquals(viewportHeight, content.getParent().getBoundsInParent().getHeight(), 0.0001);
    }

    private record ScreenContract(String resource, double minimumContentHeight, boolean fillsViewport) {
    }
}
