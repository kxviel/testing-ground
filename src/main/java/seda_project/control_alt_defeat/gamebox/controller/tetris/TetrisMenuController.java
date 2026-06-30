package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.CustomPieceBuilder;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisLanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class TetrisMenuController implements RouteDataReceiver {

    private enum MenuView {
        MODE_CHOICE,
        LOCAL,
        LAN,
        HOST,
        JOIN
    }

    @FXML
    private VBox modeChoicePane;
    @FXML
    private VBox localPane;
    @FXML
    private VBox lanPane;
    @FXML
    private VBox hostPane;
    @FXML
    private VBox joinPane;
    @FXML
    private VBox optionsPanel;
    @FXML
    private VBox configBox;
    @FXML
    private VBox customEditorBox;
    @FXML
    private GridPane customPieceGrid;
    @FXML
    private ColumnConstraints contentColumn;
    @FXML
    private ColumnConstraints optionsColumn;
    @FXML
    private HBox actionButtonRow;

    @FXML
    private Button localModeButton;
    @FXML
    private Button lanModeButton;
    @FXML
    private Button hostRoleButton;
    @FXML
    private Button joinRoleButton;
    @FXML
    private Button startButton;
    @FXML
    private Button hostStartGameButton;

    @FXML
    private TextField localPlayerOneField;
    @FXML
    private TextField localPlayerTwoField;
    @FXML
    private TextField hostPlayerNameField;
    @FXML
    private TextField joinPlayerNameField;

    @FXML
    private CheckBox standardPieceCheckBox;
    @FXML
    private CheckBox customPieceCheckBox;
    @FXML
    private CheckBox dualPieceCheckBox;
    @FXML
    private CheckBox horizontalModeCheckBox;
    @FXML
    private ComboBox<String> speedChoiceBox;

    @FXML
    private Label hostInfoLabel;
    @FXML
    private Label joinedPlayerLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label customPieceStatusLabel;

    @FXML
    private ListView<TetrisLanDiscoveryService.DiscoveredGame> availableGamesList;

    private static final int CUSTOM_EDITOR_SIZE = 5;
    private static final int LAN_GAME_STALE_MS = 4_000;

    private MenuView currentView = MenuView.MODE_CHOICE;
    private final TetrisLanDiscoveryService udpDiscovery = new TetrisLanDiscoveryService();
    private final Button[][] customPieceButtons = new Button[CUSTOM_EDITOR_SIZE][CUSTOM_EDITOR_SIZE];
    private final boolean[][] customPieceCells = new boolean[CUSTOM_EDITOR_SIZE][CUSTOM_EDITOR_SIZE];
    private final List<PieceShape> customPieces = new ArrayList<>();
    private Timeline staleGameTimer;
    private GameServer hostServer;
    private GameClient joinClient;
    private String hostName;
    private String joinedPlayerName;
    private TetrisGameConfig hostConfig = TetrisGameConfig.defaultConfig();
    private boolean gameStarted;

    @FXML
    public void initialize() {
        buildCustomPieceGrid();
        speedChoiceBox.getItems().setAll("Slow", "Normal", "Fast");
        speedChoiceBox.getSelectionModel().select("Normal");
        availableGamesList.setPlaceholder(new Label("No LAN games found yet."));
        selectView(MenuView.MODE_CHOICE);
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof String message && !message.isBlank()) {
            statusLabel.setText(message);
        }
    }

    @FXML
    private void onLocalMode() {
        selectView(MenuView.LOCAL);
    }

    @FXML
    private void onLanMode() {
        selectView(MenuView.LAN);
    }

    @FXML
    private void onHostRole() {
        udpDiscovery.stopListening();
        selectView(MenuView.HOST);
    }

    @FXML
    private void onJoinRole() {
        selectView(MenuView.JOIN);
        startDiscovery();
    }

    @FXML
    private void onRefreshGames() {
        startDiscovery();
        statusLabel.setText("Searching for LAN games...");
    }

    @FXML
    private void onCustomPieceToggle() {
        updateCustomEditorVisibility();
    }

    @FXML
    private void onSaveCustomPiece() {
        try {
            PieceShape shape = CustomPieceBuilder.build("Custom " + (customPieces.size() + 1), selectedCustomCells());

            if (customPieceExists(shape)) {
                customPieceStatusLabel.setText("Custom piece already saved.");
                return;
            }

            customPieces.add(shape);
            customPieceCheckBox.setSelected(true);
            clearCustomPieceEditor();
            customPieceStatusLabel.setText("Saved custom piece " + customPieces.size() + ".");
        } catch (IllegalArgumentException e) {
            customPieceStatusLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void onClearCustomPiece() {
        clearCustomPieceEditor();
        customPieceStatusLabel.setText("Custom piece cleared.");
    }

    @FXML
    private void onGameSelected(MouseEvent event) {
        if (currentView == MenuView.JOIN && event.getClickCount() == 1) {
            joinGame();
        }
    }

    @FXML
    private void onStart(ActionEvent event) {
        switch (currentView) {
            case MODE_CHOICE -> statusLabel.setText("Choose Local or LAN before starting.");
            case LOCAL -> startLocalGame(event);
            case LAN -> statusLabel.setText("Choose Host or Join before starting a LAN game.");
            case HOST -> startHost();
            case JOIN -> joinGame();
        }
    }

    @FXML
    private void onHostStartGame(ActionEvent event) {
        if (joinedPlayerName == null || joinedPlayerName.isBlank()) {
            statusLabel.setText("Wait for a player to join first.");
            return;
        }

        gameStarted = true;
        hostServer.send(TetrisProtocol.start(hostName, joinedPlayerName, hostConfig));
        GameServer gameServer = hostServer;
        hostServer = null;
        udpDiscovery.close();
        Router.goTo(event,
                "/tetris/TetrisGame.fxml",
                TetrisGameRouteData.host(TetrisGameSetup.host(hostName, joinedPlayerName, hostConfig), gameServer));
    }

    @FXML
    private void onBack(ActionEvent event) {
        if (currentView == MenuView.MODE_CHOICE) {
            closeLan();
            Router.goTo(event, "/GameChoice.fxml", null);
            return;
        }

        selectView(parentView(currentView));
    }

    private void selectView(MenuView view) {
        if (view != MenuView.JOIN) {
            udpDiscovery.stopListening();
            stopStaleGameTimer();
            availableGamesList.getItems().clear();
            closeJoinClient();
        } else {
            availableGamesList.setDisable(false);
            joinPlayerNameField.setDisable(false);
            startStaleGameTimer();
        }
        if (view != MenuView.HOST) {
            udpDiscovery.stopAdvertising();
            closeHostServer();
        } else {
            hostInfoLabel.setText("Not advertising.");
            joinedPlayerLabel.setText("Joined: none");
            hostStartGameButton.setVisible(false);
            hostStartGameButton.setManaged(false);
            hostStartGameButton.setDisable(true);
            hostPlayerNameField.setDisable(false);
        }

        currentView = view;
        showOnly(view);
        updateCustomEditorVisibility();
        setSelected(localModeButton, view == MenuView.LOCAL);
        setSelected(lanModeButton, view == MenuView.LAN || view == MenuView.HOST || view == MenuView.JOIN);
        setSelected(hostRoleButton, view == MenuView.HOST);
        setSelected(joinRoleButton, view == MenuView.JOIN);

        startButton.setDisable(view == MenuView.MODE_CHOICE || view == MenuView.LAN);
        startButton.setText(switch (view) {
            case MODE_CHOICE -> "Start";
            case LOCAL -> "Start Local";
            case LAN -> "Start";
            case HOST -> "Start Host";
            case JOIN -> "Join Game";
        });

        statusLabel.setText(switch (view) {
            case MODE_CHOICE -> "Choose Local or LAN to configure the match.";
            case LOCAL -> "Local preview ready.";
            case LAN -> "Choose whether this player will host or join.";
            case HOST -> "Enter your name and start hosting.";
            case JOIN -> "Searching for LAN games.";
        });
    }

    private void showOnly(MenuView view) {
        boolean optionsShown = view != MenuView.MODE_CHOICE;
        contentColumn.setPercentWidth(optionsShown ? 65 : 100);
        optionsColumn.setPercentWidth(optionsShown ? 35 : 0);
        setShown(optionsPanel, optionsShown);
        setShown(actionButtonRow, optionsShown);

        setShown(modeChoicePane, view == MenuView.MODE_CHOICE);
        setShown(localPane, view == MenuView.LOCAL);
        setShown(lanPane, view == MenuView.LAN);
        setShown(hostPane, view == MenuView.HOST);
        setShown(joinPane, view == MenuView.JOIN);
        setShown(configBox, view == MenuView.LOCAL || view == MenuView.HOST);
    }

    private void buildCustomPieceGrid() {
        customPieceGrid.getChildren().clear();

        for (int row = 0; row < CUSTOM_EDITOR_SIZE; row++) {
            for (int column = 0; column < CUSTOM_EDITOR_SIZE; column++) {
                Button button = new Button();
                button.getStyleClass().add("custom-cell");

                int cellRow = row;
                int cellColumn = column;
                button.setOnAction(event -> toggleCustomCell(cellRow, cellColumn));

                customPieceButtons[row][column] = button;
                customPieceGrid.add(button, column, row);
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
        return customPieces.stream()
                .anyMatch(customPiece -> customPiece.cells().equals(shape.cells()));
    }

    private void clearCustomPieceEditor() {
        for (int row = 0; row < CUSTOM_EDITOR_SIZE; row++) {
            for (int column = 0; column < CUSTOM_EDITOR_SIZE; column++) {
                customPieceCells[row][column] = false;
                updateCustomCellButton(row, column);
            }
        }
    }

    private void updateCustomEditorVisibility() {
        boolean shown = (currentView == MenuView.LOCAL || currentView == MenuView.HOST)
                && customPieceCheckBox.isSelected();

        setShown(customEditorBox, shown);

        if (shown && customPieces.isEmpty()) {
            customPieceStatusLabel.setText("Draw a connected piece and save it.");
        }
    }

    private MenuView parentView(MenuView view) {
        return switch (view) {
            case LOCAL, LAN -> MenuView.MODE_CHOICE;
            case HOST, JOIN -> MenuView.LAN;
            case MODE_CHOICE -> MenuView.MODE_CHOICE;
        };
    }

    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void setSelected(Button button, boolean selected) {
        if (button == null) {
            return;
        }

        if (selected && !button.getStyleClass().contains("btn-selected")) {
            button.getStyleClass().add("btn-selected");
        } else if (!selected) {
            button.getStyleClass().remove("btn-selected");
        }
    }

    private void startLocalGame(ActionEvent event) {
        String playerOne = defaultIfBlank(trimmed(localPlayerOneField), "Player 1");
        String playerTwo = defaultIfBlank(trimmed(localPlayerTwoField), "Player 2");

        TetrisGameConfig config = buildConfig();
        if (config == null) {
            return;
        }

        TetrisGameSetup setup = TetrisGameSetup.local(playerOne, playerTwo, config);
        Router.goTo(event, "/tetris/TetrisGame.fxml", TetrisGameRouteData.local(setup));
    }

    private void startHost() {
        String hostName = trimmed(hostPlayerNameField);

        if (hostName.isEmpty()) {
            statusLabel.setText("Enter your host player name before starting.");
            return;
        }

        this.hostName = hostName;
        joinedPlayerName = null;
        TetrisGameConfig config = buildConfig();
        if (config == null) {
            return;
        }
        hostConfig = config;
        gameStarted = false;

        closeHostServer();
        hostServer = new GameServer();

        try {
            hostServer.listen(GameServer.DEFAULT_PORT, this::onHostMessage,
                    () -> Platform.runLater(this::onHostDisconnect));
        } catch (Exception e) {
            hostServer = null;
            statusLabel.setText("Could not start host: " + e.getMessage());
            return;
        }

        udpDiscovery.startAdvertising(
                hostName,
                GameServer.DEFAULT_PORT,
                error -> Platform.runLater(() -> statusLabel.setText(error)));

        hostInfoLabel.setText("Advertising as " + hostName + ".");
        joinedPlayerLabel.setText("Joined: none");
        hostPlayerNameField.setDisable(true);
        hostStartGameButton.setVisible(true);
        hostStartGameButton.setManaged(true);
        hostStartGameButton.setDisable(true);
        startButton.setText("Host Started");
        startButton.setDisable(true);
        statusLabel.setText("Host is visible on LAN.");

        Thread thread = new Thread(() -> {
            try {
                hostServer.waitForClient();
                Platform.runLater(() -> statusLabel.setText("Player connected. Waiting for name."));
            } catch (Exception e) {
                if (hostServer != null && !gameStarted) {
                    Platform.runLater(() -> statusLabel.setText("Host stopped: " + e.getMessage()));
                }
            }
        }, "tetris-host-wait");

        thread.setDaemon(true);
        thread.start();
    }

    private void joinGame() {
        String playerName = trimmed(joinPlayerNameField);
        TetrisLanDiscoveryService.DiscoveredGame selectedGame = availableGamesList.getSelectionModel()
                .getSelectedItem();

        if (playerName.isEmpty()) {
            statusLabel.setText("Enter your player name before joining.");
            return;
        }
        if (selectedGame == null) {
            statusLabel.setText("Select an available game before joining.");
            return;
        }

        closeJoinClient();
        udpDiscovery.stopListening();
        stopStaleGameTimer();
        joinClient = new GameClient();
        gameStarted = false;

        startButton.setDisable(true);
        availableGamesList.setDisable(true);
        joinPlayerNameField.setDisable(true);
        statusLabel.setText("Joining " + selectedGame.playerName() + "...");

        Thread thread = new Thread(() -> {
            try {
                joinClient.connect(selectedGame.hostAddress(), selectedGame.tcpPort(), this::onJoinMessage,
                        () -> Platform.runLater(this::onJoinDisconnect));
                joinClient.send(TetrisProtocol.join(playerName));

                Platform.runLater(() -> {
                    startButton.setText("Joined");
                    startButton.setDisable(true);
                    availableGamesList.setDisable(true);
                    joinPlayerNameField.setDisable(true);
                    statusLabel.setText("Joined " + selectedGame.playerName() + ". Waiting for host.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    startButton.setDisable(false);
                    availableGamesList.setDisable(false);
                    joinPlayerNameField.setDisable(false);
                    statusLabel.setText("Could not join game: " + e.getMessage());
                });
                closeJoinClient();
            }
        }, "tetris-join");

        thread.setDaemon(true);
        thread.start();
    }

    private void startDiscovery() {
        availableGamesList.getItems().clear();
        udpDiscovery.startListening(
                game -> Platform.runLater(() -> addOrUpdateGame(game)),
                error -> Platform.runLater(() -> statusLabel.setText(error)));
    }

    private void addOrUpdateGame(TetrisLanDiscoveryService.DiscoveredGame game) {
        int existingIndex = IntStream.range(0, availableGamesList.getItems().size())
                .filter(index -> availableGamesList.getItems().get(index).sessionId().equals(game.sessionId()))
                .findFirst()
                .orElse(-1);

        if (existingIndex >= 0) {
            availableGamesList.getItems().set(existingIndex, game);
            return;
        }

        availableGamesList.getItems().add(game);
        if (availableGamesList.getSelectionModel().isEmpty()) {
            availableGamesList.getSelectionModel().selectFirst();
        }
    }

    private void startStaleGameTimer() {
        stopStaleGameTimer();
        staleGameTimer = new Timeline(new KeyFrame(Duration.millis(1_000), event -> removeStaleGames()));
        staleGameTimer.setCycleCount(Animation.INDEFINITE);
        staleGameTimer.play();
    }

    private void stopStaleGameTimer() {
        if (staleGameTimer != null) {
            staleGameTimer.stop();
            staleGameTimer = null;
        }
    }

    private void removeStaleGames() {
        long cutoff = System.currentTimeMillis() - LAN_GAME_STALE_MS;
        availableGamesList.getItems().removeIf(game -> game.timestamp() < cutoff);
    }

    private void onHostMessage(String message) {
        Platform.runLater(() -> {
            if (!TetrisProtocol.isType(message, TetrisProtocol.JOIN)) {
                return;
            }

            List<String> fields = TetrisProtocol.fields(message);
            if (fields.isEmpty()) {
                return;
            }

            joinedPlayerName = fields.get(0).trim();
            joinedPlayerLabel.setText("Joined: " + joinedPlayerName);
            hostStartGameButton.setDisable(false);
            udpDiscovery.stopAdvertising();
            statusLabel.setText(joinedPlayerName + " joined. You can start the game.");
        });
    }

    private void onJoinMessage(String message) {
        Platform.runLater(() -> {
            if (!TetrisProtocol.isType(message, TetrisProtocol.START)) {
                return;
            }

            List<String> fields = TetrisProtocol.fields(message);
            if (fields.size() < 3) {
                return;
            }

            gameStarted = true;
            TetrisGameConfig config = TetrisGameConfig.deserialize(fields.get(2));
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            GameClient gameClient = joinClient;
            joinClient = null;
            udpDiscovery.close();
            Router.goTo(stage,
                    "/tetris/TetrisGame.fxml",
                    TetrisGameRouteData.join(TetrisGameSetup.join(fields.get(0), fields.get(1), config), gameClient));
        });
    }

    private void onHostDisconnect() {
        if (gameStarted) {
            return;
        }

        joinedPlayerName = null;
        joinedPlayerLabel.setText("Joined: none");
        hostStartGameButton.setDisable(true);
        statusLabel.setText("Player left.");
    }

    private void onJoinDisconnect() {
        if (!gameStarted) {
            statusLabel.setText("Host disconnected.");
        }
    }

    private void closeLan() {
        udpDiscovery.close();
        stopStaleGameTimer();
        closeHostServer();
        closeJoinClient();
    }

    private void closeHostServer() {
        if (hostServer != null) {
            hostServer.close();
            hostServer = null;
        }
    }

    private void closeJoinClient() {
        if (joinClient != null) {
            joinClient.close();
            joinClient = null;
        }
    }

    private TetrisGameConfig buildConfig() {
        List<String> pieces = new ArrayList<>();

        if (standardPieceCheckBox.isSelected()) {
            pieces.add("Standard");
        }
        if (customPieceCheckBox.isSelected()) {
            pieces.add("Custom");
        }

        if (customPieceCheckBox.isSelected() && customPieces.isEmpty()) {
            statusLabel.setText("Create and save a custom piece first.");
            return null;
        }

        TetrisGameConfig config = new TetrisGameConfig(
                pieces,
                customPieceCheckBox.isSelected() ? customPieces : List.of(),
                selectedGravityMillis(),
                dualPieceCheckBox.isSelected(),
                horizontalModeCheckBox.isSelected());
        if (!config.hasPieces()) {
            statusLabel.setText("Select at least one piece set.");
            return null;
        }

        return config;
    }

    private int selectedGravityMillis() {
        String speed = speedChoiceBox.getSelectionModel().getSelectedItem();

        return switch (speed == null ? "Normal" : speed) {
            case "Slow" -> 750;
            case "Fast" -> 320;
            default -> TetrisGameConfig.DEFAULT_GRAVITY_MILLIS;
        };
    }

    private String trimmed(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
