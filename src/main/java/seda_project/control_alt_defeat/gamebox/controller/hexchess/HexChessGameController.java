package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoardGeometry;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessBot;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameStatus;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessProtocol;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.List;
import java.util.Optional;

public class HexChessGameController implements RouteDataReceiver {

    private static final double HEX_SIZE = 37.4;
    private static final double BOARD_WIDTH = 1122.0;
    private static final double BOARD_HEIGHT = 952.0;
    private static final double BOARD_FIT_MARGIN = 24.0;
    private static final Duration BOT_DELAY = Duration.millis(350);
    private static final Duration BOT_DRAW_DECLINE_DELAY = Duration.millis(800);
    private static final Color CELL_LIGHT = Color.web("#f7c895");
    private static final Color CELL_MID = Color.web("#e5aa68");
    private static final Color CELL_DARK = Color.web("#cf873d");
    private static final Color CELL_LEGAL = Color.web("#9fd3b5");
    private static final Color CELL_CHECK = Color.web("#f87171");
    private static final Color STROKE_BASE = Color.web("#6b4a28");
    private static final Color STROKE_SELECTED = Color.web("#0f62fe");
    private static final Color STROKE_LAST = Color.web("#7c3aed");
    private static final Color NOTATION_COLOR = Color.rgb(23, 23, 23, 0.45);

    @FXML
    private StackPane boardZone;
    @FXML
    private Canvas boardCanvas;
    @FXML
    private Label modeLabel;
    @FXML
    private Label turnLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label lastMoveLabel;
    @FXML
    private Label drawOfferLabel;
    @FXML
    private Button offerDrawButton;
    @FXML
    private Button resignButton;
    @FXML
    private Button restartButton;
    @FXML
    private Button acceptDrawButton;
    @FXML
    private Button declineDrawButton;

    private final HexChessCanvasBoard canvasBoard = new HexChessCanvasBoard(
            HEX_SIZE,
            BOARD_WIDTH,
            BOARD_HEIGHT,
            19,
            43);

    private HexChessGameSetup setup = HexChessGameSetup.local();
    private HexGameState gameState = HexGameState.standard();
    private HexCoordinate selectedCoordinate;
    private List<HexMove> selectedMoves = List.of();
    private GameServer hostServer;
    private GameClient joinClient;
    private boolean botThinking;

    @FXML
    public void initialize() {
        bindOptionalLabel(statusLabel);
        bindOptionalLabel(drawOfferLabel);
        bindBoardScale();
        startGame(setup);
    }

    private void bindBoardScale() {
        if (boardCanvas == null || boardZone == null) {
            return;
        }

        boardCanvas.scaleXProperty().bind(Bindings.createDoubleBinding(
                this::boardScale,
                boardZone.widthProperty(),
                boardZone.heightProperty()));
        boardCanvas.scaleYProperty().bind(boardCanvas.scaleXProperty());
    }

    private double boardScale() {
        double availableWidth = boardZone.getWidth() - BOARD_FIT_MARGIN;
        double availableHeight = boardZone.getHeight() - BOARD_FIT_MARGIN;
        if (availableWidth <= 0 || availableHeight <= 0) {
            return 1.0;
        }

        double fit = Math.min(availableWidth / BOARD_WIDTH, availableHeight / BOARD_HEIGHT);
        return Math.min(1.0, Math.max(0.35, fit));
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof HexChessGameRouteData routeData) {
            hostServer = routeData.server();
            joinClient = routeData.client();
            startGame(routeData.setup());
            configureNetwork();
        } else if (data instanceof HexChessGameSetup nextSetup) {
            startGame(nextSetup);
        }
    }

    @FXML
    private void onOfferDraw() {
        if (isNetworkClient()) {
            joinClient.send(HexChessProtocol.simple(HexChessProtocol.DRAW_OFFER));
            gameState = gameState.withStatusMessage("Draw offer sent.");
            render();
            return;
        }

        applyAndBroadcast(gameState.offerDraw(controlledColor()));
        maybeBotDeclineDrawOffer();
    }

    @FXML
    private void onAcceptDraw() {
        if (isNetworkClient()) {
            joinClient.send(HexChessProtocol.simple(HexChessProtocol.DRAW_ACCEPT));
            return;
        }

        applyAndBroadcast(gameState.acceptDraw(drawResponderColor()));
    }

    @FXML
    private void onDeclineDraw() {
        if (isNetworkClient()) {
            joinClient.send(HexChessProtocol.simple(HexChessProtocol.DRAW_DECLINE));
            return;
        }

        applyAndBroadcast(gameState.declineDraw(drawResponderColor()));
    }

    @FXML
    private void onResign() {
        if (isNetworkClient()) {
            joinClient.send(HexChessProtocol.simple(HexChessProtocol.RESIGN));
            return;
        }

        applyAndBroadcast(gameState.resign(controlledColor()));
    }

    @FXML
    private void onRestart() {
        if (isNetworkClient()) {
            joinClient.send(HexChessProtocol.simple(HexChessProtocol.RESTART));
            gameState = gameState.withStatusMessage("Restart requested. Waiting for host...");
            render();
            return;
        }

        restartMatch("Game restarted.");
    }

    @FXML
    private void onBackToMenu(ActionEvent event) {
        if (gameState.isActive() && !confirm("Leave Match", "Leave this match and return to the Chexsagon menu?")) {
            return;
        }
        closeNetwork();
        Router.goTo(event, "/hexchess/HexChessMenu.fxml", null);
    }

    private boolean confirm(String title, String message) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        confirm.setTitle(title);
        return confirm.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    private void startGame(HexChessGameSetup nextSetup) {
        startGame(nextSetup, null);
    }

    private void startGame(HexChessGameSetup nextSetup, String statusMessage) {
        setup = nextSetup == null ? HexChessGameSetup.local() : nextSetup;
        gameState = HexGameState.create(setup.initialBoard(), setup.startingTurn(), !setup.customPosition());
        if (statusMessage != null) {
            gameState = gameState.withStatusMessage(statusMessage);
        }
        clearSelection();
        botThinking = false;
        buildBoard();
        render();
    }

    private void configureNetwork() {
        if (isNetworkHost()) {
            hostServer.setMessageListener(message -> Platform.runLater(() -> processHostMessage(message)));
            hostServer.setDisconnectListener(() -> Platform.runLater(() -> markDisconnected("Joiner disconnected.")));
            sendStartStateIfHost();
        }

        if (isNetworkClient()) {
            joinClient.setMessageListener(message -> Platform.runLater(() -> processClientMessage(message)));
            joinClient.setDisconnectListener(() -> Platform.runLater(() -> markDisconnected("Host disconnected.")));
            joinClient.send(HexChessProtocol.join(setup.blackName()));
        }
    }

    private void buildBoard() {
        if (boardCanvas == null) {
            return;
        }

        canvasBoard.attach(boardCanvas, this::onCellClicked);
    }

    private void onCellClicked(HexCoordinate coordinate) {
        if (botThinking || !gameState.isActive() || !canControlCurrentTurn()) {
            return;
        }

        List<HexMove> matchingMoves = selectedMoves.stream()
                .filter(move -> move.to().equals(coordinate))
                .toList();

        if (!matchingMoves.isEmpty()) {
            choosePromotionMove(matchingMoves).ifPresent(this::submitMove);
            return;
        }

        boolean ownPieceSelected = gameState.board()
                .pieceAt(coordinate)
                .map(HexPiece::color)
                .filter(gameState.turn()::equals)
                .isPresent();
        if (ownPieceSelected) {
            selectedCoordinate = coordinate;
            selectedMoves = gameState.legalMovesFrom(coordinate);
        } else {
            clearSelection();
        }

        render();
    }

    private void submitMove(HexMove move) {
        if (isNetworkClient()) {
            joinClient.send(HexChessProtocol.move(move));
            clearSelection();
            gameState = gameState.withStatusMessage("Move sent.");
            render();
            return;
        }

        applyAndBroadcast(gameState.play(move));
        maybePlayBotMove();
    }

    private Optional<HexMove> choosePromotionMove(List<HexMove> matchingMoves) {
        if (matchingMoves.size() == 1) {
            return Optional.of(matchingMoves.getFirst());
        }

        List<PromotionChoice> choices = matchingMoves.stream()
                .map(HexMove::promotion)
                .filter(type -> type != null)
                .distinct()
                .map(PromotionChoice::new)
                .toList();
        if (choices.isEmpty()) {
            return Optional.of(matchingMoves.getFirst());
        }

        ChoiceDialog<PromotionChoice> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        if (boardCanvas.getScene() != null) {
            dialog.initOwner(boardCanvas.getScene().getWindow());
        }
        dialog.setTitle("Promotion");
        dialog.setHeaderText("Choose promotion piece");
        dialog.setContentText("Promote to:");

        return dialog.showAndWait()
                .flatMap(choice -> matchingMoves.stream()
                        .filter(move -> move.promotion() == choice.type())
                        .findFirst());
    }

    private void maybePlayBotMove() {
        if (setup.mode() != HexGameMode.BOT
                || gameState.turn() != HexPieceColor.BLACK
                || !gameState.isActive()) {
            return;
        }

        botThinking = true;
        statusLabel.setText("Bot thinking...");

        PauseTransition pause = new PauseTransition(BOT_DELAY);
        pause.setOnFinished(event -> {
            HexGameState stateBeforeBotMove = gameState;
            Thread botThread = new Thread(() -> {
                try {
                    HexMove botMove = HexChessBot.chooseMove(stateBeforeBotMove).orElse(null);
                    Platform.runLater(() -> finishBotMove(stateBeforeBotMove, botMove));
                } catch (RuntimeException e) {
                    Platform.runLater(() -> failBotMove(stateBeforeBotMove));
                }
            }, "hexchess-bot");
            botThread.setDaemon(true);
            botThread.start();
        });
        pause.play();
    }

    private void finishBotMove(HexGameState stateBeforeBotMove, HexMove botMove) {
        if (gameState == stateBeforeBotMove
                && botThinking
                && setup.mode() == HexGameMode.BOT
                && gameState.turn() == HexPieceColor.BLACK
                && gameState.isActive()) {
            if (botMove != null) {
                gameState = gameState.play(botMove);
            }
            broadcastStateIfHost();
        }

        botThinking = false;
        render();
    }

    private void failBotMove(HexGameState stateBeforeBotMove) {
        if (gameState == stateBeforeBotMove && botThinking) {
            gameState = gameState.failed("Bot encountered an error. Restart this game or return to the menu.");
        }
        botThinking = false;
        render();
    }

    private void maybeBotDeclineDrawOffer() {
        if (setup.mode() != HexGameMode.BOT
                || gameState.drawOfferBy() != HexPieceColor.WHITE
                || !gameState.isActive()) {
            return;
        }

        PauseTransition pause = new PauseTransition(BOT_DRAW_DECLINE_DELAY);
        pause.setOnFinished(event -> {
            if (setup.mode() == HexGameMode.BOT
                    && gameState.drawOfferBy() == HexPieceColor.WHITE
                    && gameState.isActive()) {
                gameState = gameState.declineDraw(HexPieceColor.BLACK);
                render();
            }
        });
        pause.play();
    }

    private void processHostMessage(String message) {
        switch (HexChessProtocol.type(message)) {
            case HexChessProtocol.JOIN -> {
                List<String> fields = HexChessProtocol.fields(message);
                if (!fields.isEmpty() && !fields.getFirst().isBlank()) {
                    setup = new HexChessGameSetup(
                            setup.whiteName(),
                            fields.getFirst(),
                            HexGameMode.NETWORK_HOST,
                            setup.initialBoard(),
                            setup.startingTurn());
                }
                sendStartStateIfHost();
            }
            case HexChessProtocol.MOVE -> {
                HexMove move = HexChessProtocol.parseMove(HexChessProtocol.fields(message));
                if (move == null) {
                    hostServer.send(HexChessProtocol.error("Invalid move payload."));
                } else if (gameState.turn() != HexPieceColor.BLACK) {
                    hostServer.send(HexChessProtocol.error("It is not Black's turn."));
                } else {
                    applyAndBroadcast(gameState.play(move));
                }
            }
            case HexChessProtocol.DRAW_OFFER -> applyAndBroadcast(gameState.offerDraw(HexPieceColor.BLACK));
            case HexChessProtocol.DRAW_ACCEPT -> applyAndBroadcast(gameState.acceptDraw(HexPieceColor.BLACK));
            case HexChessProtocol.DRAW_DECLINE -> applyAndBroadcast(gameState.declineDraw(HexPieceColor.BLACK));
            case HexChessProtocol.RESIGN -> applyAndBroadcast(gameState.resign(HexPieceColor.BLACK));
            case HexChessProtocol.RESTART -> restartMatch("Game restarted.");
            case HexChessProtocol.QUIT -> markDisconnected("Joiner left the game.");
            default -> {
            }
        }
    }

    private void processClientMessage(String message) {
        switch (HexChessProtocol.type(message)) {
            case HexChessProtocol.START -> {
                List<String> fields = HexChessProtocol.fields(message);
                if (fields.size() == 3) {
                    setup = new HexChessGameSetup(
                            fields.getFirst(),
                            fields.get(1),
                            HexGameMode.NETWORK_CLIENT);
                    applySnapshot(fields.get(2));
                } else {
                    failNetworkState();
                }
            }
            case HexChessProtocol.STATE -> {
                List<String> fields = HexChessProtocol.fields(message);
                if (fields.size() == 1) {
                    applySnapshot(fields.getFirst());
                } else {
                    failNetworkState();
                }
            }
            case HexChessProtocol.ERROR -> {
                List<String> fields = HexChessProtocol.fields(message);
                gameState = gameState.withStatusMessage(fields.isEmpty() ? "Network error." : fields.getFirst());
                render();
            }
            case HexChessProtocol.QUIT -> markDisconnected("Host left the game.");
            default -> {
            }
        }
    }

    private void applySnapshot(String snapshot) {
        try {
            gameState = HexChessStateSnapshot.deserialize(snapshot);
            clearSelection();
            render();
        } catch (IllegalArgumentException e) {
            failNetworkState();
        }
    }

    private void failNetworkState() {
        gameState = gameState.failed("Invalid network state received.");
        clearSelection();
        render();
    }

    private void markDisconnected(String message) {
        gameState = gameState.disconnected(message);
        clearSelection();
        render();
    }

    private void applyAndBroadcast(HexGameState nextState) {
        gameState = nextState;
        clearSelection();
        broadcastStateIfHost();
        render();
    }

    private void broadcastStateIfHost() {
        if (isNetworkHost() && hostServer.isConnected()) {
            hostServer.send(HexChessProtocol.state(HexChessStateSnapshot.serialize(gameState)));
        }
    }

    private void sendStartStateIfHost() {
        if (isNetworkHost() && hostServer.isConnected()) {
            hostServer.send(HexChessProtocol.start(setup, HexChessStateSnapshot.serialize(gameState)));
        }
    }

    private void render() {
        if (boardCanvas == null) {
            return;
        }

        drawBoard();
        renderLabels();
    }

    private void drawBoard() {
        canvasBoard.redraw(boardCanvas, this::drawCell, this::drawPiece);
    }

    private void renderLabels() {
        modeLabel.setText(modeText());
        turnLabel.setText(gameState.isActive()
                ? gameState.turn().displayName() + " to move"
                : "Game over");
        statusLabel.setText(gameState.statusMessage());
        scoreLabel.setText("White " + formatScore(gameState.whiteScore())
                + " : " + formatScore(gameState.blackScore()) + " Black");
        lastMoveLabel.setText(gameState.lastMove() == null
                ? "Last move: none"
                : "Last move: " + gameState.lastMove().move().notation());

        boolean drawOfferVisible = gameState.drawOfferBy() != null;
        boolean canAnswerDrawOffer = gameState.hasDrawOfferFor(drawResponderColor());
        drawOfferLabel.setText(drawOfferVisible
                ? gameState.drawOfferBy().displayName() + " offered a draw."
                : "");
        setVisibleManaged(acceptDrawButton, canAnswerDrawOffer);
        setVisibleManaged(declineDrawButton, canAnswerDrawOffer);

        if (offerDrawButton != null) {
            offerDrawButton.setDisable(!gameState.isActive() || gameState.drawOfferBy() != null);
        }
        if (resignButton != null) {
            resignButton.setDisable(!gameState.isActive());
        }
        if (restartButton != null) {
            setVisibleManaged(restartButton, true);
            restartButton.setDisable(gameState.status() == HexGameStatus.DISCONNECTED
                    || gameState.status() == HexGameStatus.ERROR);
        }
    }

    private void restartMatch(String message) {
        startGame(setup, message);
        sendStartStateIfHost();
    }

    private void drawCell(GraphicsContext graphics, HexCoordinate coordinate) {
        canvasBoard.fillCell(graphics, coordinate, cellFill(coordinate));
        drawCellStroke(graphics, coordinate);
        canvasBoard.drawNotation(
                graphics,
                coordinate,
                gameState.board().pieceAt(coordinate).isPresent(),
                NOTATION_COLOR);
    }

    private void drawCellStroke(GraphicsContext graphics, HexCoordinate coordinate) {
        canvasBoard.strokeCell(graphics, coordinate, STROKE_BASE, 1);
        if (isLastMoveCell(coordinate)) {
            canvasBoard.strokeCell(graphics, coordinate, STROKE_LAST, 3);
        }
        if (coordinate.equals(selectedCoordinate)) {
            canvasBoard.strokeCell(graphics, coordinate, STROKE_SELECTED, 3);
        }
    }

    private Color cellFill(HexCoordinate coordinate) {
        if (isCheckedKingCell(coordinate)) {
            return CELL_CHECK;
        }
        if (selectedMoves.stream().anyMatch(move -> move.to().equals(coordinate))) {
            return CELL_LEGAL;
        }

        return switch (HexBoardGeometry.tone(coordinate)) {
            case LIGHT -> CELL_LIGHT;
            case MID -> CELL_MID;
            case DARK -> CELL_DARK;
        };
    }

    private void drawPiece(GraphicsContext graphics, HexCoordinate coordinate) {
        HexPiece piece = gameState.board().pieceAt(coordinate).orElse(null);
        canvasBoard.drawPiece(graphics, coordinate, piece);
    }

    private boolean isLastMoveCell(HexCoordinate coordinate) {
        return gameState.lastMove() != null
                && (gameState.lastMove().move().from().equals(coordinate)
                || gameState.lastMove().move().to().equals(coordinate));
    }

    private boolean isCheckedKingCell(HexCoordinate coordinate) {
        return gameState.isInCheck(gameState.turn())
                && gameState.board().kingPosition(gameState.turn()).filter(coordinate::equals).isPresent();
    }

    private void clearSelection() {
        selectedCoordinate = null;
        selectedMoves = List.of();
    }

    private boolean canControlCurrentTurn() {
        if (isNetworkHost()) {
            return gameState.turn() == HexPieceColor.WHITE;
        }
        if (isNetworkClient()) {
            return gameState.turn() == HexPieceColor.BLACK;
        }
        return setup.mode() != HexGameMode.BOT || gameState.turn() == HexPieceColor.WHITE;
    }

    private HexPieceColor controlledColor() {
        if (isNetworkClient()) {
            return HexPieceColor.BLACK;
        }
        if (isNetworkHost()) {
            return HexPieceColor.WHITE;
        }
        return gameState.turn();
    }

    private HexPieceColor drawResponderColor() {
        if (setup.mode() == HexGameMode.LOCAL && gameState.drawOfferBy() != null) {
            return gameState.drawOfferBy().opponent();
        }

        return controlledColor();
    }

    private boolean isNetworkHost() {
        return hostServer != null;
    }

    private boolean isNetworkClient() {
        return joinClient != null;
    }

    private void closeNetwork() {
        if (hostServer != null) {
            hostServer.send(HexChessProtocol.simple(HexChessProtocol.QUIT));
            hostServer.close();
            hostServer = null;
        }
        if (joinClient != null) {
            joinClient.send(HexChessProtocol.simple(HexChessProtocol.QUIT));
            joinClient.close();
            joinClient = null;
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

    private void setVisibleManaged(Button button, boolean visible) {
        if (button == null) {
            return;
        }

        button.setVisible(visible);
        button.setManaged(visible);
    }

    private String modeText() {
        return switch (setup.mode()) {
            case BOT -> setup.whiteName() + " vs Bot";
            case NETWORK_HOST -> setup.whiteName() + " vs " + setup.blackName() + " - LAN host";
            case NETWORK_CLIENT -> setup.whiteName() + " vs " + setup.blackName() + " - LAN joiner";
            default -> setup.whiteName() + " vs " + setup.blackName();
        };
    }

    private String formatScore(double score) {
        return score == Math.rint(score)
                ? String.valueOf((int) score)
                : String.valueOf(score);
    }

    private record PromotionChoice(HexPieceType type) {

        @Override
        public String toString() {
            return switch (type) {
                case QUEEN -> "Queen";
                case ROOK -> "Rook";
                case BISHOP -> "Bishop";
                case KNIGHT -> "Knight";
                default -> type.name();
            };
        }
    }
}
