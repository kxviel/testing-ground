package seda_project.control_alt_defeat.gamebox.controller.memory;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryProtocol;
import seda_project.control_alt_defeat.gamebox.network.memory.MemoryStateSnapshot;
import seda_project.control_alt_defeat.gamebox.ui.TimedStatus;
import seda_project.control_alt_defeat.gamebox.util.ResponsiveLayout;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.UiVisibility;

import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class GameController implements RouteDataReceiver {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private static final String MENU_ROUTE = "/memory/MemoryMenu.fxml";
    private static final String PLAYER_ONE = SafeText.PLAYER_ONE_NAME;
    private static final String PLAYER_TWO = SafeText.PLAYER_TWO_NAME;
    private static final String ACTIVE_PLAYER_CLASS = "score-active";
    private static final String CARD_BASE_CLASS = "memory-card-button";
    private static final String CARD_HIDDEN_CLASS = "card";
    private static final String CARD_FACE_UP_CLASS = "card-flipped";
    private static final String CARD_MATCHED_CLASS = "card-matched";
    private static final String CARD_MATCHED_PLAYER_ONE_CLASS = "card-matched-player-one";
    private static final String CARD_MATCHED_PLAYER_TWO_CLASS = "card-matched-player-two";
    private static final double FALLBACK_GRID_WIDTH = 804;
    private static final double FALLBACK_GRID_HEIGHT = 286;
    private static final double MAX_CARD_SIZE = 192;
    private static final double MIN_CARD_SIZE = 66;
    private static final double CARD_FACE_SCALE = 0.72;
    private static final double MIN_GAP = 10;
    private static final Duration MISMATCH_DELAY = Duration.millis(800);
    private static final int STATUS_SECONDS = 10;
    private static final double MIN_GAME_CONTENT_HEIGHT = 480;

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
    private ScrollPane gameScrollPane;
    @FXML
    private GridPane gameLayout;
    @FXML
    private StackPane boardGridWrapper;
    @FXML
    private HBox p1Chip;
    @FXML
    private HBox p2Chip;
    @FXML
    private HBox postGameBar;
    @FXML
    private Label resultLabel;
    @FXML
    private Button memoryRestartButton;

    private GameModel model;
    private Mode mode;
    private GameServer server;
    private GameClient client;
    private String playerOneName = PLAYER_ONE;
    private String playerTwoName = PLAYER_TWO;

    private Button[] cardButtons;
    private CardLayout cardLayout = new CardLayout(MAX_CARD_SIZE, MIN_GAP, 32);
    private boolean inputLocked = false;
    private boolean gameEnded = false;
    private boolean remoteQuitHandled;
    private PauseTransition mismatchPause;
    private TimedStatus timedStatus;

    @FXML
    private void initialize() {
        ResponsiveLayout.bindSidebarGrid(gameLayout, 320.0, 420.0);
        timedStatus = new TimedStatus(statusLabel);
        boardGridWrapper.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> resizeBoard());
        configureViewportFill();
    }

    private void configureViewportFill() {
        if (gameScrollPane == null || gameLayout == null) {
            return;
        }
        gameScrollPane.viewportBoundsProperty().addListener((observable, oldBounds, bounds) -> {
            fitGameLayoutToViewport(bounds);
            gameScrollPane.setVvalue(0);
        });
        Platform.runLater(() -> {
            fitGameLayoutToViewport(gameScrollPane.getViewportBounds());
            gameScrollPane.setVvalue(0);
        });
    }

    private void fitGameLayoutToViewport(Bounds viewportBounds) {
        if (viewportBounds == null || !Double.isFinite(viewportBounds.getHeight())) {
            return;
        }
        boolean compact = ResponsiveLayout.isCompact(gameLayout.getWidth());
        gameLayout.setMinHeight(Math.max(MIN_GAME_CONTENT_HEIGHT, viewportBounds.getHeight()));
        gameLayout.setPrefHeight(compact
                ? Region.USE_COMPUTED_SIZE
                : Math.max(MIN_GAME_CONTENT_HEIGHT, viewportBounds.getHeight()));
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof MemoryGameRouteData routeData) {
            init(routeData);
        }
    }

    private void init(MemoryGameRouteData routeData) {
        mode = routeData.mode();
        server = routeData.server();
        client = routeData.client();
        playerOneName = SafeText.playerName(routeData.playerOneName(), PLAYER_ONE);
        playerTwoName = SafeText.playerName(routeData.playerTwoName(), PLAYER_TWO);
        memoryRestartButton.setDisable(isNetworkClient());

        if (isNetworkClient()) {
            configureClient();
            statusLabel.setText("Connected! Waiting for host to start the game...");
            replayPendingMessages(routeData.pendingMessages(), this::processMessage);
            return;
        }

        model = new GameModel(routeData.variant());

        buildBoard();
        refreshUI();

        if (isNetworkHost()) {
            configureHost();
            statusLabel.setText("Connected. Waiting for " + playerTwoName + "'s name...");
            replayPendingMessages(routeData.pendingMessages(), this::processClientMessage);
        }
    }

    private void configureHost() {
        server.setMessageListener(msg -> Platform.runLater(() -> processClientMessage(msg)));
        server.setDisconnectListener(() -> Platform.runLater(this::handleDisconnect));
    }

    private void configureClient() {
        client.setMessageListener(msg -> Platform.runLater(() -> processMessage(msg)));
        client.setDisconnectListener(() -> Platform.runLater(this::handleDisconnect));
    }

    private void replayPendingMessages(Queue<String> pendingMessages, Consumer<String> handler) {
        if (pendingMessages == null) {
            return;
        }
        String message;
        while ((message = pendingMessages.poll()) != null) {
            handler.accept(message);
        }
    }

    private void sendStartToClient() {
        server.send(MemoryProtocol.start(playerOneName, playerTwoName, snapshot()));
    }

    private void processClientMessage(String msg) {
        if (msg == null || msg.isBlank())
            return;

        List<String> fields = MemoryProtocol.fields(msg);
        switch (MemoryProtocol.type(msg)) {
            case MemoryProtocol.JOIN -> {
                if (fields.size() != 1) {
                    return;
                }
                playerTwoName = fieldOrDefault(fields, 0, PLAYER_TWO);
                refreshUI();
                sendStartToClient();
                showTimedStatus(playerTwoName + " joined. Game started!", STATUS_SECONDS);
            }
            case MemoryProtocol.FLIP -> {
                if (fields.size() != 1) {
                    return;
                }
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
                if (fields.size() != 1) {
                    return;
                }
                String initiator = fieldOrDefault(fields, 0, PLAYER_TWO);
                if (restartGame()) {
                    showTimedStatus(initiator + " restarted the game!", STATUS_SECONDS);
                }
            }
            case MemoryProtocol.QUIT -> {
                if (fields.size() == 1) {
                    handleRemoteQuit();
                }
            }
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
            case MemoryProtocol.ERROR -> {
                if (fields.size() == 1) {
                    handleError(fields);
                }
            }
            case MemoryProtocol.QUIT -> {
                if (fields.size() == 1) {
                    handleRemoteQuit();
                }
            }
        }
    }

    private void handleStart(List<String> fields) {
        if (fields.size() != 3) {
            log.warn("Ignoring malformed START message");
            return;
        }

        playerOneName = fieldOrDefault(fields, 0, PLAYER_ONE);
        playerTwoName = fieldOrDefault(fields, 1, PLAYER_TWO);
        if (applySnapshot(fields.get(2))) {
            showTimedStatus("Game started! " + playerOneName + " (Host) goes first.", STATUS_SECONDS);
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
        memoryRestartButton.setDisable(false);
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
            memoryRestartButton.setDisable(false);

            if (gameEnded) {
                showGameOver();
            }
            return true;
        } catch (IllegalArgumentException e) {
            statusLabel.setText("Invalid network state received.");
            inputLocked = true;
            memoryRestartButton.setDisable(false);
            return false;
        }
    }

    private void handleRemoteQuit() {
        remoteQuitHandled = true;
        inputLocked = true;
        hidePostGameBar();
        returnToMainMenu(opponentName() + " left the game.");
    }

    private void handleDisconnect() {
        if (gameEnded || remoteQuitHandled)
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
        btn.getStyleClass().add(CARD_BASE_CLASS);
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
            statusLabel.setText("It's " + playerTwoName + "'s turn. Please wait.");
            return;
        }
        if (isNetworkClient() && model.getCurrentPlayer() != 1) {
            statusLabel.setText("It's " + playerOneName + "'s turn. Please wait.");
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
        p1Label.setText(playerOneName);
        p2Label.setText(playerTwoName);
        p1ScoreLabel.setText(String.valueOf(model.getScore(0)));
        p2ScoreLabel.setText(String.valueOf(model.getScore(1)));
        setActivePlayer(p1Chip, cur == 0);
        setActivePlayer(p2Chip, cur == 1);
        turnLabel.setText(playerName(cur) + "'s turn");
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
            showCardFace(btn, card.getSymbol(), CARD_MATCHED_CLASS, layout);
            setMatchedOwnerClass(btn, card.getMatchedBy());
            btn.setDisable(true);
        } else if (card.isFaceUp()) {
            showCardFace(btn, card.getSymbol(), CARD_FACE_UP_CLASS, layout);
            btn.setDisable(false);
        } else {
            showCardBack(btn, layout.fontSize());
            btn.setDisable(false);
        }
    }

    private void showCardBack(Button button, int fontSize) {
        button.setText("?");
        button.setGraphic(null);
        button.setContentDisplay(ContentDisplay.TEXT_ONLY);
        setCardState(button, CARD_HIDDEN_CLASS, fontSize);
    }

    private void showCardFace(Button button, String faceId, String stateClass, CardLayout layout) {
        double fitSize = Math.max(24.0, layout.size() * CARD_FACE_SCALE);
        ImageView face = new ImageView(cardFaceImage(faceId));
        face.getStyleClass().add("memory-card-face");
        face.setFitWidth(fitSize);
        face.setFitHeight(fitSize);
        face.setPreserveRatio(true);
        face.setSmooth(true);
        button.setText("");
        button.setGraphic(face);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setCardState(button, stateClass, layout.fontSize());
    }

    private static Image cardFaceImage(String faceId) {
        String resource = "/icons/" + faceId;
        var imageUrl = GameController.class.getResource(resource);
        if (imageUrl == null) {
            imageUrl = GameController.class.getResource("/icons/memory-card-icon.png");
        }
        if (imageUrl == null) {
            throw new IllegalStateException("Memory card fallback image is missing.");
        }
        return new Image(imageUrl.toExternalForm());
    }

    private CardLayout computeCardLayout() {
        Bounds bounds = boardGridWrapper.getLayoutBounds();
        return cardLayout(model.getRows(), model.getCols(), bounds.getWidth(), bounds.getHeight());
    }

    private static CardLayout cardLayout(int rows, int cols, double width, double height) {
        double gap = MIN_GAP;
        double widthFit = sizeFit(available(width, FALLBACK_GRID_WIDTH), cols, gap);
        double heightFit = sizeFit(available(height, FALLBACK_GRID_HEIGHT), rows, gap);
        double fit = Math.min(widthFit, heightFit);

        double size = Math.min(MAX_CARD_SIZE, fit < MIN_CARD_SIZE ? Math.max(1, fit) : fit);
        int fontSize = (int) Math.round(Math.max(10, Math.min(54, size * 0.48))) + 2;
        return new CardLayout(size, gap, fontSize);
    }

    private static double available(double value, double fallback) {
        return Math.max(1, value > 0 ? value : fallback);
    }

    private static double sizeFit(double available, int count, double gap) {
        return (available - gap * Math.max(0, count - 1)) / count;
    }

    private record CardLayout(double size, double gap, int fontSize) {
    }

    private void setCardState(Button button, String stateClass, int fontSize) {
        button.getStyleClass().removeAll(
                CARD_HIDDEN_CLASS,
                CARD_FACE_UP_CLASS,
                CARD_MATCHED_CLASS,
                CARD_MATCHED_PLAYER_ONE_CLASS,
                CARD_MATCHED_PLAYER_TWO_CLASS);
        button.getStyleClass().add(stateClass);
        button.setStyle("-fx-font-size:" + fontSize + "px;");
    }

    private void setMatchedOwnerClass(Button button, int matchedBy) {
        if (matchedBy == 0) {
            button.getStyleClass().add(CARD_MATCHED_PLAYER_ONE_CLASS);
        } else if (matchedBy == 1) {
            button.getStyleClass().add(CARD_MATCHED_PLAYER_TWO_CLASS);
        }
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
                : playerName(winner) + " Wins! 🎉";
        String scores = playerOneName + ": " + model.getScore(0)
                + "  |  " + playerTwoName + ": " + model.getScore(1);

        if (mode == Mode.LOCAL) {
            showLocalGameOver(resultText, scores);
        } else {
            resultLabel.setText(resultText + "   " + scores);
            UiVisibility.setVisibleManaged(postGameBar, true);
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
            if (btn == restartBtn) {
                restartGame();
            } else {
                returnToMainMenu(null);
            }
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
            if (model == null) {
                statusLabel.setText("Wait for the game to start before restarting.");
                return;
            }
            if (client == null || !client.isConnected()) {
                inputLocked = false;
                memoryRestartButton.setDisable(false);
                statusLabel.setText("Cannot restart because the host is disconnected.");
                return;
            }
            inputLocked = true;
            memoryRestartButton.setDisable(true);
            client.send(MemoryProtocol.restartRequest(myName));
            showTimedStatus("Restart requested. Waiting for host...", STATUS_SECONDS);
        } else if (restartGame()) {
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
        timedStatus.show(message, seconds);
    }

    private void hidePostGameBar() {
        UiVisibility.setVisibleManaged(postGameBar, false);
    }

    private boolean restartGame() {
        if (model == null) {
            statusLabel.setText("The game is not ready to restart.");
            return false;
        }

        hidePostGameBar();
        stopMismatchPause();
        inputLocked = false;
        gameEnded = false;
        remoteQuitHandled = false;
        model = new GameModel(model.getK(), model.getN(), model.getRows(), model.getCols());
        buildBoard();
        refreshAll();
        memoryRestartButton.setDisable(false);
        statusLabel.setText("");
        resetGameViewport();

        if (isNetworkHost()) {
            if (server == null) {
                returnToMainMenu(null);
                return false;
            }
            server.send(MemoryProtocol.restartState(snapshot()));
        }
        return true;
    }

    private void resetGameViewport() {
        gameScrollPane.setHvalue(0.0);
        gameScrollPane.setVvalue(0.0);
        Platform.runLater(() -> {
            gameScrollPane.setHvalue(0.0);
            gameScrollPane.setVvalue(0.0);
        });
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
    }

    private void sendQuit() {
        if (isNetworkHost() && server != null) {
            server.closeAfterSending(MemoryProtocol.quit(playerOneName));
            server = null;
        }
        if (isNetworkClient() && client != null) {
            client.closeAfterSending(MemoryProtocol.quit(playerTwoName));
            client = null;
        }
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
        return isNetworkHost() ? playerOneName : playerTwoName;
    }

    private String opponentName() {
        return isNetworkHost() ? playerTwoName : playerOneName;
    }

    private String playerName(int player) {
        return player == 0 ? playerOneName : playerTwoName;
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
            log.warn("Ignoring malformed {}: {}", source,
                    SafeText.singleLine(fields.getFirst(), "invalid value", 64));
            return null;
        }
    }

    private static String fieldOrDefault(List<String> fields, int index, String fallback) {
        return SafeText.playerName(fields != null && fields.size() > index ? fields.get(index) : null, fallback);
    }
}
