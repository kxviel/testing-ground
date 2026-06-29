package seda_project.control_alt_defeat.gamebox.controller;

import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.GameBox;
import seda_project.control_alt_defeat.gamebox.settings.WindowMode;
import seda_project.control_alt_defeat.gamebox.settings.WindowResolution;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettings;
import seda_project.control_alt_defeat.gamebox.settings.WindowSettingsStore;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.WindowManager;

public class GameChoice {

    @FXML
    private StackPane root;
    @FXML
    private RadioButton maximizedRadio;
    @FXML
    private RadioButton windowedRadio;
    @FXML
    private RadioButton fullscreenRadio;
    @FXML
    private VBox resolutionBox;
    @FXML
    private Label resolutionHint;
    @FXML
    private RadioButton resolution800Radio;
    @FXML
    private RadioButton resolution1024Radio;
    @FXML
    private RadioButton resolution1280Radio;
    @FXML
    private RadioButton resolution1366Radio;
    @FXML
    private RadioButton resolution1920Radio;

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleGroup resolutionGroup = new ToggleGroup();

    private Map<RadioButton, WindowMode> modes;
    private Map<RadioButton, WindowResolution> resolutions;

    @FXML
    private void initialize() {
        modes = Map.of(
                maximizedRadio, WindowMode.MAXIMIZED,
                windowedRadio, WindowMode.WINDOWED,
                fullscreenRadio, WindowMode.FULLSCREEN);
        resolutions = Map.of(
                resolution800Radio, WindowSettings.RESOLUTION_800_600,
                resolution1024Radio, WindowSettings.RESOLUTION_1024_768,
                resolution1280Radio, WindowSettings.RESOLUTION_1280_720,
                resolution1366Radio, WindowSettings.RESOLUTION_1366_768,
                resolution1920Radio, WindowSettings.RESOLUTION_1920_1080);

        modes.keySet().forEach(radio -> radio.setToggleGroup(modeGroup));
        resolutions.keySet().forEach(radio -> radio.setToggleGroup(resolutionGroup));
        showSettings(WindowSettingsStore.get());
        Platform.runLater(root::requestFocus);
    }

    @FXML
    private void playMemory(ActionEvent event) {
        Router.goTo(event, "/memory/MemoryMenu.fxml", null);
    }

    @FXML
    private void playTetris(ActionEvent event) {
        Router.goTo(event, "/tetris/TetrisMenu.fxml", null);
    }

    @FXML
    private void playHexChess(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessMenu.fxml", null);
    }

    @FXML
    private void exit() {
        GameBox.cleanExit();
    }

    @FXML
    private void onWindowModeChanged() {
        selectedModeRadio().ifPresent(radio -> applySettings(WindowSettingsStore.setWindowMode(modes.get(radio))));
    }

    @FXML
    private void onResolutionChanged() {
        selectedResolutionRadio().ifPresent(radio -> applySettings(
                WindowSettingsStore.setWindowedResolution(resolutions.get(radio))));
    }

    private Optional<RadioButton> selectedModeRadio() {
        return modes.keySet().stream()
                .filter(RadioButton::isSelected)
                .findFirst();
    }

    private Optional<RadioButton> selectedResolutionRadio() {
        return resolutions.keySet().stream()
                .filter(RadioButton::isSelected)
                .findFirst();
    }

    private void applySettings(WindowSettings settings) {
        showSettings(settings);
        currentStage().ifPresent(WindowManager::applyCurrentSettings);
    }

    private void showSettings(WindowSettings settings) {
        modes.forEach((radio, mode) -> radio.setSelected(settings.mode() == mode));
        resolutions.forEach((radio, resolution) -> radio.setSelected(settings.windowedResolution().equals(resolution)));

        boolean windowed = settings.mode() == WindowMode.WINDOWED;
        resolutionBox.setDisable(!windowed);
        resolutionBox.setOpacity(windowed ? 1.0 : 0.42);
        resolutionHint.setVisible(!windowed);
        resolutionHint.setManaged(!windowed);
    }

    private Optional<Stage> currentStage() {
        if (root.getScene() == null) {
            return Optional.empty();
        }
        return Optional.of((Stage) root.getScene().getWindow());
    }
}
