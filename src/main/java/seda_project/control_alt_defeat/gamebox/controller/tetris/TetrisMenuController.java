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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
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
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;
import seda_project.control_alt_defeat.gamebox.util.UiVisibility;

import java.io.IOException;
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
    private Button joinSelectedGameButton;

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
    private Label optionsTitleLabel;
    @FXML
    private Label optionsSubtitleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label customPieceStatusLabel;

    @FXML
    private ListView<LanDiscoveryService.DiscoveredGame> availableGamesList;

    private static final int CUSTOM_EDITOR_SIZE = 5;
    private static final int LAN_GAME_STALE_MS = 4_000;
    private static final int CONTENT_COLUMN_PERCENT = 60;
    private static final int SIDEBAR_COLUMN_PERCENT = 40;

    private MenuView currentView = MenuView.MODE_CHOICE;
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
        UiInputGuards.limitPlayerNames(
                localPlayerOneField,
                localPlayerTwoField,
                hostPlayerNameField,
                joinPlayerNameField);
        buildCustomPieceGrid();
        discoveredGameList = new DiscoveredGameListController(availableGamesList);
        speedChoiceBox.getItems().setAll("Slow", "Normal", "Fast");
        speedChoiceBox.getSelectionModel().select("Normal");
        availableGamesList.setPlaceholder(new Label("No LAN games found yet."));
        availableGamesList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldGame, newGame) -> updateJoinSelectedButton());
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
        if (joinClient != null || gameStarted) {
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
        if (currentView == MenuView.JOIN && event.getClickCount() == 2) {
            joinGame();
        }
    }

    @FXML
    private void onJoinSelectedLan(ActionEvent event) {
        joinGame();
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
        if (gameStarted) {
            return;
        }

        if (joinedPlayerName == null || joinedPlayerName.isBlank()) {
            statusLabel.setText("Wait for a player to join first.");
            return;
        }

        GameServer gameServer = hostServer;
        if (gameServer == null || !gameServer.isConnected()) {
            statusLabel.setText("Wait for a player to join first.");
            return;
        }

        if (!beginNavigation()) {
            return;
        }
        gameStarted = true;
        gameServer.send(TetrisProtocol.start(hostName, joinedPlayerName, hostConfig));
        hostServer = null;
        udpDiscovery.close();
        Router.goTo(event,
                "/tetris/TetrisGame.fxml",
                TetrisGameRouteData.host(TetrisGameSetup.host(hostName, joinedPlayerName, hostConfig), gameServer));
    }

    @FXML
    private void onBack(ActionEvent event) {
        if (currentView == MenuView.MODE_CHOICE) {
            if (!beginNavigation()) {
                return;
            }
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
            discoveredGameList.clear();
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
            UiVisibility.setVisibleManaged(hostStartGameButton, false);
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
        updateJoinSelectedButton();
    }

    private void showOnly(MenuView view) {
        contentColumn.setPercentWidth(CONTENT_COLUMN_PERCENT);
        optionsColumn.setPercentWidth(SIDEBAR_COLUMN_PERCENT);
        UiVisibility.setVisibleManaged(optionsPanel, true);
        UiVisibility.setVisibleManaged(actionButtonRow, true);
        updateSidebarTitle(view);

        UiVisibility.setVisibleManaged(modeChoicePane, view == MenuView.MODE_CHOICE || isLanView(view));
        UiVisibility.setVisibleManaged(localPane, view == MenuView.LOCAL);
        UiVisibility.setVisibleManaged(lanPane, view == MenuView.LAN);
        UiVisibility.setVisibleManaged(hostPane, view == MenuView.HOST);
        UiVisibility.setVisibleManaged(joinPane, view == MenuView.JOIN);
        UiVisibility.setVisibleManaged(configBox, view == MenuView.LOCAL || view == MenuView.HOST);
    }

    private void updateSidebarTitle(MenuView view) {
        if (optionsTitleLabel == null || optionsSubtitleLabel == null) {
            return;
        }

        if (view == MenuView.MODE_CHOICE || isLanView(view)) {
            optionsTitleLabel.setText("Network");
            optionsSubtitleLabel.setText("Host or join LAN matches");
        } else {
            optionsTitleLabel.setText("Game Options");
            optionsSubtitleLabel.setText("Pieces, speed, and board rules");
        }
    }

    private boolean isLanView(MenuView view) {
        return view == MenuView.LAN || view == MenuView.HOST || view == MenuView.JOIN;
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

        UiVisibility.setVisibleManaged(customEditorBox, shown);

        if (!shown) {
            customPieceStatusLabel.setText("");
        } else if (customPieces.isEmpty()) {
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
        if (!beginNavigation()) {
            return;
        }

        TetrisGameSetup setup = TetrisGameSetup.local(playerOne, playerTwo, config);
        Router.goTo(event, "/tetris/TetrisGame.fxml", TetrisGameRouteData.local(setup));
    }

    private void startHost() {
        if (hostServer != null || gameStarted) {
            return;
        }

        String hostName = defaultIfBlank(trimmed(hostPlayerNameField), "Player 1");

        this.hostName = hostName;
        joinedPlayerName = null;
        TetrisGameConfig config = buildConfig();
        if (config == null) {
            return;
        }
        hostConfig = config;
        gameStarted = false;

        closeHostServer();
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
        UiVisibility.setVisibleManaged(hostStartGameButton, true);
        hostStartGameButton.setDisable(true);
        startButton.setText("Host Started");
        startButton.setDisable(true);
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
                        statusLabel.setText("Host stopped: " + e.getMessage());
                    }
                });
            }
        }, "tetris-host-wait");

        thread.setDaemon(true);
        thread.start();
    }

    private void joinGame() {
        if (joinClient != null || gameStarted) {
            return;
        }

        String playerName = defaultIfBlank(trimmed(joinPlayerNameField), "Player 2");
        LanDiscoveryService.DiscoveredGame selectedGame = availableGamesList.getSelectionModel()
                .getSelectedItem();

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

        startButton.setDisable(true);
        availableGamesList.setDisable(true);
        updateJoinSelectedButton();
        joinPlayerNameField.setDisable(true);
        statusLabel.setText("Joining " + selectedGame.playerName() + "...");

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

                    startButton.setText("Joined");
                    startButton.setDisable(true);
                    availableGamesList.setDisable(true);
                    updateJoinSelectedButton();
                    joinPlayerNameField.setDisable(true);
                    statusLabel.setText("Joined " + selectedGame.playerName() + ". Waiting for host.");
                });
            } catch (IOException e) {
                client.close();
                Platform.runLater(() -> {
                    if (joinClient != client) {
                        return;
                    }

                    joinClient = null;
                    startButton.setDisable(false);
                    availableGamesList.setDisable(false);
                    updateJoinSelectedButton();
                    joinPlayerNameField.setDisable(false);
                    statusLabel.setText("Could not join game: " + e.getMessage());
                });
            }
        }, "tetris-join");

        thread.setDaemon(true);
        thread.start();
    }

    private void startDiscovery() {
        discoveredGameList.clear();
        udpDiscovery.startListening(
                game -> Platform.runLater(() -> addOrUpdateGame(game)),
                error -> Platform.runLater(() -> statusLabel.setText(error)));
    }

    private void addOrUpdateGame(LanDiscoveryService.DiscoveredGame game) {
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
                    currentView != MenuView.JOIN
                            || availableGamesList.isDisabled()
                            || availableGamesList.getSelectionModel().isEmpty());
        }
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

            joinedPlayerName = defaultIfBlank(fields.get(0), "Player 2");
            if (joinedPlayerName.isBlank()) {
                return;
            }

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
        return SafeText.playerName(value, fallback);
    }

    private boolean beginNavigation() {
        if (navigationPending) {
            return false;
        }
        navigationPending = true;
        return true;
    }
}
