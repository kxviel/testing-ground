package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MemoryMenuController implements RouteDataReceiver {

    private static final Logger log = LoggerFactory.getLogger(MemoryMenuController.class);
    private static final String GAME_BOARD_ROUTE = "/memory/GameBoard.fxml";
    private static final int STATUS_SECONDS = 10;

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
    private TextField ipField;
    @FXML
    private HBox joinBox;
    @FXML
    private Button btnLocalGame;
    @FXML
    private Button btnHostGame;
    @FXML
    private Button btnJoinGame;
    @FXML
    private Button btnCancelHost;
    @FXML
    private Button btnApplyK;

    private GameServer pendingServer;

    private final ToggleGroup variantGroup = new ToggleGroup();
    private List<BoardVariant> currentVariants = List.of();

    @Override
    public void setRouteData(Object data) {
        if (data instanceof String message && !message.isBlank()) {
            showTimedStatus(message, STATUS_SECONDS);
        }
    }

    @FXML
    public void initialize() {
        variantRadios().forEach(radio -> radio.setToggleGroup(variantGroup));
        onApplyK();
    }

    @FXML
    private void onApplyK() {
        kErrorLabel.setText("");

        Integer k = parseK();
        if (k == null) {
            disableVariants();
            return;
        }

        currentVariants = BoardVariant.computeVariants(k);
        updateVariantRadios();
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
        variantRadios().forEach(radio -> setVisibleManaged(radio, false));
    }

    private void updateVariantRadios() {
        List<RadioButton> radios = variantRadios();
        IntStream.range(0, radios.size())
                .forEach(i -> updateVariantRadio(radios.get(i), i));

        if (!currentVariants.isEmpty() && variantGroup.getSelectedToggle() == null) {
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

    private BoardVariant validateAndGetVariant() {
        onApplyK();

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
        Router.goTo(event, GAME_BOARD_ROUTE, MemoryGameRouteData.local(v));
    }

    @FXML
    private void onHostGame(ActionEvent event) {
        BoardVariant v = validateAndGetVariant();
        if (v == null)
            return;

        Stage stage = stageFrom(event);
        setHostingPending(true);

        GameServer server = new GameServer();
        pendingServer = server;
        statusLabel.setText("Starting server...");

        startDaemon(() -> {
            try {
                server.listen(GameServer.DEFAULT_PORT, msg -> {
                }, () -> {
                });
                String ip = GameServer.getLocalAddress();
                Platform.runLater(() -> statusLabel.setText(
                        "Waiting for Player 2... Your IP: " + ip + "  Port: " + GameServer.DEFAULT_PORT));
                server.waitForClient();
                Platform.runLater(() -> {
                    pendingServer = null;
                    setVisibleManaged(btnCancelHost, false);
                    Router.goTo(stage, GAME_BOARD_ROUTE, MemoryGameRouteData.host(v, server));
                });
            } catch (Exception e) {
                if (server.isConnected() || pendingServer == null)
                    return;
                log.error("Host setup failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    setHostingPending(false);
                });
                server.close();
            }
        }, "memory-host-setup");
    }

    @FXML
    private void onCancelHost() {
        if (pendingServer != null) {
            pendingServer.close();
            pendingServer = null;
        }
        statusLabel.setText("Hosting cancelled.");
        setHostingPending(false);
    }

    private void setMenuButtonsDisabled(boolean disabled) {
        Stream.of(btnLocalGame, btnHostGame, btnJoinGame, kField, btnApplyK,
                variant1Radio, variant2Radio, variant3Radio)
                .forEach(control -> control.setDisable(disabled));
    }

    @FXML
    private void onJoinGame() {
        setVisibleManaged(joinBox, true);
    }

    @FXML
    private void onConnect(ActionEvent event) {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            statusLabel.setText("Please enter a host IP address.");
            return;
        }

        Stage stage = stageFrom(event);
        setMenuButtonsDisabled(true);
        statusLabel.setText("Connecting to " + ip + "...");

        GameClient client = new GameClient();
        Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();

        startDaemon(() -> {
            try {
                client.connect(ip, GameServer.DEFAULT_PORT, pendingMessages::add, () -> {
                });
                Platform.runLater(
                        () -> Router.goTo(stage, GAME_BOARD_ROUTE,
                                MemoryGameRouteData.join(client, pendingMessages)));
            } catch (Exception e) {
                log.error("Join failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    setMenuButtonsDisabled(false);
                });
                client.close();
            }
        }, "memory-client-connect");
    }

    private static Stage stageFrom(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }

    private void setHostingPending(boolean pending) {
        setMenuButtonsDisabled(pending);
        setVisibleManaged(btnCancelHost, pending);
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
        PauseTransition timer = new PauseTransition(Duration.seconds(seconds));
        timer.setOnFinished(e -> statusLabel.setText(""));
        timer.play();
    }
}
