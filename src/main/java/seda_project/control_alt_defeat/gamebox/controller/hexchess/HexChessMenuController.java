package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.io.IOException;
import java.util.stream.IntStream;

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
    private final ObservableList<LanDiscoveryService.DiscoveredGame> discoveredGames =
            FXCollections.observableArrayList();
    private GameServer pendingHostServer;
    private GameClient pendingJoinClient;
    private Timeline staleGameTimer;

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
        closeMenuNetwork();
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onStartBot(ActionEvent event) {
        closeMenuNetwork();
        Router.goTo(event, "/hexchess/HexChessGame.fxml", setup(HexGameMode.BOT));
    }

    @FXML
    private void onCustomSetup(ActionEvent event) {
        closeMenuNetwork();
        Router.goTo(event, "/hexchess/HexChessSetup.fxml", setup(HexGameMode.LOCAL));
    }

    @FXML
    private void onHostLan(ActionEvent event) {
        Stage stage = stageFrom(event);
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
                    Router.goTo(
                            stage,
                            "/hexchess/HexChessGame.fxml",
                            HexChessGameRouteData.host(setup(HexGameMode.NETWORK_HOST), server));
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (pendingHostServer == server) {
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
        LanDiscoveryService.DiscoveredGame selected = lanGamesList == null
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
        discoveredGames.clear();
        startListeningForLanGames();
        startStaleGameTimer();
        statusLabel.setText("Looking for Hex Chess LAN games...");
    }

    private void joinHost(Stage stage, String host, int port) {
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
        String selectedSessionId = selectedLanSessionId();

        removeStaleDiscoveredGames();

        int existingIndex = IntStream.range(0, discoveredGames.size())
                .filter(index -> discoveredGames.get(index).sessionId().equals(game.sessionId()))
                .findFirst()
                .orElse(-1);
        if (existingIndex >= 0) {
            discoveredGames.set(existingIndex, game);
            restoreLanSelection(selectedSessionId);
            return;
        }

        discoveredGames.add(game);
        restoreLanSelection(selectedSessionId);
    }

    private void removeStaleDiscoveredGames() {
        long cutoff = System.currentTimeMillis() - DISCOVERED_GAME_TTL_MS;
        discoveredGames.removeIf(game -> game.timestamp() < cutoff);
    }

    private void startStaleGameTimer() {
        stopStaleGameTimer();
        staleGameTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> removeStaleDiscoveredGames()));
        staleGameTimer.setCycleCount(Animation.INDEFINITE);
        staleGameTimer.play();
    }

    private void stopStaleGameTimer() {
        if (staleGameTimer != null) {
            staleGameTimer.stop();
            staleGameTimer = null;
        }
    }

    private String selectedLanSessionId() {
        if (lanGamesList == null) {
            return null;
        }

        LanDiscoveryService.DiscoveredGame selected = lanGamesList.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.sessionId();
    }

    private void restoreLanSelection(String sessionId) {
        if (lanGamesList == null || sessionId == null) {
            return;
        }

        IntStream.range(0, discoveredGames.size())
                .filter(index -> discoveredGames.get(index).sessionId().equals(sessionId))
                .findFirst()
                .ifPresent(index -> lanGamesList.getSelectionModel().select(index));
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
