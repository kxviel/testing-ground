package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.CustomPieceBuilder;
import seda_project.control_alt_defeat.gamebox.model.tetris.PieceShape;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisProtocol;
import seda_project.control_alt_defeat.gamebox.util.DiscoveredGameListController;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveLayout;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.SoundManager;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;
import seda_project.control_alt_defeat.gamebox.util.UiVisibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class TetrisMenuController implements RouteDataReceiver {

    private static final int CUSTOM_EDITOR_SIZE = 5;
    private static final int LAN_GAME_STALE_MS = 4_000;
    private static final String PLAYER_ONE = SafeText.PLAYER_ONE_NAME;
    private static final String PLAYER_TWO = SafeText.PLAYER_TWO_NAME;

    @FXML
    private GridPane tetrisMenuMain;
    @FXML
    private Button localModeButton;
    @FXML
    private Button startButton;
    @FXML
    private Button joinSelectedGameButton;
    @FXML
    private Button cancelHostingButton;
    @FXML
    private Button hostLanButton;

    @FXML
    private TextField playerOneNameField;
    @FXML
    private TextField playerTwoNameField;

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
    private Label hostLanTitleLabel;
    @FXML
    private Label hostLanSubtitleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label customPieceStatusLabel;

    @FXML
    private VBox customEditorBox;
    @FXML
    private GridPane customPieceGrid;
    @FXML
    private ListView<LanDiscoveryService.DiscoveredGame> availableGamesList;

    private boolean localSelected;
    private final LanDiscoveryService udpDiscovery = LanDiscoveryService.tetris();
    private final Button[][] customPieceButtons = new Button[CUSTOM_EDITOR_SIZE][CUSTOM_EDITOR_SIZE];
    private final boolean[][] customPieceCells = new boolean[CUSTOM_EDITOR_SIZE][CUSTOM_EDITOR_SIZE];
    private final List<PieceShape> customPieces = new ArrayList<>();
    private DiscoveredGameListController discoveredGameList;
    private volatile GameServer hostServer;
    private volatile GameClient joinClient;
    private String hostName;
    private String joinedPlayerName;
    private TetrisGameConfig hostConfig = TetrisGameConfig.defaultConfig();
    private volatile boolean gameStarted;
    private boolean navigationPending;

    @FXML
    public void initialize() {
        ResponsiveLayout.bindTwoColumnGrid(tetrisMenuMain, 60.0);
        UiInputGuards.limitPlayerNames(playerOneNameField, playerTwoNameField);
        buildCustomPieceGrid();
        discoveredGameList = new DiscoveredGameListController(availableGamesList);
        speedChoiceBox.getItems().setAll("Slow", "Normal", "Fast");
        speedChoiceBox.getSelectionModel().select("Normal");
        availableGamesList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldGame, newGame) -> updateJoinSelectedButton());
        setLocalSelected(false);
        startDiscovery();
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof String message && !message.isBlank()) {
            statusLabel.setText(message);
        }
    }

    @FXML
    private void onLocalMode() {
        setLocalSelected(true);
    }

    @FXML
    private void onRefreshGames() {
        if (lanBusy()) {
            return;
        }

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
            if (customPieces.size() >= TetrisGameConfig.MAX_CUSTOM_PIECES) {
                customPieceStatusLabel.setText("You can save at most " + TetrisGameConfig.MAX_CUSTOM_PIECES + " custom pieces.");
                return;
            }
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
        if (event != null && event.getClickCount() == 2) {
            joinGame();
        }
    }

    @FXML
    private void onJoinSelectedLan() {
        joinGame();
    }

    @FXML
    private void onHostLan(ActionEvent event) {
        if (gameStarted) {
            return;
        }

        if (hostServer == null) {
            startHost();
            return;
        }

        if (joinedPlayerName == null || joinedPlayerName.isBlank()) {
            statusLabel.setText("Waiting for " + PLAYER_TWO + " to join.");
            return;
        }

        startHostGame(event);
    }

    @FXML
    private void onCancelHost() {
        if (hostServer == null) {
            return;
        }

        gameStarted = false;
        joinedPlayerName = null;
        closeHostServer();
        udpDiscovery.stopAdvertising();
        setNetworkControlsDisabled(false);
        UiVisibility.setVisibleManaged(cancelHostingButton, false);
        updateHostLanButton();
        startDiscovery();
        statusLabel.setText("Hosting cancelled.");
    }

    @FXML
    private void onStart(ActionEvent event) {
        if (!localSelected) {
            statusLabel.setText("Choose Local before starting a local match.");
            return;
        }

        startLocalGame(event);
    }

    @FXML
    private void onBack(ActionEvent event) {
        if (localSelected && !lanBusy()) {
            setLocalSelected(false);
            return;
        }

        if (!beginNavigation()) {
            return;
        }
        closeLan();
        Router.goTo(event, "/GameChoice.fxml", null);
    }

    private void setLocalSelected(boolean selected) {
        localSelected = selected;
        setSelected(localModeButton, selected);
        startButton.setDisable(!selected || lanBusy());
        startButton.setText(selected ? "Start Local" : "Start");
        statusLabel.setText(selected
                ? "Local match ready."
                : "Choose Local or use the network panel.");
        updateCustomEditorVisibility();
        updateJoinSelectedButton();
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
        boolean shown = customPieceCheckBox.isSelected();
        UiVisibility.setVisibleManaged(customEditorBox, shown);

        if (!shown) {
            customPieceStatusLabel.setText("");
        } else if (customPieces.isEmpty()) {
            customPieceStatusLabel.setText("Draw a connected piece and save it.");
        }
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
        TetrisGameConfig config = buildConfig();
        if (config == null || !beginNavigation()) {
            return;
        }

        TetrisGameSetup setup = TetrisGameSetup.local(playerOneName(), playerTwoName(), config);
        Router.goTo(event, "/tetris/TetrisGame.fxml", TetrisGameRouteData.local(setup));
    }

    private void startHost() {
        if (lanBusy()) {
            return;
        }

        TetrisGameConfig config = buildConfig();
        if (config == null) {
            return;
        }

        hostName = playerOneName();
        joinedPlayerName = null;
        hostConfig = config;
        gameStarted = false;
        closeHostServer();
        closeJoinClient();
        udpDiscovery.stopListening();
        stopStaleGameTimer();
        discoveredGameList.clear();

        GameServer server = new GameServer();
        try {
            server.listen(GameServer.DEFAULT_PORT,
                    message -> {
                        if (hostServer == server) {
                            onHostMessage(message);
                        }
                    },
                    () -> Platform.runLater(() -> {
                        if (hostServer == server) {
                            onHostDisconnect();
                        }
                    }));
            hostServer = server;
        } catch (IOException e) {
            server.close();
            startDiscovery();
            statusLabel.setText("Could not start host: " + e.getMessage());
            return;
        }

        udpDiscovery.startAdvertising(
                hostName,
                GameServer.DEFAULT_PORT,
                error -> Platform.runLater(() -> statusLabel.setText(error)));

        setNetworkControlsDisabled(true);
        UiVisibility.setVisibleManaged(cancelHostingButton, true);
        updateHostLanButton();
        statusLabel.setText("Host is visible on LAN.");

        Thread thread = new Thread(() -> {
            try {
                server.waitForClient();
                Platform.runLater(() -> {
                    if (hostServer == server && !gameStarted) {
                        statusLabel.setText("Player connected. Waiting for name.");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (hostServer == server && !gameStarted) {
                        hostServer = null;
                        server.close();
                        udpDiscovery.stopAdvertising();
                        setNetworkControlsDisabled(false);
                        UiVisibility.setVisibleManaged(cancelHostingButton, false);
                        updateHostLanButton();
                        startDiscovery();
                        statusLabel.setText("Host stopped: " + e.getMessage());
                    }
                });
            }
        }, "tetris-host-wait");

        thread.setDaemon(true);
        thread.start();
    }

    private void startHostGame(ActionEvent event) {
        if (gameStarted) {
            return;
        }

        GameServer gameServer = hostServer;
        if (gameServer == null || !gameServer.isConnected()) {
            statusLabel.setText("Wait for " + PLAYER_TWO + " to join first.");
            return;
        }

        if (!beginNavigation()) {
            return;
        }
        gameStarted = true;
        gameServer.send(TetrisProtocol.start(hostName, joinedPlayerName, hostConfig));
        hostServer = null;
        UiVisibility.setVisibleManaged(cancelHostingButton, false);
        udpDiscovery.close();
        Router.goTo(event,
                "/tetris/TetrisGame.fxml",
                TetrisGameRouteData.host(TetrisGameSetup.host(hostName, joinedPlayerName, hostConfig), gameServer));
    }

    private void joinGame() {
        if (lanBusy()) {
            return;
        }

        LanDiscoveryService.DiscoveredGame selectedGame = availableGamesList.getSelectionModel().getSelectedItem();
        if (selectedGame == null) {
            statusLabel.setText("Select an available game before joining.");
            return;
        }

        closeJoinClient();
        udpDiscovery.stopListening();
        stopStaleGameTimer();
        GameClient client = new GameClient();
        joinClient = client;
        gameStarted = false;

        setNetworkControlsDisabled(true);
        statusLabel.setText("Joining " + selectedGame.playerName() + "...");
        String playerName = playerTwoName();

        Thread thread = new Thread(() -> {
            try {
                client.connect(selectedGame.hostAddress(),
                        selectedGame.tcpPort(),
                        message -> {
                            if (joinClient == client) {
                                onJoinMessage(message);
                            }
                        },
                        () -> Platform.runLater(() -> {
                            if (joinClient == client) {
                                onJoinDisconnect();
                            }
                        }));
                client.send(TetrisProtocol.join(playerName));

                Platform.runLater(() -> {
                    if (joinClient != client || gameStarted) {
                        return;
                    }

                    statusLabel.setText("Joined " + selectedGame.playerName() + ". Waiting for host.");
                });
            } catch (IOException e) {
                client.close();
                Platform.runLater(() -> {
                    if (joinClient != client) {
                        return;
                    }

                    joinClient = null;
                    setNetworkControlsDisabled(false);
                    startDiscovery();
                    statusLabel.setText("Could not join game: " + e.getMessage());
                });
            }
        }, "tetris-join");

        thread.setDaemon(true);
        thread.start();
    }

    private void startDiscovery() {
        if (lanBusy()) {
            return;
        }

        discoveredGameList.clear();
        udpDiscovery.startListening(
                game -> Platform.runLater(() -> addOrUpdateGame(game)),
                error -> Platform.runLater(() -> statusLabel.setText(error)));
        startStaleGameTimer();
        updateJoinSelectedButton();
    }

    private void addOrUpdateGame(LanDiscoveryService.DiscoveredGame game) {
        discoveredGameList.removeStale(LAN_GAME_STALE_MS);
        discoveredGameList.upsert(game);
        updateJoinSelectedButton();
    }

    private void startStaleGameTimer() {
        discoveredGameList.startStaleTimer(LAN_GAME_STALE_MS);
    }

    private void stopStaleGameTimer() {
        discoveredGameList.stop();
    }

    private void updateJoinSelectedButton() {
        if (joinSelectedGameButton != null && availableGamesList != null) {
            joinSelectedGameButton.setDisable(
                    lanBusy()
                            || availableGamesList.isDisabled()
                            || availableGamesList.getSelectionModel().isEmpty());
        }
    }

    private void updateHostLanButton() {
        if (hostLanTitleLabel == null || hostLanSubtitleLabel == null) {
            return;
        }

        if (hostServer == null) {
            hostLanTitleLabel.setText("Host LAN Game");
            hostLanSubtitleLabel.setText("Make your match discoverable");
            SoundManager.setGameStartButton(hostLanButton, false);
        } else if (joinedPlayerName == null || joinedPlayerName.isBlank()) {
            hostLanTitleLabel.setText("Waiting for " + PLAYER_TWO);
            hostLanSubtitleLabel.setText("Host is visible on LAN");
            SoundManager.setGameStartButton(hostLanButton, false);
        } else {
            hostLanTitleLabel.setText("Start Game");
            hostLanSubtitleLabel.setText(joinedPlayerName + " joined");
            SoundManager.setGameStartButton(hostLanButton, true);
        }
    }

    private void setNetworkControlsDisabled(boolean disabled) {
        availableGamesList.setDisable(disabled);
        setPlayerFieldsDisabled(disabled);
        localModeButton.setDisable(disabled);
        startButton.setDisable(disabled || !localSelected);
        updateJoinSelectedButton();
    }

    private void setPlayerFieldsDisabled(boolean disabled) {
        playerOneNameField.setDisable(disabled);
        playerTwoNameField.setDisable(disabled);
    }

    private void onHostMessage(String message) {
        Platform.runLater(() -> {
            if (!TetrisProtocol.isType(message, TetrisProtocol.JOIN)) {
                return;
            }

            List<String> fields = TetrisProtocol.fields(message);
            if (fields.size() != 1) {
                return;
            }

            joinedPlayerName = defaultIfBlank(fields.getFirst(), PLAYER_TWO);
            if (joinedPlayerName.isBlank()) {
                return;
            }

            udpDiscovery.stopAdvertising();
            updateHostLanButton();
            statusLabel.setText(joinedPlayerName + " joined. Press Start Game.");
        });
    }

    private void onJoinMessage(String message) {
        Platform.runLater(() -> {
            if (!TetrisProtocol.isType(message, TetrisProtocol.START)) {
                return;
            }

            List<String> fields = TetrisProtocol.fields(message);
            if (fields.size() != 3) {
                return;
            }

            GameClient gameClient = joinClient;
            if (gameClient == null) {
                return;
            }

            gameStarted = true;
            TetrisGameConfig config = TetrisGameConfig.deserialize(fields.get(2));
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            joinClient = null;
            udpDiscovery.close();
            if (!beginNavigation()) {
                gameClient.close();
                return;
            }
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
        updateHostLanButton();
        statusLabel.setText("Player left.");
    }

    private void onJoinDisconnect() {
        if (!gameStarted) {
            joinClient = null;
            setNetworkControlsDisabled(false);
            startDiscovery();
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
        GameServer server = hostServer;
        hostServer = null;
        if (server != null) {
            server.close();
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
        return gravityMillisForSpeed(speed);
    }

    static int gravityMillisForSpeed(String speed) {
        return switch (speed == null ? "Normal" : speed) {
            case "Slow" -> 750;
            case "Fast" -> 320;
            default -> TetrisGameConfig.DEFAULT_GRAVITY_MILLIS;
        };
    }

    private String playerOneName() {
        return defaultIfBlank(text(playerOneNameField), PLAYER_ONE);
    }

    private String playerTwoName() {
        return defaultIfBlank(text(playerTwoNameField), PLAYER_TWO);
    }

    private static String text(TextField field) {
        return field == null ? "" : field.getText();
    }

    static String defaultIfBlank(String value, String fallback) {
        return SafeText.playerName(value, fallback);
    }

    private boolean lanBusy() {
        return hostServer != null || joinClient != null || gameStarted;
    }

    private boolean beginNavigation() {
        if (navigationPending) {
            return false;
        }
        navigationPending = true;
        return true;
    }
}
