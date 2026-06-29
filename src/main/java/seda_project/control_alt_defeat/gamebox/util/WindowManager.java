package seda_project.control_alt_defeat.gamebox.util;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.settings.WindowResolution;
import seda_project.control_alt_defeat.gamebox.settings.WindowMode;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettings;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettingsStore;

public final class WindowManager {

    private WindowManager() {
    }

    public static Scene createScene(Parent root) {
        WindowSettings settings = WindowSettingsStore.get();
        if (settings.mode() == WindowMode.WINDOWED) {
            return createWindowedScene(root, settings.windowedResolution());
        }

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return new Scene(root, bounds.getWidth(), bounds.getHeight());
    }

    private static Scene createWindowedScene(Parent root, WindowResolution resolution) {
        return new Scene(root, resolution.width(), resolution.height());
    }

    public static void setScene(Stage stage, Parent root) {
        setScene(stage, createScene(root));
    }

    public static void setScene(Stage stage, Scene scene) {
        stage.setScene(scene);
        applyCurrentSettings(stage);
    }

    public static void applyCurrentSettings(Stage stage) {
        if (Platform.isFxApplicationThread()) {
            apply(stage, WindowSettingsStore.get());
            Platform.runLater(() -> apply(stage, WindowSettingsStore.get()));
            return;
        }

        Platform.runLater(() -> {
            apply(stage, WindowSettingsStore.get());
            Platform.runLater(() -> apply(stage, WindowSettingsStore.get()));
        });
    }

    private static void apply(Stage stage, WindowSettings settings) {
        switch (settings.mode()) {
            case MAXIMIZED -> applyMaximized(stage);
            case WINDOWED -> applyWindowed(stage, settings.windowedResolution());
            case FULLSCREEN -> applyFullscreen(stage);
        }
    }

    private static void applyMaximized(Stage stage) {
        stage.setFullScreen(false);
        stage.setMaximized(true);
    }

    private static void applyWindowed(Stage stage, WindowResolution resolution) {
        stage.setFullScreen(false);
        stage.setMaximized(false);
        stage.setWidth(resolution.width());
        stage.setHeight(resolution.height());
        stage.centerOnScreen();
    }

    private static void applyFullscreen(Stage stage) {
        stage.setMaximized(false);
        stage.setFullScreen(true);
    }
}
