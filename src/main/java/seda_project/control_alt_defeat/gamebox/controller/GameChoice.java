package seda_project.control_alt_defeat.gamebox.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import seda_project.control_alt_defeat.gamebox.GameBox;
import seda_project.control_alt_defeat.gamebox.controller.memory.MemoryGameRouteData;
import seda_project.control_alt_defeat.gamebox.controller.tetris.TetrisGameRouteData;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.CustomPieceBuilder;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;

public class GameChoice implements RouteDataReceiver {

    private static final String SELECTED_STYLE = "choice-selected";
    private static final int CUSTOM_EDITOR_SIZE = 5;

    @FXML
    private StackPane root;
    @FXML
    private Button memoryGameButton;
    @FXML
    private Button tetrisGameButton;
    @FXML
    private Button hexGameButton;
    @FXML
    private Button localModeButton;
    @FXML
    private Button botModeButton;
    @FXML
    private Button customModeButton;
    @FXML
    private Button primaryLaunchButton;
    @FXML
    private TextField playerOneNameField;
    @FXML
    private TextField playerTwoNameField;
    @FXML
    private Label playerOneRoleLabel;
    @FXML
    private Label playerTwoRoleLabel;
    @FXML
    private Label selectedGameTitleLabel;
    @FXML
    private Label selectedGameSubtitleLabel;
    @FXML
    private Label selectedModeLabel;
    @FXML
    private Label launchTitleLabel;
    @FXML
    private Label launchSubtitleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox modeSection;
    @FXML
    private VBox memorySettingsPane;
    @FXML
    private VBox tetrisSettingsPane;
    @FXML
    private VBox hexSettingsPane;
    @FXML
    private TextField memoryTupleField;
    @FXML
    private ComboBox<BoardVariant> memoryVariantBox;
    @FXML
    private ComboBox<String> tetrisSpeedBox;
    @FXML
    private CheckBox tetrisStandardPiecesCheckBox;
    @FXML
    private CheckBox tetrisCustomPiecesCheckBox;
    @FXML
    private CheckBox tetrisDualBlocksCheckBox;
    @FXML
    private CheckBox tetrisHorizontalCheckBox;
    @FXML
    private VBox tetrisCustomEditorBox;
    @FXML
    private javafx.scene.layout.GridPane tetrisCustomPieceGrid;
    @FXML
    private Label tetrisCustomPieceStatusLabel;

    private GameSelection selectedGame = GameSelection.MEMORY;
    private LaunchMode selectedMode = LaunchMode.LOCAL;
    private final Button[][] customPieceButtons = new Button[CUSTOM_EDITOR_SIZE][CUSTOM_EDITOR_SIZE];
    private final boolean[][] customPieceCells = new boolean[CUSTOM_EDITOR_SIZE][CUSTOM_EDITOR_SIZE];
    private final List<PieceShape> customPieces = new ArrayList<>();
    private boolean navigationPending;

    @Override
    public void setRouteData(Object data) {
        if (data instanceof String message && !message.isBlank()) {
            statusLabel.setText(message);
        } else if (data instanceof HexChessGameSetup setup) {
            selectedGame = GameSelection.HEX_CHESS;
            selectedMode = setup.mode() == HexGameMode.BOT ? LaunchMode.BOT : LaunchMode.LOCAL;
            playerOneNameField.setText(setup.whiteName());
            playerTwoNameField.setText(setup.blackName());
            updateView();
        }
    }

    @FXML
    private void initialize() {
        UiInputGuards.limitPlayerNames(playerOneNameField, playerTwoNameField);
        UiInputGuards.limitWholeNumber(memoryTupleField, 2);
        tetrisSpeedBox.getItems().setAll("Slow", "Normal", "Fast");
        tetrisSpeedBox.getSelectionModel().select("Normal");
        buildTetrisCustomPieceGrid();
        applyMemoryTuple();
        updateTetrisCustomEditorVisibility();
        updateView();
        Platform.runLater(root::requestFocus);
    }

    @FXML
    private void selectMemory() {
        selectGame(GameSelection.MEMORY);
    }

    @FXML
    private void selectTetris() {
        selectGame(GameSelection.TETRIS);
    }

    @FXML
    private void selectHexChess() {
        selectGame(GameSelection.HEX_CHESS);
    }

    @FXML
    private void selectLocalMode() {
        selectMode(LaunchMode.LOCAL);
    }

    @FXML
    private void selectBotMode() {
        selectMode(LaunchMode.BOT);
    }

    @FXML
    private void selectCustomMode() {
        selectMode(LaunchMode.CUSTOM);
    }

    @FXML
    private void onApplyMemoryTuple() {
        applyMemoryTuple();
    }

    @FXML
    private void onTetrisCustomPieceToggle() {
        updateTetrisCustomEditorVisibility();
    }

    @FXML
    private void onSaveTetrisCustomPiece() {
        try {
            PieceShape shape = CustomPieceBuilder.build("Custom " + (customPieces.size() + 1), selectedCustomCells());
            if (customPieceExists(shape)) {
                tetrisCustomPieceStatusLabel.setText("Custom piece already saved.");
                return;
            }

            customPieces.add(shape);
            tetrisCustomPiecesCheckBox.setSelected(true);
            clearCustomPieceEditor();
            tetrisCustomPieceStatusLabel.setText("Saved custom piece " + customPieces.size() + ".");
        } catch (IllegalArgumentException e) {
            tetrisCustomPieceStatusLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void onClearTetrisCustomPiece() {
        clearCustomPieceEditor();
        tetrisCustomPieceStatusLabel.setText("Custom piece cleared.");
    }

    @FXML
    private void launch(ActionEvent event) {
        switch (selectedGame) {
            case MEMORY -> launchMemory(event);
            case TETRIS -> launchTetris(event);
            case HEX_CHESS -> launchHexChess(event);
        }
    }

    @FXML
    private void exit() {
        GameBox.cleanExit();
    }

    private void selectGame(GameSelection game) {
        selectedGame = game;
        if (!selectedGame.supports(selectedMode)) {
            selectedMode = LaunchMode.LOCAL;
        }
        updateView();
    }

    private void selectMode(LaunchMode mode) {
        if (!selectedGame.supports(mode)) {
            return;
        }
        selectedMode = mode;
        updateView();
    }

    private void updateView() {
        selectedGameTitleLabel.setText(selectedGame.title());
        selectedGameSubtitleLabel.setText(selectedGame.description());
        selectedModeLabel.setText(selectedMode.label());

        playerOneRoleLabel.setText(selectedGame == GameSelection.HEX_CHESS ? "White" : "Player 1");
        playerTwoRoleLabel.setText(selectedMode == LaunchMode.BOT ? "Bot" :
                selectedGame == GameSelection.HEX_CHESS ? "Black" : "Player 2");

        setSelected(memoryGameButton, selectedGame == GameSelection.MEMORY);
        setSelected(tetrisGameButton, selectedGame == GameSelection.TETRIS);
        setSelected(hexGameButton, selectedGame == GameSelection.HEX_CHESS);
        setSelected(localModeButton, selectedMode == LaunchMode.LOCAL);
        setSelected(botModeButton, selectedMode == LaunchMode.BOT);
        setSelected(customModeButton, selectedMode == LaunchMode.CUSTOM);

        setVisibleManaged(memorySettingsPane, selectedGame == GameSelection.MEMORY);
        setVisibleManaged(tetrisSettingsPane, selectedGame == GameSelection.TETRIS);
        setVisibleManaged(hexSettingsPane, selectedGame == GameSelection.HEX_CHESS);
        setVisibleManaged(modeSection, selectedGame == GameSelection.HEX_CHESS);
        setVisibleManaged(botModeButton, selectedGame == GameSelection.HEX_CHESS);
        setVisibleManaged(customModeButton, selectedGame == GameSelection.HEX_CHESS);

        playerTwoNameField.setDisable(selectedMode == LaunchMode.BOT);
        updateLaunchCopy();
    }

    private void updateLaunchCopy() {
        String action = switch (selectedMode) {
            case LOCAL -> "Start Local";
            case BOT -> "Start Bot Game";
            case CUSTOM -> "Open Custom Setup";
        };
        primaryLaunchButton.setText(action);
        launchTitleLabel.setText(action);
        launchSubtitleLabel.setText(switch (selectedMode) {
            case LOCAL -> "Start " + selectedGame.title() + " with the settings on this screen.";
            case BOT -> "Play White against the computer.";
            case CUSTOM -> "Build a custom Chexsagon starting position.";
        });
    }

    private void applyMemoryTuple() {
        int k = parseMemoryTuple();
        if (k == -1) {
            memoryVariantBox.getItems().clear();
            statusLabel.setText("Memory tuple size must be between 1 and 45.");
            return;
        }

        List<BoardVariant> variants = BoardVariant.computeVariants(k);
        memoryVariantBox.getItems().setAll(variants);
        if (!variants.isEmpty()) {
            memoryVariantBox.getSelectionModel().selectFirst();
        }
        statusLabel.setText("");
    }

    private int parseMemoryTuple() {
        try {
            int k = Integer.parseInt(memoryTupleField.getText().trim());
            return k >= 1 && k <= BoardVariant.MAX_CARDS ? k : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void launchMemory(ActionEvent event) {
        BoardVariant variant = memoryVariantBox.getSelectionModel().getSelectedItem();
        if (variant == null) {
            statusLabel.setText("Apply a valid Memory tuple size before starting.");
            return;
        }

        goToOnce(event, "/memory/GameBoard.fxml", MemoryGameRouteData.local(
                variant,
                playerOneName(),
                playerTwoName()));
    }

    private void launchTetris(ActionEvent event) {
        TetrisGameConfig config = buildTetrisConfig();
        if (config == null) {
            return;
        }

        TetrisGameSetup setup = TetrisGameSetup.local(playerOneName(), playerTwoName(), config);
        goToOnce(event, "/tetris/TetrisGame.fxml", TetrisGameRouteData.local(setup));
    }

    private TetrisGameConfig buildTetrisConfig() {
        List<String> pieces = new ArrayList<>();
        if (tetrisStandardPiecesCheckBox.isSelected()) {
            pieces.add("Standard");
        }
        if (tetrisCustomPiecesCheckBox.isSelected()) {
            pieces.add("Custom");
        }
        if (tetrisCustomPiecesCheckBox.isSelected() && customPieces.isEmpty()) {
            statusLabel.setText("Create and save a custom Tetris piece first.");
            return null;
        }

        TetrisGameConfig config = new TetrisGameConfig(
                pieces,
                tetrisCustomPiecesCheckBox.isSelected() ? customPieces : List.of(),
                selectedGravityMillis(),
                tetrisDualBlocksCheckBox.isSelected(),
                tetrisHorizontalCheckBox.isSelected());
        if (!config.hasPieces()) {
            statusLabel.setText("Select at least one Tetris piece set.");
            return null;
        }

        statusLabel.setText("");
        return config;
    }

    private int selectedGravityMillis() {
        String speed = tetrisSpeedBox.getSelectionModel().getSelectedItem();
        return switch (speed == null ? "Normal" : speed) {
            case "Slow" -> 750;
            case "Fast" -> 320;
            default -> TetrisGameConfig.DEFAULT_GRAVITY_MILLIS;
        };
    }

    private void launchHexChess(ActionEvent event) {
        if (selectedMode == LaunchMode.CUSTOM) {
            goToOnce(event, "/hexchess/HexChessSetup.fxml", hexSetup(HexGameMode.LOCAL));
            return;
        }

        HexGameMode mode = selectedMode == LaunchMode.BOT ? HexGameMode.BOT : HexGameMode.LOCAL;
        goToOnce(event, "/hexchess/HexChessGame.fxml", hexSetup(mode));
    }

    private HexChessGameSetup hexSetup(HexGameMode mode) {
        return new HexChessGameSetup(
                playerOneName(),
                mode == HexGameMode.BOT ? "Bot" : playerTwoName(),
                mode);
    }

    private String playerOneName() {
        return SafeText.playerName(playerOneNameField.getText(), SafeText.PLAYER_ONE_NAME);
    }

    private String playerTwoName() {
        return SafeText.playerName(playerTwoNameField.getText(), SafeText.PLAYER_TWO_NAME);
    }

    private void buildTetrisCustomPieceGrid() {
        tetrisCustomPieceGrid.getChildren().clear();

        for (int row = 0; row < CUSTOM_EDITOR_SIZE; row++) {
            for (int column = 0; column < CUSTOM_EDITOR_SIZE; column++) {
                Button button = new Button();
                button.getStyleClass().add("custom-cell");

                int cellRow = row;
                int cellColumn = column;
                button.setOnAction(event -> toggleCustomCell(cellRow, cellColumn));

                customPieceButtons[row][column] = button;
                tetrisCustomPieceGrid.add(button, column, row);
            }
        }
    }

    private void toggleCustomCell(int row, int column) {
        customPieceCells[row][column] = !customPieceCells[row][column];
        updateCustomCellButton(row, column);
    }

    private void updateCustomCellButton(int row, int column) {
        Button button = customPieceButtons[row][column];
        button.getStyleClass().remove("custom-cell-selected");
        if (customPieceCells[row][column]) {
            button.getStyleClass().add("custom-cell-selected");
        }
    }

    private List<BoardPosition> selectedCustomCells() {
        return IntStream.range(0, CUSTOM_EDITOR_SIZE)
                .boxed()
                .flatMap(row -> IntStream.range(0, CUSTOM_EDITOR_SIZE)
                        .filter(column -> customPieceCells[row][column])
                        .mapToObj(column -> new BoardPosition(row, column)))
                .toList();
    }

    private boolean customPieceExists(PieceShape shape) {
        return customPieces.stream().anyMatch(customPiece -> customPiece.cells().equals(shape.cells()));
    }

    private void clearCustomPieceEditor() {
        for (int row = 0; row < CUSTOM_EDITOR_SIZE; row++) {
            for (int column = 0; column < CUSTOM_EDITOR_SIZE; column++) {
                customPieceCells[row][column] = false;
                updateCustomCellButton(row, column);
            }
        }
    }

    private void updateTetrisCustomEditorVisibility() {
        boolean visible = tetrisCustomPiecesCheckBox.isSelected();
        setVisibleManaged(tetrisCustomEditorBox, visible);
        tetrisCustomPieceStatusLabel.setText(visible && customPieces.isEmpty()
                ? "Draw a connected piece and save it."
                : "");
    }

    private void goToOnce(ActionEvent event, String route, Object data) {
        if (navigationPending) {
            return;
        }

        navigationPending = true;
        Router.goTo(event, route, data);
    }

    private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void setSelected(Button button, boolean selected) {
        if (selected && !button.getStyleClass().contains(SELECTED_STYLE)) {
            button.getStyleClass().add(SELECTED_STYLE);
        } else if (!selected) {
            button.getStyleClass().remove(SELECTED_STYLE);
        }
    }

    private enum GameSelection {
        MEMORY("Memory Match", "Tuple matching with variable board sizes."),
        TETRIS("Zetris", "Dual-board block stacking with speed options."),
        HEX_CHESS("Chexsagon", "Glinski-style hex chess with bot and setup modes.");

        private final String title;
        private final String description;

        GameSelection(String title, String description) {
            this.title = title;
            this.description = description;
        }

        private String title() {
            return title;
        }

        private String description() {
            return description;
        }

        private boolean supports(LaunchMode mode) {
            return switch (mode) {
                case LOCAL -> true;
                case BOT, CUSTOM -> this == HEX_CHESS;
            };
        }
    }

    private enum LaunchMode {
        LOCAL("Local"),
        BOT("Bot"),
        CUSTOM("Custom");

        private final String label;

        LaunchMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }
}
