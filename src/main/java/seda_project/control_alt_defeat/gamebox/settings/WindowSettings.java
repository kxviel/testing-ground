package seda_project.control_alt_defeat.gamebox.settings;

import java.util.Objects;

public record WindowSettings(WindowMode mode, double windowedWidth, double windowedHeight) {

    public static final double DEFAULT_WINDOWED_WIDTH = 1280;
    public static final double DEFAULT_WINDOWED_HEIGHT = 720;
    public static final double MIN_WINDOWED_WIDTH = 1000;
    public static final double MIN_WINDOWED_HEIGHT = 680;

    public WindowSettings {
        Objects.requireNonNull(mode, "mode");

        if (!Double.isFinite(windowedWidth) || windowedWidth < MIN_WINDOWED_WIDTH) {
            throw new IllegalArgumentException("windowedWidth must be at least " + MIN_WINDOWED_WIDTH);
        }
        if (!Double.isFinite(windowedHeight) || windowedHeight < MIN_WINDOWED_HEIGHT) {
            throw new IllegalArgumentException("windowedHeight must be at least " + MIN_WINDOWED_HEIGHT);
        }
    }

    public static WindowSettings defaults() {
        return new WindowSettings(WindowMode.MAXIMIZED, DEFAULT_WINDOWED_WIDTH, DEFAULT_WINDOWED_HEIGHT);
    }

    public WindowSettings withMode(WindowMode mode) {
        return new WindowSettings(mode, windowedWidth, windowedHeight);
    }

    public WindowSettings withWindowedSize(double width, double height) {
        return new WindowSettings(WindowMode.WINDOWED, width, height);
    }
}
