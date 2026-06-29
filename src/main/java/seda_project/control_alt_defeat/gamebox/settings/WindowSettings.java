package seda_project.control_alt_defeat.gamebox.settings;

import java.util.List;
import java.util.Objects;

public record WindowSettings(WindowMode mode, WindowResolution windowedResolution) {

    public static final WindowMode DEFAULT_MODE = WindowMode.MAXIMIZED;
    public static final WindowResolution RESOLUTION_800_600 = new WindowResolution(800, 600);
    public static final WindowResolution RESOLUTION_1024_768 = new WindowResolution(1024, 768);
    public static final WindowResolution RESOLUTION_1280_720 = new WindowResolution(1280, 720);
    public static final WindowResolution RESOLUTION_1366_768 = new WindowResolution(1366, 768);
    public static final WindowResolution RESOLUTION_1920_1080 = new WindowResolution(1920, 1080);
    public static final WindowResolution DEFAULT_WINDOWED_RESOLUTION = RESOLUTION_1280_720;

    private static final List<WindowResolution> SUPPORTED_WINDOWED_RESOLUTIONS = List.of(
            RESOLUTION_800_600,
            RESOLUTION_1024_768,
            RESOLUTION_1280_720,
            RESOLUTION_1366_768,
            RESOLUTION_1920_1080);

    public WindowSettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(windowedResolution, "windowedResolution");

        if (!isSupportedWindowedResolution(windowedResolution)) {
            throw new IllegalArgumentException("Unsupported windowed resolution: " + windowedResolution);
        }
    }

    public static WindowSettings defaults() {
        return new WindowSettings(DEFAULT_MODE, DEFAULT_WINDOWED_RESOLUTION);
    }

    public static List<WindowResolution> supportedWindowedResolutions() {
        return SUPPORTED_WINDOWED_RESOLUTIONS;
    }

    public static boolean isSupportedWindowedResolution(WindowResolution resolution) {
        return SUPPORTED_WINDOWED_RESOLUTIONS.contains(resolution);
    }

    public WindowSettings withMode(WindowMode mode) {
        return new WindowSettings(mode, windowedResolution);
    }

    public WindowSettings withWindowedResolution(WindowResolution windowedResolution) {
        return new WindowSettings(mode, windowedResolution);
    }
}
