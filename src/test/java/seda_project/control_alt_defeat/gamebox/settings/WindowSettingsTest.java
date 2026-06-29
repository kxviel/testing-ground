package seda_project.control_alt_defeat.gamebox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class WindowSettingsTest {

    @Test
    void defaultsUseMaximizedMode() {
        WindowSettings settings = WindowSettings.defaults();

        assertEquals(WindowMode.MAXIMIZED, settings.mode());
        assertEquals(WindowSettings.RESOLUTION_1280_720, settings.windowedResolution());
    }

    @Test
    void supportedWindowedResolutionsMatchSettingsOptions() {
        assertEquals(List.of(
                new WindowResolution(800, 600),
                new WindowResolution(1024, 768),
                new WindowResolution(1280, 720),
                new WindowResolution(1366, 768),
                new WindowResolution(1920, 1080)),
                WindowSettings.supportedWindowedResolutions());
    }

    @Test
    void rejectsUnsupportedWindowedResolution() {
        WindowResolution unsupportedResolution = new WindowResolution(1440, 900);

        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, unsupportedResolution));
    }

    @Test
    void rejectsInvalidResolutionDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new WindowResolution(0, 600));
        assertThrows(IllegalArgumentException.class, () -> new WindowResolution(800, -1));
    }

    @Test
    void updatesModeAndResolutionIndependently() {
        WindowSettings settings = WindowSettings.defaults()
                .withMode(WindowMode.WINDOWED)
                .withWindowedResolution(WindowSettings.RESOLUTION_1920_1080);

        assertEquals(WindowMode.WINDOWED, settings.mode());
        assertEquals(WindowSettings.RESOLUTION_1920_1080, settings.windowedResolution());
    }
}
