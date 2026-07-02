package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.util.Duration;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
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
    private static final String CARD_SYMBOL_CLASS = "memory-card-symbol";
    private static final double FALLBACK_GRID_WIDTH = 1350;
    private static final double FALLBACK_GRID_HEIGHT = 608;
    private static final double MAX_CARD_SIZE = 171;
    private static final double MIN_CARD_SIZE = 34;
    private static final double GRID_INSET = 12;
    private static final double MIN_GAP = 8;
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
    private ScrollPane memoryScroll;
    @FXML
    private HBox p1Chip;
    @FXML
    private HBox p2Chip;
    @FXML
    private HBox postGameBar;
    @FXML
    private Label resultLabel;

    private GameModel model;
    private Mode mode;
    private GameServer server;
    private GameClient client;

    private Button[] cardButtons;
    private Image cardBackImage;
    private CardLayout cardLayout = new CardLayout(MAX_CARD_SIZE, 18, 48);
    private boolean inputLocked = false;
    private boolean gameEnded = false;
    private PauseTransition mismatchPause;
    private PauseTransition statusTimer;

    @FXML
    private void initialize() {
        memoryScroll.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> resizeBoard());
    }

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
            sendStartToClient();
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

    private void sendStartToClient() {
        server.send(MemoryProtocol.start(PLAYER_ONE, PLAYER_TWO, snapshot()));
    }

    private void processClientMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;

        List<String> fields = MemoryProtocol.fields(msg);
        switch (MemoryProtocol.type(msg)) {
            case MemoryProtocol.JOIN -> sendStartToClient();
            case MemoryProtocol.FLIP -> {
                Integer idx = parseCardIndex(fields, "FLIP from client");
                if (idx == null) {
                    return;
                }
                if (model.getCurrentPlayer() != 1) {
                    server.send(MemoryProtocol.error("It is not your turn."));
                    return;
                }
                executeCardSelect(idx);
            }
            case MemoryProtocol.RESTART_REQUEST -> {
                String initiator = fieldOrDefault(fields, 0, PLAYER_TWO);
                restartGame();
                showTimedStatus(initiator + " restarted the game!", STATUS_SECONDS);
            }
            case MemoryProtocol.QUIT -> handleRemoteQuit();
        }
    }

    private void processMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;

        List<String> fields = MemoryProtocol.fields(msg);
        switch (MemoryProtocol.type(msg)) {
            case MemoryProtocol.START -> handleStart(fields);
            case MemoryProtocol.STATE -> handleState(fields);
            case MemoryProtocol.RESTART_STATE -> handleRestartState(fields);
            case MemoryProtocol.ERROR -> handleError(fields);
            case MemoryProtocol.QUIT -> handleRemoteQuit();
        }
    }

    private void handleStart(List<String> fields) {
        if (fields.size() != 3) {
            log.warn("Ignoring malformed START message");
            return;
        }

        if (applySnapshot(fields.get(2))) {
            showTimedStatus("Game started! " + PLAYER_ONE + " (Host) goes first.", STATUS_SECONDS);
        }
    }

    private boolean handleState(List<String> fields) {
        if (fields.size() != 1) {
            log.warn("Ignoring malformed STATE message");
            return false;
        }
        return applySnapshot(fields.getFirst());
    }

    private void handleRestartState(List<String> fields) {
        if (handleState(fields)) {
            showTimedStatus("Game restarted!", STATUS_SECONDS);
        }
    }

    private void handleError(List<String> fields) {
        statusLabel.setText(fieldOrDefault(fields, 0, "Network error."));
        inputLocked = false;
    }

    private boolean applySnapshot(String snapshot) {
        try {
            GameModel nextModel = MemoryStateSnapshot.deserialize(snapshot);
            boolean rebuild = model == null
                    || cardButtons == null
                    || model.getRows() != nextModel.getRows()
                    || model.getCols() != nextModel.getCols();

            model = nextModel;
            gameEnded = model.isGameOver();
            inputLocked = gameEnded || hasPendingMismatch();
            hidePostGameBar();

            if (rebuild) {
                buildBoard();
            }
            refreshAll();

            if (gameEnded) {
                showGameOver();
            }
            return true;
        } catch (IllegalArgumentException e) {
            statusLabel.setText("Invalid network state received.");
            inputLocked = true;
            return false;
        }
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
        cardLayout = computeCardLayout();

        cardGrid.setHgap(cardLayout.gap());
        cardGrid.setVgap(cardLayout.gap());

        IntStream.range(0, total)
                .forEach(this::addCardButton);
        Platform.runLater(this::resizeBoard);
    }

    private void addCardButton(int idx) {
        Button btn = new Button();
        btn.setId("card_" + idx);
        btn.getStyleClass().add("memory-card");
        setCardSize(btn, cardLayout.size());
        btn.setFocusTraversable(false);
        showCardBack(btn, cardLayout.fontSize());
        btn.setOnAction(e -> onCardClick(idx));
        cardGrid.add(btn, idx % model.getCols(), idx / model.getCols());
        cardButtons[idx] = btn;
    }

    private void resizeBoard() {
        if (model == null || cardButtons == null)
            return;

        cardLayout = computeCardLayout();
        cardGrid.setHgap(cardLayout.gap());
        cardGrid.setVgap(cardLayout.gap());
        IntStream.range(0, cardButtons.length).forEach(i -> {
            setCardSize(cardButtons[i], cardLayout.size());
            updateCardButton(i, cardLayout);
        });
    }

    private static void setCardSize(Button button, double size) {
        button.setMinSize(size, size);
        button.setPrefSize(size, size);
        button.setMaxSize(size, size);
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
            inputLocked = true;
            statusLabel.setText("Move sent. Waiting for host...");
            client.send(MemoryProtocol.flip(idx));
        } else {
            executeCardSelect(idx);
        }
    }

    private void executeCardSelect(int idx) {
        if (idx < 0 || idx >= model.getCards().size()) {
            if (isNetworkHost()) {
                server.send(MemoryProtocol.error("Invalid card."));
            }
            return;
        }

        Card card = model.getCard(idx);
        if (!card.isSelectable()) {
            broadcastStateIfHost();
            return;
        }

        GameModel.SelectResult result = model.selectCard(idx);
        if (result == GameModel.SelectResult.IGNORED)
            return;

        updateCardButton(idx);
        refreshUI();

        switch (result) {
            case OPENED -> broadcastStateIfHost();
            case RESOLVED_MATCH -> {
                handleResolvedMatch();
                broadcastStateIfHost();
            }
            case RESOLVED_MISMATCH -> {
                broadcastStateIfHost();
                showMismatchThenClose();
            }
            case IGNORED -> {
            }
        }
    }

    private void handleResolvedMatch() {
        refreshAll();
        if (!model.isGameOver())
            return;
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
            broadcastStateIfHost();
        });
        mismatchPause.play();
    }

    private void broadcastStateIfHost() {
        if (isNetworkHost() && server != null && server.isConnected()) {
            server.send(MemoryProtocol.state(snapshot()));
        }
    }

    private String snapshot() {
        return MemoryStateSnapshot.serialize(model);
    }

    private boolean hasPendingMismatch() {
        List<Card> openCards = model.getCards().stream()
                .filter(card -> card.isFaceUp() && !card.isMatched())
                .toList();

        if (openCards.size() < model.getK()) {
            return false;
        }

        String symbol = openCards.getFirst().getSymbol();
        return openCards.stream()
                .map(Card::getSymbol)
                .anyMatch(cardSymbol -> !symbol.equals(cardSymbol));
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
        updateCardButton(idx, cardLayout);
    }

    private void updateCardButton(int idx, CardLayout layout) {
        Card card = model.getCard(idx);
        Button btn = cardButtons[idx];
        if (card.isMatched()) {
            showCardFace(btn, card.getSymbol(), CARD_MATCHED_CLASS, layout.fontSize());
            btn.setDisable(true);
        } else if (card.isFaceUp()) {
            showCardFace(btn, card.getSymbol(), CARD_FACE_UP_CLASS, layout.fontSize());
            btn.setDisable(false);
        } else {
            showCardBack(btn, layout.fontSize());
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
        Label symbol = new Label(text);
        symbol.getStyleClass().add(CARD_SYMBOL_CLASS);
        symbol.setFont(Font.font("Segoe UI Emoji", fontSize));
        symbol.setStyle("-fx-font-size:" + fontSize + "px;");
        button.setText("");
        button.setGraphic(symbol);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setCardState(button, stateClass, fontSize);
    }

    private ImageView cardBackGraphic(Button button) {
        ImageView imageView = new ImageView(cardBackImage());
        double imageSize = Math.max(8, Math.min(button.getPrefWidth(), button.getPrefHeight()) * 0.58);
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

    private CardLayout computeCardLayout() {
        Bounds bounds = memoryScroll.getViewportBounds();
        return cardLayout(model.getRows(), model.getCols(), bounds.getWidth(), bounds.getHeight());
    }

    private static CardLayout cardLayout(int rows, int cols, double width, double height) {
        double gap = targetGap(rows * cols);
        double widthFit = sizeFit(available(width, FALLBACK_GRID_WIDTH), cols, gap);
        double heightFit = sizeFit(available(height, FALLBACK_GRID_HEIGHT), rows, gap);
        double fit = Math.min(widthFit, heightFit);

        if (fit < MIN_CARD_SIZE) {
            gap = MIN_GAP;
            widthFit = sizeFit(available(width, FALLBACK_GRID_WIDTH), cols, gap);
            heightFit = sizeFit(available(height, FALLBACK_GRID_HEIGHT), rows, gap);
            fit = Math.min(widthFit, heightFit);
        }

        double size = Math.min(MAX_CARD_SIZE, fit < MIN_CARD_SIZE ? Math.max(1, fit) : fit);
        int fontSize = (int) Math.round(Math.max(10, Math.min(54, size * 0.48)));
        return new CardLayout(size, gap, fontSize);
    }

    private static double targetGap(int total) {
        return total > 30 ? 18 : (total > 16 ? 22 : 26);
    }

    private static double available(double value, double fallback) {
        return Math.max(1, (value > 0 ? value : fallback) - GRID_INSET * 2);
    }

    private static double sizeFit(double available, int count, double gap) {
        return (available - gap * Math.max(0, count - 1)) / count;
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
        hidePostGameBar();
        requestRestart();
    }

    @FXML
    private void onRestart() {
        requestRestart();
    }

    private void requestRestart() {
        String myName = playerName();
        if (isNetworkClient()) {
            inputLocked = true;
            client.send(MemoryProtocol.restartRequest(myName));
            showTimedStatus("Restart requested. Waiting for host...", STATUS_SECONDS);
        } else {
            restartGame();
            showTimedStatus(myName + " restarted the game!", STATUS_SECONDS);
        }
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

    private void hidePostGameBar() {
        if (postGameBar != null) {
            postGameBar.setVisible(false);
            postGameBar.setManaged(false);
        }
    }

    private void restartGame() {
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
            server.send(MemoryProtocol.restartState(snapshot()));
        }
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
            server.send(MemoryProtocol.quit(PLAYER_ONE));
        if (isNetworkClient() && client != null)
            client.send(MemoryProtocol.quit(PLAYER_TWO));
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

    private Integer parseCardIndex(List<String> fields, String source) {
        if (fields.isEmpty()) {
            log.warn("Ignoring malformed {}: missing card index", source);
            return null;
        }
        try {
            return Integer.parseInt(fields.getFirst());
        } catch (NumberFormatException e) {
            log.warn("Ignoring malformed {}: {}", source, fields.getFirst());
            return null;
        }
    }

    private static String fieldOrDefault(List<String> fields, int index, String fallback) {
        return fields != null && fields.size() > index && !fields.get(index).isBlank()
                ? fields.get(index)
                : fallback;
    }
}
