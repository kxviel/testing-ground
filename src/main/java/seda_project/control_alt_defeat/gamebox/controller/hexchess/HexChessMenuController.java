package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.util.DiscoveredGameListController;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;
import seda_project.control_alt_defeat.gamebox.util.UiVisibility;

import java.io.IOException;

public class HexChessMenuController implements RouteDataReceiver {

    private static final long DISCOVERED_GAME_TTL_MS = 5_000;

    @FXML
    private TextField whiteNameField;
    @FXML
    private TextField blackNameField;
    @FXML
    private Label statusLabel;
    @FXML
    private ListView<LanDiscoveryService.DiscoveredGame> lanGamesList;
    @FXML
    private Button joinSelectedLanButton;

    private final LanDiscoveryService discoveryService = LanDiscoveryService.hexChess();
    private GameServer pendingHostServer;
    private GameClient pendingJoinClient;
    private DiscoveredGameListController discoveredGameList;
    private boolean navigationPending;

    @FXML
    private void initialize() {
        UiInputGuards.limitPlayerNames(whiteNameField, blackNameField);
        discoveredGameList = new DiscoveredGameListController(lanGamesList);
        UiVisibility.bindVisibleWhenTextPresent(statusLabel);
        if (lanGamesList != null && joinSelectedLanButton != null) {
            joinSelectedLanButton.disableProperty()
                    .bind(lanGamesList.getSelectionModel().selectedItemProperty().isNull());
        }
        startListeningForLanGames();
        startStaleGameTimer();
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof HexChessGameSetup nextSetup) {
            whiteNameField.setText(nextSetup.whiteName());
            blackNameField.setText(nextSetup.blackName());
        } else if (data instanceof String message && !message.isBlank()) {
            statusLabel.setText(message);
        }
    }

    @FXML
    private void onStartLocal(ActionEvent event) {
        if (!beginNavigation()) {
            return;
        }
        closeMenuNetwork();
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onStartBot(ActionEvent event) {
        if (!beginNavigation()) {
            return;
        }
        closeMenuNetwork();
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.BOT));
    }

    @FXML
    private void onCustomSetup(ActionEvent event) {
        if (!beginNavigation()) {
            return;
        }
        closeMenuNetwork();
        Router.goTo(event, "/hexchess/HexChessSetup.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onHostLan(ActionEvent event) {
        if (hasPendingNetwork()) {
            return;
        }

        Stage stage = Router.stageFrom(event);
        closePendingJoinClient();
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
                Platform.runLater(() -> {
                    if (pendingHostServer != server) {
                        server.close();
                        return;
                    }
                    clearPendingHostServer(server);
                    closeDiscovery();
                    if (!beginNavigation()) {
                        server.close();
                        return;
                    }
                    Router.goTo(
                            stage,
                            "/hexchess/HexChessGame.fxml",
                            HexChessGameRouteData.host(setup(HexGameMode.NETWORK_HOST), server));
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (pendingHostServer == server) {
                        pendingHostServer = null;
                        server.close();
                        statusLabel.setText("LAN host failed: " + e.getMessage());
                        closeDiscovery();
                        startListeningForLanGames();
                        startStaleGameTimer();
                    }
                });
            }
        }, "hexchess-host-wait");
        waitThread.setDaemon(true);
        waitThread.start();
    }

    @FXML
    private void onJoinSelectedLan(ActionEvent event) {
        if (hasPendingNetwork()) {
            return;
        }

        LanDiscoveryService.DiscoveredGame selected = discoveredGameList.selectedGame();
        if (selected == null) {
            statusLabel.setText("Select a discovered LAN game first.");
            return;
        }

        joinHost(Router.stageFrom(event), selected.hostAddress(), selected.tcpPort());
    }

    @FXML
    private void onRefreshLan() {
        if (hasPendingNetwork()) {
            return;
        }

        discoveredGameList.clear();
        startListeningForLanGames();
        startStaleGameTimer();
        statusLabel.setText("Looking for Hex Chess LAN games...");
    }

    private void joinHost(Stage stage, String host, int port) {
        if (hasPendingNetwork()) {
            return;
        }

        closePendingJoinClient();
        statusLabel.setText("Connecting to " + host + ":" + port + "...");
        GameClient client = new GameClient();
        pendingJoinClient = client;

        Thread connectThread = new Thread(() -> {
            try {
                client.connect(host, port, message -> {
                }, () -> {
                });
                Platform.runLater(() -> {
                    if (pendingJoinClient != client) {
                        client.close();
                        return;
                    }
                    pendingJoinClient = null;
                    closeDiscovery();
                    if (!beginNavigation()) {
                        client.close();
                        return;
                    }
                    Router.goTo(
                            stage,
                            "/hexchess/HexChessGame.fxml",
                            HexChessGameRouteData.join(setup(HexGameMode.NETWORK_CLIENT), client));
                });
            } catch (IOException e) {
                client.close();
                Platform.runLater(() -> {
                    if (pendingJoinClient == client) {
                        pendingJoinClient = null;
                        statusLabel.setText("Could not join LAN game: " + e.getMessage());
                    }
                });
            }
        }, "hexchess-client-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    private void onBack(ActionEvent event) {
        if (!beginNavigation()) {
            return;
        }
        closeMenuNetwork();
        Router.goTo(event, "/GameChoice.fxml", null);
    }

    private HexChessGameSetup setup(HexGameMode mode) {
        String whiteName = whiteNameField == null ? "Player 1" : whiteNameField.getText();
        String blackName = mode == HexGameMode.BOT
                ? "Bot"
                : blackNameField == null ? "Player 2" : blackNameField.getText();

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

    private void rememberDiscoveredGame(LanDiscoveryService.DiscoveredGame game) {
        discoveredGameList.removeStale(DISCOVERED_GAME_TTL_MS);
        discoveredGameList.upsert(game);
    }

    private void startStaleGameTimer() {
        discoveredGameList.startStaleTimer(DISCOVERED_GAME_TTL_MS);
    }

    private void stopStaleGameTimer() {
        discoveredGameList.stop();
    }

    private void closeDiscovery() {
        discoveryService.close();
        stopStaleGameTimer();
        closePendingHostServer();
    }

    private void closeMenuNetwork() {
        closeDiscovery();
        closePendingJoinClient();
    }

    private void closePendingHostServer() {
        if (pendingHostServer != null) {
            pendingHostServer.close();
            pendingHostServer = null;
        }
    }

    private void closePendingJoinClient() {
        if (pendingJoinClient != null) {
            pendingJoinClient.close();
            pendingJoinClient = null;
        }
    }

    private void clearPendingHostServer(GameServer server) {
        if (pendingHostServer == server) {
            pendingHostServer = null;
        }
    }

    private boolean hasPendingNetwork() {
        return pendingHostServer != null || pendingJoinClient != null;
    }

    private boolean beginNavigation() {
        if (navigationPending) {
            return false;
        }
        navigationPending = true;
        return true;
    }

}
