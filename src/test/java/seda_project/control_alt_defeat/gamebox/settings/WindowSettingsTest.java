package seda_project.control_alt_defeat.gamebox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WindowSettingsTest {

    @Test
    void defaultsUseMaximizedModeAndDefaultWindowedSize() {
        WindowSettings settings = WindowSettings.defaults();

        assertEquals(WindowMode.MAXIMIZED, settings.mode());
        assertEquals(WindowSettings.DEFAULT_WINDOWED_WIDTH, settings.windowedWidth());
        assertEquals(WindowSettings.DEFAULT_WINDOWED_HEIGHT, settings.windowedHeight());
    }

    @Test
    void rejectsInvalidWindowedDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, WindowSettings.MIN_WINDOWED_WIDTH - 1, 720));
        assertThrows(IllegalArgumentException.class,
                () -> new WindowSettings(WindowMode.WINDOWED, 800, WindowSettings.MIN_WINDOWED_HEIGHT - 1));
    }

    @Test
    void remembersManualWindowedSize() {
        WindowSettings settings = WindowSettings.defaults()
                .withWindowedSize(1440, 900);

        assertEquals(WindowMode.WINDOWED, settings.mode());
        assertEquals(1440, settings.windowedWidth());
        assertEquals(900, settings.windowedHeight());
    }
}
