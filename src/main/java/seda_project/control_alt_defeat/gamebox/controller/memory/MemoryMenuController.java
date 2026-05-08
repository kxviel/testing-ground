package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.MessageRouter;

import java.io.IOException;
import java.util.List;

// Controller for the main menu screen.
public class MemoryMenuController {

    private static final Logger log = LoggerFactory.getLogger(MemoryMenuController.class);

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
    private javafx.scene.layout.HBox joinBox;
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

    @FXML
    public void initialize() {
        variant1Radio.setToggleGroup(variantGroup);
        variant2Radio.setToggleGroup(variantGroup);
        variant3Radio.setToggleGroup(variantGroup);
        onApplyK();
    }

    /**
     * Parses the k input field and rebuilds the board-size options.
     */
    @FXML
    private void onApplyK() {
        kErrorLabel.setText("");
        String text = kField.getText().trim();
        int k;
        try {
            k = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            kErrorLabel.setText("Please enter a whole number between 1 and 45.");
            disableVariants();
            return;
        }
        if (k < 1 || k > 45) {
            kErrorLabel.setText("k must be between 1 and 45. Please enter a valid value.");
            disableVariants();
            return;
        }
        currentVariants = BoardVariant.computeVariants(k);
        updateVariantRadios();
    }

    private void disableVariants() {
        currentVariants = List.of();
        setRadioVisible(variant1Radio, false);
        setRadioVisible(variant2Radio, false);
        setRadioVisible(variant3Radio, false);
    }

    /**
     * Updates a radio button's visibility and layout participation together.
     *
     * @param rb radio button to update
     * @param v  true when the option should be shown
     */
    private void setRadioVisible(RadioButton rb, boolean v) {
        rb.setVisible(v);
        rb.setManaged(v);
    }

    private void updateVariantRadios() {
        RadioButton[] radios = { variant1Radio, variant2Radio, variant3Radio };
        for (int i = 0; i < 3; i++) {
            if (i < currentVariants.size()) {
                BoardVariant v = currentVariants.get(i);
                radios[i].setText(v.toString());
                setRadioVisible(radios[i], true);
            } else {
                setRadioVisible(radios[i], false);
            }
        }

        if (!currentVariants.isEmpty() && variantGroup.getSelectedToggle() == null) {
            variant1Radio.setSelected(true);
        }
    }

    /**
     * @return selected board variant, or the first variant as a fallback
     */
    private BoardVariant getSelectedVariant() {
        if (currentVariants.isEmpty())
            return null;
        RadioButton[] radios = { variant1Radio, variant2Radio, variant3Radio };
        for (int i = 0; i < radios.length; i++) {
            if (radios[i].isSelected() && i < currentVariants.size()) {
                return currentVariants.get(i);
            }
        }
        return currentVariants.isEmpty() ? null : currentVariants.get(0);
    }

    /**
     * Revalidates the menu inputs before starting a game.
     *
     * @return selected variant, or null if the current input is invalid
     */
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

    /**
     * Starts a local two-player game on the same machine.
     */
    @FXML
    private void onLocalGame() {
        BoardVariant v = validateAndGetVariant();
        if (v == null)
            return;
        openGameBoard(v, GameController.Mode.LOCAL, null, null, null, null);
    }

    /**
     * Starts a host socket and waits for one joining client.
     */
    @FXML
    private void onHostGame() {
        BoardVariant v = validateAndGetVariant();
        if (v == null)
            return;

        setMenuButtonsDisabled(true);
        btnCancelHost.setVisible(true);
        btnCancelHost.setManaged(true);

        MessageRouter router = new MessageRouter();
        GameServer server = new GameServer();
        pendingServer = server;
        statusLabel.setText("Starting server...");

        Thread t = new Thread(() -> {
            try {
                server.listen(GameServer.DEFAULT_PORT, router.asConsumer(), router.asDisconnectRunnable());
                String ip = GameServer.getLocalAddress();
                Platform.runLater(() -> statusLabel.setText(
                        "Waiting for Player 2... Your IP: " + ip + "  Port: " + GameServer.DEFAULT_PORT));
                server.waitForClient();
                Platform.runLater(() -> {
                    // The server is now owned by GameController, so it is no longer cancellable here.
                    pendingServer = null;
                    btnCancelHost.setVisible(false);
                    btnCancelHost.setManaged(false);
                    openGameBoard(v, GameController.Mode.NETWORK_HOST, server, null, router, null);
                });
            } catch (Exception e) {
                if (server.isConnected() || pendingServer == null)
                    return;
                log.error("Host setup failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    setMenuButtonsDisabled(false);
                    btnCancelHost.setVisible(false);
                    btnCancelHost.setManaged(false);
                });
                server.close();
            }
        }, "host-setup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Cancels a host setup that is still waiting for a client.
     */
    @FXML
    private void onCancelHost() {
        if (pendingServer != null) {
            pendingServer.close();
            pendingServer = null;
        }
        statusLabel.setText("Hosting cancelled.");
        setMenuButtonsDisabled(false);
        btnCancelHost.setVisible(false);
        btnCancelHost.setManaged(false);
    }

    /**
     * Enables or disables controls that would conflict with an active
     * connection attempt.
     *
     * @param disabled true to block menu interaction
     */
    private void setMenuButtonsDisabled(boolean disabled) {
        btnLocalGame.setDisable(disabled);
        btnHostGame.setDisable(disabled);
        btnJoinGame.setDisable(disabled);
        kField.setDisable(disabled);
        btnApplyK.setDisable(disabled);
        variant1Radio.setDisable(disabled);
        variant2Radio.setDisable(disabled);
        variant3Radio.setDisable(disabled);
    }

    /**
     * Reveals the host-IP input used to join a network game.
     */
    @FXML
    private void onJoinGame() {
        joinBox.setVisible(true);
        joinBox.setManaged(true);
    }

    /**
     * Connects to a host and opens the game board after the socket is ready.
     */
    @FXML
    private void onConnect() {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            statusLabel.setText("Please enter a host IP address.");
            return;
        }

        setMenuButtonsDisabled(true);
        statusLabel.setText("Connecting to " + ip + "...");

        MessageRouter router = new MessageRouter();
        GameClient client = new GameClient();

        Thread t = new Thread(() -> {
            try {
                client.connect(ip, GameServer.DEFAULT_PORT, router.asConsumer(), router.asDisconnectRunnable());
                Platform.runLater(
                        () -> openGameBoard(null, GameController.Mode.NETWORK_CLIENT, null, client, null, router));
            } catch (Exception e) {
                log.error("Join failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    setMenuButtonsDisabled(false);
                });
                client.close();
            }
        }, "client-connect");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Loads the game-board screen and injects the selected mode and network
     * objects into its controller.
     *
     * @param variant      board variant for local/host modes, null for clients
     * @param mode         selected game mode
     * @param server       connected server for host mode
     * @param client       connected client for client mode
     * @param hostRouter   host-side message router
     * @param clientRouter client-side message router
     */
    private void openGameBoard(BoardVariant variant, GameController.Mode mode,
            GameServer server, GameClient client,
            MessageRouter hostRouter, MessageRouter clientRouter) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/memory/GameBoard.fxml"));
            Scene scene = new Scene(loader.load());
            GameController ctrl = loader.getController();
            ctrl.init(variant, mode, server, client, hostRouter, clientRouter);
            Stage stage = (Stage) kField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Multi Match Memory Game");
            ctrl.setPrimaryStage(stage);
        } catch (IOException e) {
            log.error("Failed to open game board", e);
            statusLabel.setText("Error loading game board.");
        }
    }

    /**
     * Shows a temporary status message in the main menu.
     *
     * @param message status text
     * @param seconds number of seconds before the text is cleared
     */
    public void showTimedStatus(String message, int seconds) {
        statusLabel.setText(message);
        PauseTransition timer = new PauseTransition(Duration.seconds(seconds));
        timer.setOnFinished(e -> statusLabel.setText(""));
        timer.play();
    }
}