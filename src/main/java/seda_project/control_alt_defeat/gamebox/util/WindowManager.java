package seda_project.control_alt_defeat.gamebox.util;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.settings.WindowMode;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettings;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettingsStore;

public final class WindowManager {
    private static final String THEME = "/Theme.css";
    private static final String SIZE_TRACKING_KEY = WindowManager.class.getName() + ".sizeTracking";

    private WindowManager() {
    }

    public static Scene createScene(Parent root) {
        WindowSettings settings = WindowSettingsStore.get();
        if (settings.mode() == WindowMode.WINDOWED) {
            return createScene(root, settings.windowedWidth(), settings.windowedHeight());
        }

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return createScene(root, bounds.getWidth(), bounds.getHeight());
    }

    private static Scene createScene(Parent root, double width, double height) {
        Scene scene = new Scene(new ResponsiveViewport(root), width, height);
        scene.getStylesheets().add(themeUrl());
        return scene;
    }

    private static String themeUrl() {
        var theme = WindowManager.class.getResource(THEME);
        if (theme == null) {
            throw new IllegalStateException("Missing theme resource: " + THEME);
        }
        return theme.toExternalForm();
    }

    public static void setScene(Stage stage, Parent root) {
        SoundManager.installButtonClickSound(root);
        setScene(stage, createScene(root));
    }

    public static void setScene(Stage stage, Scene scene) {
        stage.setResizable(true);
        stage.setMinWidth(WindowSettings.MIN_WINDOWED_WIDTH);
        stage.setMinHeight(WindowSettings.MIN_WINDOWED_HEIGHT);
        installSizeTracking(stage);
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
            case WINDOWED -> applyWindowed(stage, settings.windowedWidth(), settings.windowedHeight());
        }
    }

    private static void applyMaximized(Stage stage) {
        stage.setFullScreen(false);
        stage.setMaximized(true);
    }

    private static void applyWindowed(Stage stage, double width, double height) {
        stage.setFullScreen(false);
        stage.setMaximized(false);
        stage.setWidth(width);
        stage.setHeight(height);
    }

    private static void installSizeTracking(Stage stage) {
        if (Boolean.TRUE.equals(stage.getProperties().get(SIZE_TRACKING_KEY))) {
            return;
        }

        stage.getProperties().put(SIZE_TRACKING_KEY, true);
        stage.maximizedProperty().addListener((observable, wasMaximized, maximized) -> {
            if (!stage.isShowing()) {
                return;
            }
            if (maximized) {
                WindowSettingsStore.rememberMaximized();
            } else {
                rememberWindowedSize(stage);
            }
        });
        stage.widthProperty().addListener((observable, oldWidth, width) -> rememberWindowedSize(stage));
        stage.heightProperty().addListener((observable, oldHeight, height) -> rememberWindowedSize(stage));
    }

    private static void rememberWindowedSize(Stage stage) {
        if (stage.isShowing() && !stage.isMaximized() && !stage.isFullScreen()) {
            WindowSettingsStore.rememberWindowedSize(
                    Math.max(WindowSettings.MIN_WINDOWED_WIDTH, stage.getWidth()),
                    Math.max(WindowSettings.MIN_WINDOWED_HEIGHT, stage.getHeight()));
        }
    }
}
