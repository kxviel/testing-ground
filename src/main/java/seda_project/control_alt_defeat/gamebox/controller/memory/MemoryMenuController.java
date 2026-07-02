package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MemoryMenuController implements RouteDataReceiver {

    private static final Logger log = LoggerFactory.getLogger(MemoryMenuController.class);
    private static final String GAME_BOARD_ROUTE = "/memory/GameBoard.fxml";
    private static final String PLAYER_ONE = "Player 1";
    private static final int STATUS_SECONDS = 10;
    private static final long DISCOVERED_GAME_TTL_MS = 5_000;

    @FXML
    private TextField kField;
    @FXML
    private Label kErrorLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private RadioButton variant1Radio;
    @FXML
    private RadioButton variant2Radio;
    @FXML
    private RadioButton variant3Radio;

    @FXML
    private Button btnLocalGame;
    @FXML
    private Button btnHostGame;
    @FXML
    private Button btnCancelHost;
    @FXML
    private Button btnApplyK;
    @FXML
    private Button btnRefreshGames;
    @FXML
    private Button btnJoinSelectedGame;
    @FXML
    private ListView<LanDiscoveryService.DiscoveredGame> availableGamesList;

    private volatile GameServer pendingServer;
    private volatile GameClient pendingClient;

    private final LanDiscoveryService discoveryService = LanDiscoveryService.memory();
    private final ObservableList<LanDiscoveryService.DiscoveredGame> discoveredGames =
            FXCollections.observableArrayList();
    private final ToggleGroup variantGroup = new ToggleGroup();
    private List<BoardVariant> currentVariants = List.of();
    private Timeline staleGameTimer;
    private PauseTransition statusTimer;
    private boolean networkPending;
    private boolean variantsApplied;
    private boolean navigationPending;

    @Override
    public void setRouteData(Object data) {
        if (data instanceof String message && !message.isBlank()) {
            showTimedStatus(message, STATUS_SECONDS);
        }
    }

    @FXML
    public void initialize() {
        UiInputGuards.limitWholeNumber(kField, 2);
        variantRadios().forEach(radio -> radio.setToggleGroup(variantGroup));
        availableGamesList.setItems(discoveredGames);
        availableGamesList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldGame, newGame) -> updateJoinSelectedButton());
        kField.textProperty().addListener((observable, oldValue, newValue) -> variantsApplied = false);
        onApplyK();
        startListeningForLanGames();
        startStaleGameTimer();
        updateJoinSelectedButton();
    }

    @FXML
    private void onApplyK() {
        kErrorLabel.setText("");

        Integer k = parseK();
        if (k == null) {
            variantsApplied = false;
            disableVariants();
            return;
        }

        currentVariants = BoardVariant.computeVariants(k);
        updateVariantRadios();
        variantsApplied = true;
    }

    private Integer parseK() {
        try {
            int k = Integer.parseInt(kField.getText().trim());
            if (k >= 1 && k <= BoardVariant.MAX_CARDS) {
                return k;
            }
            kErrorLabel.setText("k must be between 1 and 45. Please enter a valid value.");
        } catch (NumberFormatException e) {
            kErrorLabel.setText("Please enter a whole number between 1 and 45.");
        }
        return null;
    }

    private void disableVariants() {
        currentVariants = List.of();
        variantGroup.selectToggle(null);
        variantRadios().forEach(radio -> setVisibleManaged(radio, false));
    }

    private void updateVariantRadios() {
        List<RadioButton> radios = variantRadios();
        int selectedIndex = selectedVariantIndex(radios);
        IntStream.range(0, radios.size())
                .forEach(i -> updateVariantRadio(radios.get(i), i));

        if (!currentVariants.isEmpty() && (selectedIndex < 0 || selectedIndex >= currentVariants.size())) {
            variant1Radio.setSelected(true);
        }
    }

    private void updateVariantRadio(RadioButton radio, int index) {
        boolean visible = index < currentVariants.size();
        setVisibleManaged(radio, visible);
        if (visible) {
            radio.setText(currentVariants.get(index).toString());
        }
    }

    private BoardVariant getSelectedVariant() {
        if (currentVariants.isEmpty())
            return null;

        List<RadioButton> radios = variantRadios();
        return IntStream.range(0, currentVariants.size())
                .filter(i -> radios.get(i).isSelected())
                .mapToObj(currentVariants::get)
                .findFirst()
                .orElse(currentVariants.get(0));
    }

    private int selectedVariantIndex(List<RadioButton> radios) {
        return IntStream.range(0, radios.size())
                .filter(i -> radios.get(i).isSelected())
                .findFirst()
                .orElse(-1);
    }

    private BoardVariant validateAndGetVariant() {
        if (!variantsApplied) {
            kErrorLabel.setText("Click Apply before starting after changing k.");
            return null;
        }

        if (currentVariants.isEmpty()) {
            if (kErrorLabel.getText().isEmpty()) {
                kErrorLabel.setText("No valid board variants for this k.");
            }
            return null;
        }
        return getSelectedVariant();
    }

    @FXML
    private void onLocalGame(ActionEvent event) {
        BoardVariant v = validateAndGetVariant();
        if (v == null)
            return;
        if (!beginNavigation()) {
            return;
        }
        closeMenuNetwork();
        Router.goTo(event, GAME_BOARD_ROUTE, MemoryGameRouteData.local(v));
    }

    @FXML
    private void onHostGame(ActionEvent event) {
        if (networkPending) {
            return;
        }

        BoardVariant v = validateAndGetVariant();
        if (v == null)
            return;

        Stage stage = stageFrom(event);
        setNetworkPending(true, "Cancel Hosting");
        discoveryService.stopListening();
        closePendingNetwork();

        GameServer server = new GameServer();
        pendingServer = server;
        statusLabel.setText("Starting server...");

        startDaemon(() -> {
            try {
                server.listen(GameServer.DEFAULT_PORT, msg -> {
                }, () -> {
                });
                discoveryService.startAdvertising(
                        PLAYER_ONE,
                        GameServer.DEFAULT_PORT,
                        message -> Platform.runLater(() -> statusLabel.setText(message)));
                String ip = GameServer.getLocalAddress();
                Platform.runLater(() -> statusLabel.setText(
                        "Hosting on " + ip + ":" + GameServer.DEFAULT_PORT + ". Waiting for Player 2..."));
                server.waitForClient();
                Platform.runLater(() -> {
                    if (pendingServer != server) {
                        server.close();
                        return;
                    }
                    pendingServer = null;
                    discoveryService.stopAdvertising();
                    discoveryService.close();
                    stopStaleGameTimer();
                    setVisibleManaged(btnCancelHost, false);
                    if (!beginNavigation()) {
                        server.close();
                        return;
                    }
                    Router.goTo(stage, GAME_BOARD_ROUTE, MemoryGameRouteData.host(v, server));
                });
            } catch (IOException e) {
                if (server.isConnected() || pendingServer == null)
                    return;
                log.error("Host setup failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    if (pendingServer == server) {
                        pendingServer = null;
                    }
                    statusLabel.setText("Error: " + e.getMessage());
                    setNetworkPending(false, "");
                });
                server.close();
                discoveryService.stopAdvertising();
            }
        }, "memory-host-setup");
    }

    @FXML
    private void onCancelHost() {
        boolean hosting = pendingServer != null;
        closePendingNetwork();
        discoveryService.stopAdvertising();
        startListeningForLanGames();
        statusLabel.setText(hosting ? "Hosting cancelled." : "Connection cancelled.");
        setNetworkPending(false, "");
    }

    private void setMenuButtonsDisabled(boolean disabled) {
        Stream.of(btnLocalGame, btnHostGame, kField, btnApplyK,
                variant1Radio, variant2Radio, variant3Radio, btnRefreshGames, availableGamesList)
                .forEach(control -> control.setDisable(disabled));
        updateJoinSelectedButton();
    }

    @FXML
    private void onRefreshLan() {
        if (networkPending) {
            return;
        }

        discoveredGames.clear();
        startListeningForLanGames();
        statusLabel.setText("Looking for Memory LAN games...");
        updateJoinSelectedButton();
    }

    @FXML
    private void onJoinSelectedLan(ActionEvent event) {
        if (networkPending) {
            return;
        }

        LanDiscoveryService.DiscoveredGame selectedGame = availableGamesList.getSelectionModel().getSelectedItem();
        if (selectedGame == null) {
            statusLabel.setText("Select a discovered LAN game first.");
            return;
        }

        Stage stage = stageFrom(event);
        setNetworkPending(true, "Cancel Connecting");
        discoveryService.stopListening();
        statusLabel.setText("Connecting to " + selectedGame.playerName() + "...");

        GameClient client = new GameClient();
        pendingClient = client;
        Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();

        startDaemon(() -> {
            try {
                client.connect(selectedGame.hostAddress(), selectedGame.tcpPort(), pendingMessages::add, () -> {
                });
                Platform.runLater(() -> {
                    if (pendingClient != client) {
                        client.close();
                        return;
                    }
                    pendingClient = null;
                    discoveryService.close();
                    stopStaleGameTimer();
                    if (!beginNavigation()) {
                        client.close();
                        return;
                    }
                    Router.goTo(stage, GAME_BOARD_ROUTE, MemoryGameRouteData.join(client, pendingMessages));
                });
            } catch (IOException e) {
                log.error("Join failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    if (pendingClient != client) {
                        return;
                    }
                    pendingClient = null;
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    setNetworkPending(false, "");
                    startListeningForLanGames();
                });
                client.close();
            }
        }, "memory-client-connect");
    }

    @FXML
    private void onBack(ActionEvent event) {
        if (!beginNavigation()) {
            return;
        }
        closeMenuNetwork();
        Router.goTo(event, "/GameChoice.fxml", null);
    }

    private static Stage stageFrom(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }

    private void setNetworkPending(boolean pending, String cancelText) {
        networkPending = pending;
        setMenuButtonsDisabled(pending);
        btnCancelHost.setText(cancelText);
        setVisibleManaged(btnCancelHost, pending);
        updateJoinSelectedButton();
    }

    private void closePendingNetwork() {
        if (pendingServer != null) {
            pendingServer.close();
            pendingServer = null;
        }
        if (pendingClient != null) {
            pendingClient.close();
            pendingClient = null;
        }
    }

    private void closeMenuNetwork() {
        discoveryService.close();
        stopStaleGameTimer();
        closePendingNetwork();
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
        } else {
            discoveredGames.add(game);
        }

        restoreLanSelection(selectedSessionId);
        if (availableGamesList.getSelectionModel().isEmpty()) {
            availableGamesList.getSelectionModel().selectFirst();
        }
        updateJoinSelectedButton();
    }

    private void removeStaleDiscoveredGames() {
        long cutoff = System.currentTimeMillis() - DISCOVERED_GAME_TTL_MS;
        discoveredGames.removeIf(game -> game.timestamp() < cutoff);
        updateJoinSelectedButton();
    }

    private String selectedLanSessionId() {
        LanDiscoveryService.DiscoveredGame selected = availableGamesList.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.sessionId();
    }

    private void restoreLanSelection(String sessionId) {
        if (sessionId == null) {
            return;
        }

        IntStream.range(0, discoveredGames.size())
                .filter(index -> discoveredGames.get(index).sessionId().equals(sessionId))
                .findFirst()
                .ifPresent(index -> availableGamesList.getSelectionModel().select(index));
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

    private void updateJoinSelectedButton() {
        if (btnJoinSelectedGame != null && availableGamesList != null) {
            btnJoinSelectedGame.setDisable(networkPending || availableGamesList.getSelectionModel().isEmpty());
        }
    }

    private boolean beginNavigation() {
        if (navigationPending) {
            return false;
        }
        navigationPending = true;
        return true;
    }

    private List<RadioButton> variantRadios() {
        return List.of(variant1Radio, variant2Radio, variant3Radio);
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void startDaemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    public void showTimedStatus(String message, int seconds) {
        statusLabel.setText(message);
        if (statusTimer != null) {
            statusTimer.stop();
        }

        statusTimer = new PauseTransition(Duration.seconds(seconds));
        statusTimer.setOnFinished(e -> {
            if (message.equals(statusLabel.getText())) {
                statusLabel.setText("");
            }
            statusTimer = null;
        });
        statusTimer.play();
    }
}
