package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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

    private static final long DISCOVERED_GAME_TTL_MS = 5_000;

    @FXML
    private TextField whiteNameField;
    @FXML
    private TextField blackNameField;
    @FXML
    private TextField lanHostField;
    @FXML
    private TextField lanPortField;
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
    private GameServer pendingHostServer;

    @FXML
    private void initialize() {
        if (lanGamesList != null) {
            lanGamesList.setItems(discoveredGames);
        }
        bindOptionalLabel(statusLabel);
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
        closePendingHostServer();

        GameServer server;
        try {
            server = createListeningServer();
        } catch (IOException e) {
            statusLabel.setText("Could not host LAN game: " + e.getMessage());
            return;
        }

        pendingHostServer = server;
        int tcpPort = server.localPort();
        discoveryService.startAdvertising(
                hostPlayerName(),
                tcpPort,
                message -> Platform.runLater(() -> statusLabel.setText(message)));
        statusLabel.setText("Hosting on " + GameServer.getLocalAddress() + ":" + tcpPort
                + ". Waiting for joiner...");

        Thread waitThread = new Thread(() -> {
            try {
                server.waitForClient();
                clearPendingHostServer(server);
                closeDiscovery();
                Platform.runLater(() -> Router.goTo(
                        stage,
                        "/hexchess/HexChessGame.fxml",
                        HexChessGameRouteData.host(setup(HexGameMode.NETWORK_HOST), server)));
            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("LAN host failed: " + e.getMessage()));
                closeDiscovery();
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

        Integer port = parseLanPort();
        if (port != null) {
            joinHost(stageFrom(event), host, port);
        }
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
        statusLabel.setText("Connecting to " + host + ":" + port + "...");

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

    private GameServer createListeningServer() throws IOException {
        GameServer server = new GameServer();
        try {
            server.listen(GameServer.DEFAULT_PORT, message -> {
            }, () -> {
            });
            return server;
        } catch (IOException e) {
            server.close();
            GameServer fallbackServer = new GameServer();
            try {
                fallbackServer.listen(0, message -> {
                }, () -> {
                });
                return fallbackServer;
            } catch (IOException fallbackException) {
                fallbackServer.close();
                fallbackException.addSuppressed(e);
                throw fallbackException;
            }
        }
    }

    private String hostPlayerName() {
        return setup(HexGameMode.NETWORK_HOST).whiteName();
    }

    private void startListeningForLanGames() {
        discoveryService.startListening(
                game -> Platform.runLater(() -> rememberDiscoveredGame(game)),
                message -> Platform.runLater(() -> statusLabel.setText(message)));
    }

    private void rememberDiscoveredGame(HexChessLanDiscoveryService.DiscoveredGame game) {
        removeStaleDiscoveredGames();
        discoveredGamesBySession.put(game.sessionId(), game);
        discoveredGames.setAll(discoveredGamesBySession.values()
                .stream()
                .toList());
    }

    private void removeStaleDiscoveredGames() {
        long cutoff = System.currentTimeMillis() - DISCOVERED_GAME_TTL_MS;
        discoveredGamesBySession.entrySet().removeIf(entry -> entry.getValue().timestamp() < cutoff);
    }

    private Integer parseLanPort() {
        String rawPort = lanPortField == null ? "" : lanPortField.getText().trim();
        if (rawPort.isBlank()) {
            return GameServer.DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(rawPort);
            if (port >= 1 && port <= 65_535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }

        statusLabel.setText("Enter a valid TCP port from 1 to 65535.");
        return null;
    }

    private void closeDiscovery() {
        discoveryService.close();
        closePendingHostServer();
    }

    private void closePendingHostServer() {
        if (pendingHostServer != null) {
            pendingHostServer.close();
            pendingHostServer = null;
        }
    }

    private void clearPendingHostServer(GameServer server) {
        if (pendingHostServer == server) {
            pendingHostServer = null;
        }
    }

    private void bindOptionalLabel(Label label) {
        if (label == null) {
            return;
        }

        label.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !label.getText().isBlank(),
                label.textProperty()));
        label.managedProperty().bind(label.visibleProperty());
    }

    private Stage stageFrom(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
