package seda_project.control_alt_defeat.gamebox.controller.tetris;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.tetris.TetrisLanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.ArrayList;
import java.util.List;

public class TetrisMenuController {

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
    private VBox configBox;

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
    private CheckBox extendedPieceCheckBox;
    @FXML
    private CheckBox customPieceCheckBox;

    @FXML
    private Label hostInfoLabel;
    @FXML
    private Label joinedPlayerLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private ListView<TetrisLanDiscoveryService.DiscoveredGame> availableGamesList;

    private MenuView currentView = MenuView.MODE_CHOICE;
    private final TetrisLanDiscoveryService udpDiscovery = new TetrisLanDiscoveryService();
    private GameServer hostServer;
    private GameClient joinClient;
    private String hostName;
    private String joinedPlayerName;
    private TetrisGameConfig hostConfig = TetrisGameConfig.defaultConfig();
    private boolean gameStarted;

    private static final String JOIN = "JOIN";
    private static final String START = "START";

    @FXML
    public void initialize() {
        availableGamesList.setPlaceholder(new Label("No LAN games found yet."));
        selectView(MenuView.MODE_CHOICE);
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
        hostServer.send(START + ":" + hostName + ":" + joinedPlayerName + ":" + hostConfig.serialize());
        Router.goTo(event, "/tetris/TetrisGame.fxml", new TetrisGameSetup(hostName, joinedPlayerName, hostConfig));
        closeLan();
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
            availableGamesList.getItems().clear();
            closeJoinClient();
        } else {
            availableGamesList.setDisable(false);
            joinPlayerNameField.setDisable(false);
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
        setShown(modeChoicePane, view == MenuView.MODE_CHOICE);
        setShown(localPane, view == MenuView.LOCAL);
        setShown(lanPane, view == MenuView.LAN);
        setShown(hostPane, view == MenuView.HOST);
        setShown(joinPane, view == MenuView.JOIN);
        setShown(configBox, view == MenuView.LOCAL || view == MenuView.HOST);
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
        String playerOne = trimmed(localPlayerOneField);
        String playerTwo = trimmed(localPlayerTwoField);

        if (playerOne.isEmpty() || playerTwo.isEmpty()) {
            statusLabel.setText("Enter both local player names before starting.");
            return;
        }

        TetrisGameConfig config = buildConfig();
        if (config == null) {
            return;
        }

        TetrisGameSetup setup = new TetrisGameSetup(playerOne, playerTwo, config);
        Router.goTo(event, "/tetris/TetrisGame.fxml", setup);
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
                joinClient.send(JOIN + ":" + playerName);

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
        for (int i = 0; i < availableGamesList.getItems().size(); i++) {
            TetrisLanDiscoveryService.DiscoveredGame current = availableGamesList.getItems().get(i);
            if (current.sessionId().equals(game.sessionId())) {
                availableGamesList.getItems().set(i, game);
                return;
            }
        }

        availableGamesList.getItems().add(game);
        if (availableGamesList.getSelectionModel().isEmpty()) {
            availableGamesList.getSelectionModel().selectFirst();
        }
    }

    private void onHostMessage(String message) {
        Platform.runLater(() -> {
            if (!message.startsWith(JOIN + ":")) {
                return;
            }

            joinedPlayerName = message.substring((JOIN + ":").length()).trim();
            joinedPlayerLabel.setText("Joined: " + joinedPlayerName);
            hostStartGameButton.setDisable(false);
            udpDiscovery.stopAdvertising();
            statusLabel.setText(joinedPlayerName + " joined. You can start the game.");
        });
    }

    private void onJoinMessage(String message) {
        Platform.runLater(() -> {
            if (!message.startsWith(START + ":")) {
                return;
            }

            String[] parts = message.split(":", 4);
            if (parts.length != 4) {
                return;
            }

            gameStarted = true;
            TetrisGameConfig config = TetrisGameConfig.deserialize(parts[3]);
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            Router.goTo(stage, "/tetris/TetrisGame.fxml", new TetrisGameSetup(parts[1], parts[2], config));
            closeLan();
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
        if (extendedPieceCheckBox.isSelected()) {
            pieces.add("Extended");
        }
        if (customPieceCheckBox.isSelected()) {
            pieces.add("Custom");
        }

        TetrisGameConfig config = new TetrisGameConfig(pieces);
        if (!config.hasPieces()) {
            statusLabel.setText("Select at least one piece set.");
            return null;
        }

        return config;
    }

    private String trimmed(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }
}
