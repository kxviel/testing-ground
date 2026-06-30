package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.Protocol;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;

public class GameController implements RouteDataReceiver {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private static final String MENU_ROUTE = "/memory/MemoryMenu.fxml";
    private static final String PLAYER_ONE = "Player 1";
    private static final String PLAYER_TWO = "Player 2";
    private static final String CARD_BACK_ICON = "/icons/icons8-question-100.png";
    private static final String ACTIVE_PLAYER_CLASS = "active-player";
    private static final String CARD_HIDDEN_CLASS = "memory-card-hidden";
    private static final String CARD_FACE_UP_CLASS = "memory-card-face-up";
    private static final String CARD_MATCHED_CLASS = "memory-card-matched";
    private static final double MAX_GRID_WIDTH = 600;
    private static final double MAX_GRID_HEIGHT = 270;
    private static final double MIN_CARD_SIZE = 36;
    private static final double MAX_CARD_SIZE = 76;
    private static final Duration MISMATCH_DELAY = Duration.millis(800);
    private static final int STATUS_SECONDS = 10;

    public enum Mode {
        LOCAL, NETWORK_HOST, NETWORK_CLIENT
    }

    @FXML
    private Label p1Label;
    @FXML
    private Label p2Label;
    @FXML
    private Label p1ScoreLabel;
    @FXML
    private Label p2ScoreLabel;
    @FXML
    private Label turnLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private GridPane cardGrid;
    @FXML
    private HBox p1Chip;
    @FXML
    private HBox p2Chip;
    @FXML
    private HBox postGameBar;
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
    private Image cardBackImage;
    private boolean inputLocked = false;
    private boolean gameEnded = false;
    private PauseTransition mismatchPause;

    @Override
    public void setRouteData(Object data) {
        if (data instanceof MemoryGameRouteData routeData) {
            init(routeData.variant(), routeData.mode(), routeData.server(), routeData.client());
            replayPendingMessages(routeData.pendingMessages());
        }
    }

    private void init(BoardVariant variant, Mode mode,
            GameServer server, GameClient client) {
        this.mode = mode;
        this.server = server;
        this.client = client;

        if (isNetworkClient()) {
            configureClient();
            statusLabel.setText("Connected! Waiting for host to start the game...");
            return;
        }

        this.model = new GameModel(variant);

        if (isNetworkHost()) {
            configureHost();
            sendInitToClient();
        }

        buildBoard();
        refreshUI();
    }

    private void configureHost() {
        server.setMessageListener(msg -> Platform.runLater(() -> processClientMessage(msg)));
        server.setDisconnectListener(() -> Platform.runLater(this::handleDisconnect));
    }

    private void configureClient() {
        client.setMessageListener(msg -> Platform.runLater(() -> processMessage(msg)));
        client.setDisconnectListener(() -> Platform.runLater(this::handleDisconnect));
    }

    private void replayPendingMessages(Queue<String> pendingMessages) {
        if (pendingMessages == null) {
            return;
        }
        String message;
        while ((message = pendingMessages.poll()) != null) {
            processMessage(message);
        }
    }

    private void sendInitToClient() {
        server.send(Protocol.make(Protocol.INIT,
                String.valueOf(model.getK()),
                String.valueOf(model.getN()),
                String.valueOf(model.getRows()),
                String.valueOf(model.getCols()),
                String.join(",", model.getSymbolOrder())));
    }

    private void processClientMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;

        String[] parts = msg.split(":", -1);
        switch (parts[0]) {
            case Protocol.FLIP -> {
                Integer idx = parseCardIndex(parts, "FLIP from client");
                if (idx != null && model.getCurrentPlayer() == 1) {
                    executeCardSelect(idx);
                }
            }
            case Protocol.RESTART -> {
                String initiator = fieldOrDefault(parts, 1, PLAYER_TWO);
                restartGame(initiator);
                showTimedStatus(initiator + " restarted the game!", STATUS_SECONDS);
            }
            case Protocol.QUIT -> handleRemoteQuit();
        }
    }

    private void processMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;

        String[] parts = msg.split(":", -1);
        switch (parts[0]) {
            case Protocol.INIT -> handleInit(parts);
            case Protocol.FLIP -> handleRemoteFlip(parts);
            case Protocol.CLOSE -> handleRemoteClose();
            case Protocol.GAMEOVER -> handleGameOverMsg();
            case Protocol.RESTART -> handleRestart(parts);
            case Protocol.QUIT -> handleRemoteQuit();
        }
    }

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
            showTimedStatus("Game started! " + PLAYER_ONE + " (Host) goes first.", STATUS_SECONDS);
        }
    }

    private void handleRemoteFlip(String[] parts) {
        Integer idx = parseCardIndex(parts, "FLIP message");
        if (idx == null)
            return;

        handleSelectionResult(idx, model.selectCard(idx), "Mismatch! Closing cards...");
    }

    private void handleRemoteClose() {
        model.closeOpenCards();
        refreshAll();
        inputLocked = false;
        statusLabel.setText("");
    }

    private void handleGameOverMsg() {
        if (gameEnded)
            return;
        gameEnded = true;
        inputLocked = true;
        showGameOver();
    }

    private void handleRestart(String[] parts) {
        if (isNetworkClient()) {
            String initiator = fieldOrDefault(parts, 1, PLAYER_ONE);
            showTimedStatus(initiator + " restarted the game!", STATUS_SECONDS);
            return;
        }

        inputLocked = false;
        gameEnded = false;
    }

    private void handleRemoteQuit() {
        inputLocked = true;
        hidePostGameBar();
        returnToMainMenu(opponentName() + " left the game.");
    }

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

    private void buildBoard() {
        cardGrid.getChildren().clear();
        int total = model.getRows() * model.getCols();
        cardButtons = new Button[total];
        CardLayout cardLayout = cardLayout(model.getRows(), model.getCols());

        cardGrid.setHgap(cardLayout.gap());
        cardGrid.setVgap(cardLayout.gap());

        IntStream.range(0, total)
                .forEach(i -> addCardButton(i, cardLayout.fontSize(), cardLayout.size()));
    }

    private void addCardButton(int idx, int fontSize, double btnSize) {
        Button btn = new Button();
        btn.setId("card_" + idx);
        btn.getStyleClass().add("memory-card");
        btn.setMinWidth(btnSize);
        btn.setMinHeight(btnSize);
        btn.setPrefWidth(btnSize);
        btn.setPrefHeight(btnSize);
        btn.setMaxWidth(btnSize);
        btn.setMaxHeight(btnSize);
        btn.setFocusTraversable(false);
        showCardBack(btn, fontSize);
        btn.setOnAction(e -> onCardClick(idx));
        cardGrid.add(btn, idx % model.getCols(), idx / model.getCols());
        cardButtons[idx] = btn;
    }

    private void onCardClick(int idx) {
        if (inputLocked || gameEnded)
            return;

        if (isNetworkHost() && model.getCurrentPlayer() != 0) {
            statusLabel.setText("It's " + PLAYER_TWO + "'s turn. Please wait.");
            return;
        }
        if (isNetworkClient() && model.getCurrentPlayer() != 1) {
            statusLabel.setText("It's " + PLAYER_ONE + "'s turn. Please wait.");
            return;
        }

        Card card = model.getCard(idx);
        if (!card.isSelectable())
            return;

        if (isNetworkClient()) {
            client.send(Protocol.make(Protocol.FLIP, String.valueOf(idx)));
            handleSelectionResult(idx, model.selectCard(idx), "Mismatch! Waiting for host...");
        } else {
            executeCardSelect(idx);
        }
    }

    private void executeCardSelect(int idx) {
        Card card = model.getCard(idx);
        if (!card.isSelectable())
            return;

        GameModel.SelectResult result = model.selectCard(idx);
        if (result == GameModel.SelectResult.IGNORED)
            return;

        updateCardButton(idx);

        if (isNetworkHost()) {
            server.send(Protocol.make(Protocol.FLIP, String.valueOf(idx)));
        }
        refreshUI();

        switch (result) {
            case RESOLVED_MATCH -> handleResolvedMatch();
            case RESOLVED_MISMATCH -> showMismatchThenClose();
            case IGNORED, OPENED -> {
            }
        }
    }

    private void handleSelectionResult(int idx, GameModel.SelectResult result, String mismatchMessage) {
        switch (result) {
            case IGNORED -> {
            }
            case OPENED -> {
                updateCardButton(idx);
                refreshUI();
            }
            case RESOLVED_MATCH -> handleResolvedMatch();
            case RESOLVED_MISMATCH -> {
                updateCardButton(idx);
                refreshUI();
                inputLocked = true;
                statusLabel.setText(mismatchMessage);
            }
        }
    }

    private void handleResolvedMatch() {
        refreshAll();
        if (!model.isGameOver())
            return;

        if (isNetworkHost()) {
            server.send(Protocol.make(Protocol.GAMEOVER,
                    String.valueOf(model.getWinner()),
                    String.valueOf(model.getScore(0)),
                    String.valueOf(model.getScore(1))));
        }
        showGameOver();
    }

    private void showMismatchThenClose() {
        inputLocked = true;
        statusLabel.setText("Mismatch! Closing cards...");
        stopMismatchPause();

        mismatchPause = new PauseTransition(MISMATCH_DELAY);
        mismatchPause.setOnFinished(e -> {
            model.closeOpenCards();
            refreshAll();
            inputLocked = false;
            statusLabel.setText("");
            if (isNetworkHost() && server != null)
                server.send(Protocol.CLOSE);
        });
        mismatchPause.play();
    }

    private void refreshUI() {
        int cur = model.getCurrentPlayer();
        p1Label.setText(PLAYER_ONE);
        p2Label.setText(PLAYER_TWO);
        p1ScoreLabel.setText(String.valueOf(model.getScore(0)));
        p2ScoreLabel.setText(String.valueOf(model.getScore(1)));
        setActivePlayer(p1Chip, cur == 0);
        setActivePlayer(p2Chip, cur == 1);
        turnLabel.setText((cur == 0 ? PLAYER_ONE : PLAYER_TWO) + "'s Turn");
    }

    private void refreshAll() {
        if (cardButtons == null)
            return;
        IntStream.range(0, cardButtons.length).forEach(this::updateCardButton);
        refreshUI();
    }

    private void updateCardButton(int idx) {
        if (cardButtons == null || idx >= cardButtons.length)
            return;
        Card card = model.getCard(idx);
        Button btn = cardButtons[idx];
        int fontSize = cardLayout(model.getRows(), model.getCols()).fontSize();
        if (card.isMatched()) {
            showCardFace(btn, card.getSymbol(), CARD_MATCHED_CLASS, fontSize);
            btn.setDisable(true);
        } else if (card.isFaceUp()) {
            showCardFace(btn, card.getSymbol(), CARD_FACE_UP_CLASS, fontSize);
            btn.setDisable(false);
        } else {
            showCardBack(btn, fontSize);
            btn.setDisable(false);
        }
    }

    private void showCardBack(Button button, int fontSize) {
        button.setText("");
        button.setGraphic(cardBackGraphic(button));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setCardState(button, CARD_HIDDEN_CLASS, fontSize);
    }

    private void showCardFace(Button button, String text, String stateClass, int fontSize) {
        button.setGraphic(null);
        button.setText(text);
        button.setContentDisplay(ContentDisplay.TEXT_ONLY);
        setCardState(button, stateClass, fontSize);
    }

    private ImageView cardBackGraphic(Button button) {
        ImageView imageView = new ImageView(cardBackImage());
        double imageSize = Math.max(22, Math.min(button.getPrefWidth(), button.getPrefHeight()) * 0.58);
        imageView.setFitWidth(imageSize);
        imageView.setFitHeight(imageSize);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    private Image cardBackImage() {
        if (cardBackImage == null) {
            var iconUrl = GameController.class.getResource(CARD_BACK_ICON);
            if (iconUrl == null) {
                throw new IllegalStateException("Missing card back icon: " + CARD_BACK_ICON);
            }
            cardBackImage = new Image(iconUrl.toExternalForm());
        }
        return cardBackImage;
    }

    private static CardLayout cardLayout(int rows, int cols) {
        double gap = rows * cols > 30 ? 8 : (rows * cols > 16 ? 10 : 12);
        double widthFit = (MAX_GRID_WIDTH - gap * Math.max(0, cols - 1)) / cols;
        double heightFit = (MAX_GRID_HEIGHT - gap * Math.max(0, rows - 1)) / rows;
        double size = Math.max(MIN_CARD_SIZE, Math.min(MAX_CARD_SIZE, Math.min(widthFit, heightFit)));
        int fontSize = (int) Math.max(18, Math.min(32, Math.round(size * 0.48)));
        return new CardLayout(size, gap, fontSize);
    }

    private record CardLayout(double size, double gap, int fontSize) {
    }

    private void setCardState(Button button, String stateClass, int fontSize) {
        button.getStyleClass().removeAll(CARD_HIDDEN_CLASS, CARD_FACE_UP_CLASS, CARD_MATCHED_CLASS);
        button.getStyleClass().add(stateClass);
        button.setStyle("-fx-font-size:" + fontSize + "px;");
    }

    private void setActivePlayer(HBox chip, boolean active) {
        if (active && !chip.getStyleClass().contains(ACTIVE_PLAYER_CLASS)) {
            chip.getStyleClass().add(ACTIVE_PLAYER_CLASS);
        } else if (!active) {
            chip.getStyleClass().remove(ACTIVE_PLAYER_CLASS);
        }
    }

    private void showGameOver() {
        gameEnded = true;
        inputLocked = true;
        int winner = model.getWinner();
        String resultText = winner < 0 ? "It's a Draw! 🤝"
                : "Player " + (winner + 1) + " Wins! 🎉";
        String scores = PLAYER_ONE + ": " + model.getScore(0)
                + "  |  " + PLAYER_TWO + ": " + model.getScore(1);

        if (mode == Mode.LOCAL) {
            showLocalGameOver(resultText, scores);
        } else {
            resultLabel.setText(resultText + "   " + scores);
            postGameBar.setVisible(true);
            postGameBar.setManaged(true);
        }
    }

    private void showLocalGameOver(String resultText, String scores) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        ButtonType restartBtn = new ButtonType("Play Again", ButtonBar.ButtonData.YES);
        ButtonType menuBtn = new ButtonType("Main Menu", ButtonBar.ButtonData.NO);
        alert.setTitle("Game Over");
        alert.setHeaderText(resultText);
        alert.setContentText(scores);
        alert.getButtonTypes().setAll(restartBtn, menuBtn);
        alert.showAndWait().ifPresent(btn -> {
            if (btn == restartBtn)
                restartGame();
            else
                returnToMainMenu(null);
        });
    }

    @FXML
    private void onPostPlayAgain() {
        String myName = playerName();
        hidePostGameBar();
        restartGame(myName);
        showTimedStatus(myName + " restarted the game!", STATUS_SECONDS);
    }

    @FXML
    private void onPostMainMenu() {
        String myName = playerName();
        hidePostGameBar();
        sendQuit();
        returnToMainMenu(myName + " left the game.");
    }

    private void showTimedStatus(String message, int seconds) {
        statusLabel.setText(message);
        PauseTransition timer = new PauseTransition(Duration.seconds(seconds));
        timer.setOnFinished(e -> statusLabel.setText(""));
        timer.play();
    }

    private void hidePostGameBar() {
        if (postGameBar != null) {
            postGameBar.setVisible(false);
            postGameBar.setManaged(false);
        }
    }

    private void restartGame(String initiator) {
        hidePostGameBar();
        stopMismatchPause();
        model = new GameModel(model.getK(), model.getN(), model.getRows(), model.getCols());
        buildBoard();
        refreshUI();
        inputLocked = false;
        gameEnded = false;
        statusLabel.setText("");
        if (isNetworkHost()) {
            if (server == null) {
                returnToMainMenu(null);
                return;
            }
            sendInitToClient();
            server.send(Protocol.make(Protocol.RESTART, initiator != null ? initiator : PLAYER_ONE));
        }
        if (isNetworkClient()) {
            if (client == null) {
                returnToMainMenu(null);
                return;
            }
            client.send(Protocol.make(Protocol.RESTART, initiator != null ? initiator : PLAYER_TWO));
        }
    }

    private void restartGame() {
        restartGame(null);
    }

    @FXML
    private void onQuit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to quit?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Quit Game");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                stopMismatchPause();
                sendQuit();
                returnToMainMenu(null);
            }
        });
    }

    private void returnToMainMenu(String statusMessage) {
        closeNetwork();
        Stage stage = currentStage();
        if (stage == null) {
            log.error("Cannot return to main menu: stage reference is null");
            return;
        }
        Router.goTo(stage, MENU_ROUTE, statusMessage);
        stage.setTitle("Multi Match Memory Game");
    }

    private void sendQuit() {
        if (isNetworkHost() && server != null)
            server.send(Protocol.QUIT);
        if (isNetworkClient() && client != null)
            client.send(Protocol.QUIT);
    }

    private void closeNetwork() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void stopMismatchPause() {
        if (mismatchPause != null) {
            mismatchPause.stop();
            mismatchPause = null;
        }
    }

    private String playerName() {
        return isNetworkHost() ? PLAYER_ONE : PLAYER_TWO;
    }

    private String opponentName() {
        return isNetworkHost() ? PLAYER_TWO : PLAYER_ONE;
    }

    private boolean isNetworkHost() {
        return mode == Mode.NETWORK_HOST;
    }

    private boolean isNetworkClient() {
        return mode == Mode.NETWORK_CLIENT;
    }

    private Stage currentStage() {
        if (cardGrid.getScene() == null) {
            return null;
        }
        return (Stage) cardGrid.getScene().getWindow();
    }

    private Integer parseCardIndex(String[] parts, String source) {
        if (parts.length <= 1) {
            log.warn("Ignoring malformed {}: missing card index", source);
            return null;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("Ignoring malformed {}: {}", source, parts[1]);
            return null;
        }
    }

    private static String fieldOrDefault(String[] parts, int index, String fallback) {
        return parts != null && parts.length > index && !parts[index].isBlank()
                ? parts[index]
                : fallback;
    }
}
