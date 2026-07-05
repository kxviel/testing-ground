package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;
import seda_project.control_alt_defeat.gamebox.ui.TimedStatus;
import seda_project.control_alt_defeat.gamebox.util.DiscoveredGameListController;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;
import seda_project.control_alt_defeat.gamebox.util.UiVisibility;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MemoryMenuController implements RouteDataReceiver {

    private static final Logger log = LoggerFactory.getLogger(MemoryMenuController.class);
    private static final String GAME_BOARD_ROUTE = "/memory/GameBoard.fxml";
    private static final String PLAYER_ONE = SafeText.PLAYER_ONE_NAME;
    private static final String PLAYER_TWO = SafeText.PLAYER_TWO_NAME;
    private static final int STATUS_SECONDS = 10;
    private static final long DISCOVERED_GAME_TTL_MS = 5_000;

    @FXML
    private TextField kField;
    @FXML
    private TextField playerOneNameField;
    @FXML
    private TextField playerTwoNameField;
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
    private Label variant1TitleLabel;
    @FXML
    private Label variant1MetaLabel;
    @FXML
    private Label variant2TitleLabel;
    @FXML
    private Label variant2MetaLabel;
    @FXML
    private Label variant3TitleLabel;
    @FXML
    private Label variant3MetaLabel;

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
    private final ToggleGroup variantGroup = new ToggleGroup();
    private List<BoardVariant> currentVariants = List.of();
    private List<VariantRow> variantRows = List.of();
    private DiscoveredGameListController discoveredGameList;
    private TimedStatus timedStatus;
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
        timedStatus = new TimedStatus(statusLabel);
        discoveredGameList = new DiscoveredGameListController(availableGamesList);
        UiInputGuards.limitWholeNumber(kField, 2);
        UiInputGuards.limitPlayerNames(playerOneNameField, playerTwoNameField);
        variantRows = List.of(
                new VariantRow(variant1Radio, variant1TitleLabel, variant1MetaLabel),
                new VariantRow(variant2Radio, variant2TitleLabel, variant2MetaLabel),
                new VariantRow(variant3Radio, variant3TitleLabel, variant3MetaLabel));
        variantRows.forEach(row -> row.radio().setToggleGroup(variantGroup));
        UiVisibility.bindVisibleWhenTextPresent(kErrorLabel);
        UiVisibility.bindVisibleWhenTextPresent(statusLabel);
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
        variantRows.forEach(row -> UiVisibility.setVisibleManaged(row.radio(), false));
    }

    private void updateVariantRadios() {
        int selectedIndex = selectedVariantIndex();
        IntStream.range(0, variantRows.size())
                .forEach(this::updateVariantRadio);

        if (!currentVariants.isEmpty() && (selectedIndex < 0 || selectedIndex >= currentVariants.size())) {
            variant1Radio.setSelected(true);
        }
    }

    private void updateVariantRadio(int index) {
        VariantRow row = variantRows.get(index);
        boolean visible = index < currentVariants.size();
        UiVisibility.setVisibleManaged(row.radio(), visible);
        if (visible) {
            BoardVariant variant = currentVariants.get(index);
            row.title().setText(variant.difficulty);
            row.meta().setText(variant.totalCards + " cards · "
                    + variant.rows + "×" + variant.cols);
        }
    }

    private BoardVariant getSelectedVariant() {
        if (currentVariants.isEmpty())
            return null;

        return IntStream.range(0, currentVariants.size())
                .filter(i -> variantRows.get(i).radio().isSelected())
                .mapToObj(currentVariants::get)
                .findFirst()
                .orElse(currentVariants.get(0));
    }

    private int selectedVariantIndex() {
        return IntStream.range(0, variantRows.size())
                .filter(i -> variantRows.get(i).radio().isSelected())
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
        Router.goTo(event, GAME_BOARD_ROUTE, MemoryGameRouteData.local(v, playerOneName(), playerTwoName()));
    }

    @FXML
    private void onHostGame(ActionEvent event) {
        if (networkPending) {
            return;
        }

        BoardVariant v = validateAndGetVariant();
        if (v == null)
            return;

        Stage stage = Router.stageFrom(event);
        setNetworkPending(true, "Cancel Hosting");
        discoveryService.stopListening();
        closePendingNetwork();

        GameServer server = new GameServer();
        Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();
        String hostName = playerOneName();
        pendingServer = server;
        statusLabel.setText("Starting server...");

        startDaemon(() -> {
            try {
                server.listen(GameServer.DEFAULT_PORT, pendingMessages::add, () -> {
                });
                discoveryService.startAdvertising(
                        hostName,
                        GameServer.DEFAULT_PORT,
                        message -> Platform.runLater(() -> statusLabel.setText(message)));
                String ip = GameServer.getLocalAddress();
                Platform.runLater(() -> statusLabel.setText(
                        "Hosting on " + ip + ":" + GameServer.DEFAULT_PORT + ". Waiting for " + PLAYER_TWO + "..."));
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
                    UiVisibility.setVisibleManaged(btnCancelHost, false);
                    if (!beginNavigation()) {
                        server.close();
                        return;
                    }
                    Router.goTo(stage, GAME_BOARD_ROUTE, MemoryGameRouteData.host(v, server, pendingMessages, hostName));
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
                playerOneNameField, playerTwoNameField,
                variant1Radio, variant2Radio, variant3Radio, btnRefreshGames, availableGamesList)
                .forEach(control -> control.setDisable(disabled));
        updateJoinSelectedButton();
    }

    @FXML
    private void onRefreshLan() {
        if (networkPending) {
            return;
        }

        discoveredGameList.clear();
        startListeningForLanGames();
        statusLabel.setText("Looking for Memory LAN games...");
        updateJoinSelectedButton();
    }

    @FXML
    private void onJoinSelectedLan(ActionEvent event) {
        if (networkPending) {
            return;
        }

        LanDiscoveryService.DiscoveredGame selectedGame = discoveredGameList.selectedGame();
        if (selectedGame == null) {
            statusLabel.setText("Select a discovered LAN game first.");
            return;
        }

        Stage stage = Router.stageFrom(event);
        setNetworkPending(true, "Cancel Connecting");
        discoveryService.stopListening();
        statusLabel.setText("Connecting to " + selectedGame.playerName() + "...");
        String joinerName = playerTwoName();

        GameClient client = new GameClient();
        pendingClient = client;
        Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();

        startDaemon(() -> {
            try {
                client.connect(selectedGame.hostAddress(), selectedGame.tcpPort(), pendingMessages::add, () -> {
                });
                client.send(MemoryProtocol.join(joinerName));
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
                    Router.goTo(stage, GAME_BOARD_ROUTE, MemoryGameRouteData.join(client, pendingMessages, joinerName));
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

    private void setNetworkPending(boolean pending, String cancelText) {
        networkPending = pending;
        setMenuButtonsDisabled(pending);
        btnCancelHost.setText(cancelText);
        UiVisibility.setVisibleManaged(btnCancelHost, pending);
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
        discoveredGameList.removeStale(DISCOVERED_GAME_TTL_MS);
        discoveredGameList.upsert(game);
        updateJoinSelectedButton();
    }

    private void startStaleGameTimer() {
        discoveredGameList.startStaleTimer(DISCOVERED_GAME_TTL_MS);
    }

    private void stopStaleGameTimer() {
        discoveredGameList.stop();
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

    private static void startDaemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    public void showTimedStatus(String message, int seconds) {
        timedStatus.show(message, seconds);
    }

    private String playerOneName() {
        return SafeText.playerName(text(playerOneNameField), PLAYER_ONE);
    }

    private String playerTwoName() {
        return SafeText.playerName(text(playerTwoNameField), PLAYER_TWO);
    }

    private static String text(TextField field) {
        return field == null ? "" : field.getText();
    }

    private record VariantRow(RadioButton radio, Label title, Label meta) {
    }
}
