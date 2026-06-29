package seda_project.control_alt_defeat.gamebox.settings;

import java.util.Objects;

public final class WindowSettingsStore {

    private static WindowSettings settings = WindowSettings.defaults();

    private WindowSettingsStore() {
    }

    public static WindowSettings get() {
        return settings;
    }

    public static WindowSettings set(WindowSettings newSettings) {
        settings = Objects.requireNonNull(newSettings, "newSettings");
        return settings;
    }

    public static WindowSettings setWindowMode(WindowMode mode) {
        return set(settings.withMode(mode));
    }

    public static WindowSettings setWindowedResolution(WindowResolution resolution) {
        return set(settings.withWindowedResolution(resolution));
    }
}
