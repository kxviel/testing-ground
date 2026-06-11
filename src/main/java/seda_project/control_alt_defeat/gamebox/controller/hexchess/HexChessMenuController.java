package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessLanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HexChessMenuController {

    @FXML
    private TextField whiteNameField;
    @FXML
    private TextField blackNameField;
    @FXML
    private TextField lanHostField;
    @FXML
    private Label statusLabel;
    @FXML
    private ListView<HexChessLanDiscoveryService.DiscoveredGame> lanGamesList;
    @FXML
    private Button joinSelectedLanButton;

    private final HexChessLanDiscoveryService discoveryService = new HexChessLanDiscoveryService();
    private final ObservableList<HexChessLanDiscoveryService.DiscoveredGame> discoveredGames =
            FXCollections.observableArrayList();
    private final Map<String, HexChessLanDiscoveryService.DiscoveredGame> discoveredGamesBySession =
            new LinkedHashMap<>();

    @FXML
    private void initialize() {
        if (lanGamesList != null) {
            lanGamesList.setItems(discoveredGames);
        }
        if (lanGamesList != null && joinSelectedLanButton != null) {
            joinSelectedLanButton.disableProperty()
                    .bind(lanGamesList.getSelectionModel().selectedItemProperty().isNull());
        }
        startListeningForLanGames();
    }

    @FXML
    private void onStartLocal(ActionEvent event) {
        closeDiscovery();
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onStartBot(ActionEvent event) {
        closeDiscovery();
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.BOT));
    }

    @FXML
    private void onCustomSetup(ActionEvent event) {
        closeDiscovery();
        Router.goTo(event, "/hexchess/HexChessSetup.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onHostLan(ActionEvent event) {
        Stage stage = stageFrom(event);
        GameServer server = new GameServer();

        try {
            server.listen(GameServer.DEFAULT_PORT, message -> {
            }, () -> {
            });
        } catch (IOException e) {
            statusLabel.setText("Could not host LAN game: " + e.getMessage());
            return;
        }

        discoveryService.startAdvertising(
                playerName(HexGameMode.NETWORK_HOST),
                GameServer.DEFAULT_PORT,
                message -> Platform.runLater(() -> statusLabel.setText(message)));
        statusLabel.setText("Hosting on " + GameServer.getLocalAddress() + ":" + GameServer.DEFAULT_PORT
                + ". Waiting for joiner...");

        Thread waitThread = new Thread(() -> {
            try {
                server.waitForClient();
                closeDiscovery();
                Platform.runLater(() -> Router.goTo(
                        stage,
                        "/hexchess/HexChessGame.fxml",
                        HexChessGameRouteData.host(setup(HexGameMode.NETWORK_HOST), server)));
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("LAN host failed: " + e.getMessage()));
                closeDiscovery();
                server.close();
            }
        }, "hexchess-host-wait");
        waitThread.setDaemon(true);
        waitThread.start();
    }

    @FXML
    private void onJoinLan(ActionEvent event) {
        String host = lanHostField == null ? "" : lanHostField.getText().trim();
        if (host.isBlank()) {
            statusLabel.setText("Enter the host IP address first.");
            return;
        }

        joinHost(stageFrom(event), host, GameServer.DEFAULT_PORT);
    }

    @FXML
    private void onJoinSelectedLan(ActionEvent event) {
        HexChessLanDiscoveryService.DiscoveredGame selected = lanGamesList == null
                ? null
                : lanGamesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a discovered LAN game first.");
            return;
        }

        joinHost(stageFrom(event), selected.hostAddress(), selected.tcpPort());
    }

    @FXML
    private void onRefreshLan() {
        discoveredGamesBySession.clear();
        discoveredGames.clear();
        startListeningForLanGames();
        statusLabel.setText("Looking for Hex Chess LAN games...");
    }

    private void joinHost(Stage stage, String host, int port) {
        statusLabel.setText("Connecting to " + host + "...");

        Thread connectThread = new Thread(() -> {
            GameClient client = new GameClient();
            try {
                client.connect(host, port, message -> {
                }, () -> {
                });
                closeDiscovery();
                Platform.runLater(() -> Router.goTo(
                        stage,
                        "/hexchess/HexChessGame.fxml",
                        HexChessGameRouteData.join(setup(HexGameMode.NETWORK_CLIENT), client)));
            } catch (IOException e) {
                client.close();
                Platform.runLater(() -> statusLabel.setText("Could not join LAN game: " + e.getMessage()));
            }
        }, "hexchess-client-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onBack(ActionEvent event) {
        closeDiscovery();
        Router.goTo(event, "/GameChoice.fxml", null);
    }

    private HexChessGameSetup setup(HexGameMode mode) {
        String whiteName = whiteNameField == null ? "White" : whiteNameField.getText();
        String blackName = mode == HexGameMode.BOT
                ? "Bot"
                : blackNameField == null ? "Black" : blackNameField.getText();

        return new HexChessGameSetup(whiteName, blackName, mode);
    }

    private String playerName(HexGameMode mode) {
        return setup(mode).whiteName();
    }

    private void startListeningForLanGames() {
        discoveryService.startListening(
                game -> Platform.runLater(() -> rememberDiscoveredGame(game)),
                message -> Platform.runLater(() -> statusLabel.setText(message)));
    }

    private void rememberDiscoveredGame(HexChessLanDiscoveryService.DiscoveredGame game) {
        discoveredGamesBySession.put(game.sessionId(), game);
        discoveredGames.setAll(discoveredGamesBySession.values()
                .stream()
                .toList());
    }

    private void closeDiscovery() {
        discoveryService.close();
    }

    private Stage stageFrom(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
