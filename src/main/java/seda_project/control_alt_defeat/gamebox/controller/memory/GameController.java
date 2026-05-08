package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Duration;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.MessageRouter;
import seda_project.control_alt_defeat.gamebox.network.Protocol;

import java.io.IOException;
import java.util.List;

// Controller for the active game-board screen.
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    public enum Mode {
        LOCAL, NETWORK_HOST, NETWORK_CLIENT
    }

    @FXML
    private Label p1Label;
    @FXML
    private Label p2Label;
    @FXML
    private Label turnLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private GridPane cardGrid;
    @FXML
    private javafx.scene.layout.HBox postGameBar;
    @FXML
    private Label resultLabel;
    @FXML
    private Button btnPostPlayAgain;
    @FXML
    private Button btnPostMainMenu;

    private GameModel model;
    private Mode mode;
    private GameServer server;
    private GameClient client;

    private Button[] cardButtons;
    private boolean inputLocked = false;
    private boolean gameEnded = false;
    private Stage primaryStage;
    private PauseTransition mismatchPause;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    /**
     * Initializes the game screen for local play, hosting, or joining.
     *
     * @param variant      selected board variant for local/host modes
     * @param mode         controller mode
     * @param server       connected server for host mode
     * @param client       connected client for client mode
     * @param hostRouter   host-side message router
     * @param clientRouter client-side message router
     */
    public void init(BoardVariant variant, Mode mode,
            GameServer server, GameClient client,
            MessageRouter hostRouter, MessageRouter clientRouter) {
        this.mode = mode;
        this.server = server;
        this.client = client;

        if (mode == Mode.NETWORK_CLIENT) {
            clientRouter.setDelegate(msg -> Platform.runLater(() -> processMessage(msg)));
            clientRouter.setDisconnectDelegate(() -> Platform.runLater(this::handleDisconnect));
            statusLabel.setText("Connected! Waiting for host to start the game...");
            return;
        }

        this.model = new GameModel(variant);

        if (mode == Mode.NETWORK_HOST) {
            hostRouter.setDelegate(msg -> Platform.runLater(() -> processClientMessage(msg)));
            hostRouter.setDisconnectDelegate(() -> Platform.runLater(this::handleDisconnect));
            sendInitToClient();
        }

        buildBoard();
        refreshUI();
    }

    /**
     * Sends the complete board setup to the connected client.
     */
    private void sendInitToClient() {
        List<String> syms = model.getSymbolOrder();
        String symStr = String.join(",", syms);
        server.send(Protocol.make(Protocol.INIT,
                String.valueOf(model.getK()),
                String.valueOf(model.getN()),
                String.valueOf(model.getRows()),
                String.valueOf(model.getCols()),
                symStr));
    }

    /**
     * Handles messages received by the host from the joining client.
     *
     * @param msg raw protocol line
     */
    private void processClientMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;
        String[] parts = msg.split(":", -1);
        switch (parts[0]) {
            case Protocol.FLIP -> {
                if (model.getCurrentPlayer() == 1) {
                    try {
                        int idx = Integer.parseInt(parts[1]);
                        executeCardSelect(idx);
                    } catch (Exception e) {
                        log.warn("Ignoring malformed FLIP from client: {}", parts.length > 1 ? parts[1] : "(empty)");
                    }
                }
            }
            case Protocol.RESTART -> {
                String initiator = (parts.length > 1 && !parts[1].isBlank()) ? parts[1] : "Player 2";
                restartGame(initiator);
                showTimedStatus(initiator + " restarted the game!", 10);
            }
            case Protocol.QUIT -> handleRemoteQuit();
        }
    }

    /**
     * Handles messages received by the network client from the host.
     *
     * @param msg raw protocol line
     */
    private void processMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;
        String[] parts = msg.split(":", -1);
        switch (parts[0]) {
            case Protocol.INIT -> handleInit(parts);
            case Protocol.FLIP -> handleRemoteFlip(parts);
            case Protocol.CLOSE -> handleRemoteClose();
            case Protocol.GAMEOVER -> handleGameOverMsg(parts);
            case Protocol.RESTART -> handleRestart(parts);
            case Protocol.QUIT -> handleRemoteQuit();
        }
    }

    /**
     * Builds or rebuilds the client-side model from a host INIT message.
     *
     * @param parts colon-split INIT message
     */
    private void handleInit(String[] parts) {
        boolean isRestart = (model != null);

        int k = Integer.parseInt(parts[1]);
        int n = Integer.parseInt(parts[2]);
        int rows = Integer.parseInt(parts[3]);
        int cols = Integer.parseInt(parts[4]);
        List<String> symList = List.of(parts[5].split(","));
        model = new GameModel(k, n, rows, cols, symList, 0);

        gameEnded = false;
        inputLocked = false;
        hidePostGameBar();

        buildBoard();
        refreshUI();
        if (!isRestart) {
            showTimedStatus("Game started! Player 1 (Host) goes first.", 10);
        }
    }

    /**
     * Applies a card flip that originated from the remote player.
     *
     * @param parts colon-split FLIP message
     */
    private void handleRemoteFlip(String[] parts) {
        try {
            int idx = Integer.parseInt(parts[1]);
            GameModel.SelectResult result = model.selectCard(idx);
            if (result == GameModel.SelectResult.IGNORED)
                return;
            if (result == GameModel.SelectResult.RESOLVED_MATCH) {
                refreshAll();
                if (model.isGameOver())
                    showGameOver();
            } else {
                updateCardButton(idx);
                refreshUI();
                if (result == GameModel.SelectResult.RESOLVED_MISMATCH) {
                    inputLocked = true;
                    statusLabel.setText("Mismatch! Closing cards...");
                }
            }
        } catch (Exception e) {
            log.warn("Ignoring malformed FLIP message: {}", parts.length > 1 ? parts[1] : "(empty)");
        }
    }

    private void handleRemoteClose() {
        model.closeOpenCards();
        refreshAll();
        inputLocked = false;
        statusLabel.setText("");
    }

    /**
     * Shows the game-over state after a remote GAMEOVER message.
     *
     * @param parts colon-split GAMEOVER message
     */
    private void handleGameOverMsg(String[] parts) {
        if (gameEnded)
            return;
        gameEnded = true;
        inputLocked = true;
        showGameOver();
    }

    /**
     * Handles the notification that the remote side restarted the game.
     *
     * @param parts colon-split RESTART message
     */
    private void handleRestart(String[] parts) {
        if (mode == Mode.NETWORK_CLIENT) {
            String initiator = (parts != null && parts.length > 1 && !parts[1].isBlank())
                    ? parts[1]
                    : "Player 1";
            showTimedStatus(initiator + " restarted the game!", 10);
            return;
        }

        inputLocked = false;
        gameEnded = false;
    }

    /**
     * Returns to the menu when the other player intentionally leaves.
     */
    private void handleRemoteQuit() {
        String leavingPlayer = (mode == Mode.NETWORK_HOST) ? "Player 2" : "Player 1";
        inputLocked = true;
        hidePostGameBar();
        returnToMainMenu(leavingPlayer + " left the game.");
    }

    /**
     * Reports an unexpected network disconnect and returns to the menu.
     */
    private void handleDisconnect() {
        if (gameEnded)
            return;
        inputLocked = true;
        statusLabel.setText("Connection lost!");
        Alert a = new Alert(Alert.AlertType.WARNING,
                "The connection to the other player was lost.", ButtonType.OK);
        a.setTitle("Connection Lost");
        a.showAndWait();
        returnToMainMenu(null);
    }

    /**
     * Creates all card buttons for the current model.
     */
    private void buildBoard() {
        cardGrid.getChildren().clear();
        int total = model.getRows() * model.getCols();
        cardButtons = new Button[total];

        int fontSize = total > 30 ? 20 : (total > 16 ? 24 : 28);
        double btnSize = total > 30 ? 52 : (total > 16 ? 60 : 68);

        for (int i = 0; i < total; i++) {
            final int idx = i;
            Button btn = new Button("🂠");
            btn.setId("card_" + i);
            btn.setPrefWidth(btnSize);
            btn.setPrefHeight(btnSize);
            btn.setStyle(faceDownStyle(fontSize));
            btn.setOnAction(e -> onCardClick(idx));
            cardGrid.add(btn, i % model.getCols(), i / model.getCols());
            cardButtons[i] = btn;
        }
    }

    /**
     * Handles one local click on a card button.
     *
     * @param idx selected card index
     */
    private void onCardClick(int idx) {
        if (inputLocked || gameEnded)
            return;

        if (mode == Mode.NETWORK_HOST && model.getCurrentPlayer() != 0) {
            statusLabel.setText("It's Player 2's turn. Please wait.");
            return;
        }
        if (mode == Mode.NETWORK_CLIENT && model.getCurrentPlayer() != 1) {
            statusLabel.setText("It's Player 1's turn. Please wait.");
            return;
        }

        Card card = model.getCard(idx);
        if (!card.isSelectable())
            return;

        if (mode == Mode.NETWORK_CLIENT) {
            client.send(Protocol.make(Protocol.FLIP, String.valueOf(idx)));

            GameModel.SelectResult r = model.selectCard(idx);
            if (r == GameModel.SelectResult.IGNORED)
                return;
            updateCardButton(idx);
            refreshUI();
            if (r == GameModel.SelectResult.RESOLVED_MISMATCH) {
                inputLocked = true;
                statusLabel.setText("Mismatch! Waiting for host...");
            } else if (r == GameModel.SelectResult.RESOLVED_MATCH) {
                refreshAll();
                if (model.isGameOver())
                    showGameOver();
            }
        } else {
            executeCardSelect(idx);
        }
    }

    /**
     * Applies a valid card selection to the authoritative model.
     *
     * @param idx selected card index
     */
    private void executeCardSelect(int idx) {
        Card card = model.getCard(idx);
        if (!card.isSelectable())
            return;

        GameModel.SelectResult result = model.selectCard(idx);
        if (result == GameModel.SelectResult.IGNORED)
            return;

        updateCardButton(idx);

        if (mode == Mode.NETWORK_HOST) {
            server.send(Protocol.make(Protocol.FLIP, String.valueOf(idx)));
        }
        refreshUI();

        if (result == GameModel.SelectResult.RESOLVED_MATCH) {
            refreshAll();
            if (model.isGameOver()) {
                if (mode == Mode.NETWORK_HOST) {
                    server.send(Protocol.make(Protocol.GAMEOVER,
                            String.valueOf(model.getWinner()),
                            String.valueOf(model.getScore(0)),
                            String.valueOf(model.getScore(1))));
                }
                showGameOver();
            }
        } else if (result == GameModel.SelectResult.RESOLVED_MISMATCH) {
            inputLocked = true;
            statusLabel.setText("Mismatch! Closing cards...");
            if (mismatchPause != null)
                mismatchPause.stop();
            // Keep mismatched cards visible briefly before hiding them again.
            mismatchPause = new PauseTransition(Duration.millis(800));
            mismatchPause.setOnFinished(e -> {
                model.closeOpenCards();
                refreshAll();
                inputLocked = false;
                statusLabel.setText("");
                if (mode == Mode.NETWORK_HOST && server != null)
                    server.send(Protocol.CLOSE);
            });
            mismatchPause.play();
        }
    }

    /**
     * Refreshes score labels and turn indicator.
     */
    private void refreshUI() {
        int cur = model.getCurrentPlayer();
        p1Label.setText("Player 1: " + model.getScore(0));
        p2Label.setText("Player 2: " + model.getScore(1));
        p1Label.setStyle(cur == 0
                ? "-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0D47A1;"
                : "-fx-font-size:16px;-fx-font-weight:bold;");
        p2Label.setStyle(cur == 1
                ? "-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0D47A1;"
                : "-fx-font-size:16px;-fx-font-weight:bold;");
        turnLabel.setText("▶ " + (cur == 0 ? "Player 1" : "Player 2") + "'s Turn");
    }

    /**
     * Refreshes every card button and the score/turn labels.
     */
    private void refreshAll() {
        if (cardButtons == null)
            return;
        for (int i = 0; i < cardButtons.length; i++)
            updateCardButton(i);
        refreshUI();
    }

    /**
     * Updates one card button to match the model state.
     *
     * @param idx card index to redraw
     */
    private void updateCardButton(int idx) {
        if (cardButtons == null || idx >= cardButtons.length)
            return;
        Card card = model.getCard(idx);
        Button btn = cardButtons[idx];
        int fontSize = cardButtons.length > 30 ? 20 : (cardButtons.length > 16 ? 24 : 28);
        if (card.isMatched()) {
            btn.setText(card.getSymbol());
            btn.setStyle(matchedStyle(fontSize));
            btn.setDisable(true);
        } else if (card.isFaceUp()) {
            btn.setText(card.getSymbol());
            btn.setStyle(faceUpStyle(fontSize));
        } else {
            btn.setText("🂠");
            btn.setStyle(faceDownStyle(fontSize));
            btn.setDisable(false);
        }
    }

    /**
     * @param fs font size in pixels
     * @return JavaFX CSS for a hidden card
     */
    private String faceDownStyle(int fs) {
        return "-fx-font-size:" + fs + "px; -fx-background-color:#1976D2; -fx-text-fill:white; "
                + "-fx-background-radius:6; -fx-cursor:hand;";
    }

    /**
     * @param fs font size in pixels
     * @return JavaFX CSS for a revealed card
     */
    private String faceUpStyle(int fs) {
        return "-fx-font-size:" + fs + "px; -fx-background-color:#FFF9C4; -fx-text-fill:black; "
                + "-fx-background-radius:6; -fx-border-color:#F9A825; -fx-border-width:2; -fx-border-radius:6;"
                + "-fx-padding:0;";
    }

    /**
     * @param fs font size in pixels
     * @return JavaFX CSS for a permanently matched card
     */
    private String matchedStyle(int fs) {
        return "-fx-font-size:" + fs + "px; -fx-background-color:#C8E6C9; -fx-text-fill:black; "
                + "-fx-background-radius:6; -fx-opacity:0.85; -fx-padding:0;";
    }

    /**
     * Displays final scores and offers the next action.
     */
    private void showGameOver() {
        gameEnded = true;
        inputLocked = true;
        int winner = model.getWinner();
        String resultText = winner < 0 ? "It's a Draw! 🤝"
                : "Player " + (winner + 1) + " Wins! 🎉";
        String scores = "Player 1: " + model.getScore(0)
                + "  |  Player 2: " + model.getScore(1);

        if (mode == Mode.LOCAL) {
            Alert alert = new Alert(Alert.AlertType.NONE);
            alert.setTitle("Game Over");
            alert.setHeaderText(resultText);
            alert.setContentText(scores);
            ButtonType restartBtn = new ButtonType("Play Again", ButtonBar.ButtonData.YES);
            ButtonType menuBtn = new ButtonType("Main Menu", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(restartBtn, menuBtn);
            alert.showAndWait().ifPresent(btn -> {
                if (btn == restartBtn)
                    restartGame();
                else
                    returnToMainMenu(null);
            });
        } else {
            resultLabel.setText(resultText + "   " + scores);
            postGameBar.setVisible(true);
            postGameBar.setManaged(true);
        }
    }

    /**
     * Restarts the game from the post-game action bar.
     */
    @FXML
    private void onPostPlayAgain() {
        String myName = (mode == Mode.NETWORK_HOST) ? "Player 1" : "Player 2";
        hidePostGameBar();
        restartGame(myName);
        showTimedStatus(myName + " restarted the game!", 10);
    }

    /**
     * Returns to the menu from the post-game action bar and notifies the peer.
     */
    @FXML
    private void onPostMainMenu() {
        String myName = (mode == Mode.NETWORK_HOST) ? "Player 1" : "Player 2";
        hidePostGameBar();

        if (mode == Mode.NETWORK_HOST && server != null)
            server.send(Protocol.QUIT);
        if (mode == Mode.NETWORK_CLIENT && client != null)
            client.send(Protocol.QUIT);
        returnToMainMenu(myName + " left the game.");
    }

    /**
     * Shows a status message for a limited time.
     *
     * @param message status text
     * @param seconds number of seconds before the text is cleared
     */
    private void showTimedStatus(String message, int seconds) {
        statusLabel.setText(message);
        PauseTransition timer = new PauseTransition(javafx.util.Duration.seconds(seconds));
        timer.setOnFinished(e -> statusLabel.setText(""));
        timer.play();
    }

    /**
     * Hides the network post-game action bar.
     */
    private void hidePostGameBar() {
        if (postGameBar != null) {
            postGameBar.setVisible(false);
            postGameBar.setManaged(false);
        }
    }

    /**
     * Builds a fresh board and notifies the remote peer when needed.
     *
     * @param initiator display name of the player who requested the restart
     */
    private void restartGame(String initiator) {
        hidePostGameBar();
        if (mismatchPause != null) {
            mismatchPause.stop();
            mismatchPause = null;
        }
        model = new GameModel(model.getK(), model.getN(), model.getRows(), model.getCols());
        buildBoard();
        refreshUI();
        inputLocked = false;
        gameEnded = false;
        statusLabel.setText("");
        if (mode == Mode.NETWORK_HOST) {
            if (server == null) {
                returnToMainMenu(null);
                return;
            }
            sendInitToClient();
            server.send(Protocol.make(Protocol.RESTART, initiator != null ? initiator : "Player 1"));
        }
        if (mode == Mode.NETWORK_CLIENT) {
            if (client == null) {
                returnToMainMenu(null);
                return;
            }
            client.send(Protocol.make(Protocol.RESTART, initiator != null ? initiator : "Player 2"));
        }
    }

    /**
     * Restarts a local game without a named initiator.
     */
    private void restartGame() {
        restartGame(null);
    }

    /**
     * Confirms quitting, notifies the remote peer, and returns to the menu.
     */
    @FXML
    private void onQuit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to quit?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Quit Game");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                if (mismatchPause != null) {
                    mismatchPause.stop();
                    mismatchPause = null;
                }
                if (server != null) {
                    server.send(Protocol.QUIT);
                    server.close();
                    server = null;
                }
                if (client != null) {
                    client.send(Protocol.QUIT);
                    client.close();
                    client = null;
                }
                returnToMainMenu(null);
            }
        });
    }

    /**
     * Closes network resources and loads the main-menu screen.
     *
     * @param statusMessage optional message to show on the menu
     */
    private void returnToMainMenu(String statusMessage) {
        if (server != null) {
            server.close();
            server = null;
        }
        if (client != null) {
            client.close();
            client = null;
        }
        Stage stage = primaryStage;
        if (stage == null && cardGrid.getScene() != null)
            stage = (Stage) cardGrid.getScene().getWindow();
        if (stage == null) {
            log.error("Cannot return to main menu: stage reference is null");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/memory/MemoryMenu.fxml"));
            Scene scene = new Scene(loader.load(), 700, 600);
            stage.setScene(scene);
            stage.setTitle("Multi Match Memory Game");

            if (statusMessage != null) {
                MemoryMenuController ctrl = loader.getController();
                ctrl.showTimedStatus(statusMessage, 10);
            }
        } catch (IOException e) {
            log.error("Failed to return to main menu", e);
        }
    }
}