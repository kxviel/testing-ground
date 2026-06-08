package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoardGeometry;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessBot;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameState;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexMove;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public class HexChessGameController implements RouteDataReceiver {

    private static final double HEX_SIZE = 27.0;
    private static final double BOARD_WIDTH = 820.0;
    private static final double BOARD_HEIGHT = 650.0;
    private static final Duration BOT_DELAY = Duration.millis(350);

    @FXML
    private Pane boardPane;
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
    private Button acceptDrawButton;
    @FXML
    private Button declineDrawButton;

    private final Map<HexCoordinate, Polygon> cellPolygons = new HashMap<>();
    private final Map<HexCoordinate, Text> pieceTexts = new HashMap<>();

    private HexChessGameSetup setup = HexChessGameSetup.local();
    private HexGameState gameState = HexGameState.standard();
    private HexCoordinate selectedCoordinate;
    private List<HexMove> selectedMoves = List.of();
    private boolean botThinking;

    @FXML
    public void initialize() {
        startGame(setup);
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof HexChessGameSetup nextSetup) {
            startGame(nextSetup);
        }
    }

    @FXML
    private void onOfferDraw() {
        gameState = gameState.offerDraw();
        render();
    }

    @FXML
    private void onAcceptDraw() {
        gameState = gameState.acceptDraw();
        clearSelection();
        render();
    }

    @FXML
    private void onDeclineDraw() {
        gameState = gameState.declineDraw();
        render();
    }

    @FXML
    private void onResign() {
        gameState = gameState.resign();
        clearSelection();
        render();
    }

    @FXML
    private void onRestart() {
        startGame(setup);
    }

    @FXML
    private void onBackToMenu(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessMenu.fxml", null);
    }

    private void startGame(HexChessGameSetup nextSetup) {
        setup = nextSetup == null ? HexChessGameSetup.local() : nextSetup;
        gameState = HexGameState.standard();
        clearSelection();
        botThinking = false;
        buildBoard();
        render();
    }

    private void buildBoard() {
        if (boardPane == null) {
            return;
        }

        boardPane.getChildren().clear();
        cellPolygons.clear();
        pieceTexts.clear();
        boardPane.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);

        HexBoardGeometry.displayOrder().forEach(coordinate -> {
            Point2D center = centerOf(coordinate);
            Polygon cell = createHexagon(center);
            Text notation = createNotationText(coordinate, center);
            Text piece = createPieceText(center);
            Group group = new Group(cell, notation, piece);

            group.setOnMouseClicked(event -> onCellClicked(coordinate));
            cellPolygons.put(coordinate, cell);
            pieceTexts.put(coordinate, piece);
            boardPane.getChildren().add(group);
        });
    }

    private void onCellClicked(HexCoordinate coordinate) {
        if (botThinking || !gameState.isActive()) {
            return;
        }

        Optional<HexMove> selectedMove = selectedMoves.stream()
                .filter(move -> move.to().equals(coordinate))
                .findFirst();

        if (selectedMove.isPresent()) {
            gameState = gameState.play(selectedMove.get());
            clearSelection();
            render();
            maybePlayBotMove();
            return;
        }

        Optional<HexPiece> piece = gameState.board().pieceAt(coordinate);
        if (piece.isPresent() && piece.get().color() == gameState.turn()) {
            selectedCoordinate = coordinate;
            selectedMoves = gameState.legalMovesFrom(coordinate);
        } else {
            clearSelection();
        }

        render();
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
            HexChessBot.chooseMove(gameState).ifPresent(move -> gameState = gameState.play(move));
            botThinking = false;
            render();
        });
        pause.play();
    }

    private void render() {
        if (boardPane == null) {
            return;
        }

        renderCells();
        renderPieces();
        renderLabels();
    }

    private void renderCells() {
        cellPolygons.forEach((coordinate, polygon) -> {
            polygon.getStyleClass().setAll("hex-cell", HexBoardGeometry.tone(coordinate).styleClass());

            if (coordinate.equals(selectedCoordinate)) {
                polygon.getStyleClass().add("hex-cell-selected");
            }
            if (selectedMoves.stream().anyMatch(move -> move.to().equals(coordinate))) {
                polygon.getStyleClass().add("hex-cell-legal");
            }
            if (gameState.lastMove() != null && gameState.lastMove().move().from().equals(coordinate)) {
                polygon.getStyleClass().add("hex-cell-last-from");
            }
            if (gameState.lastMove() != null && gameState.lastMove().move().to().equals(coordinate)) {
                polygon.getStyleClass().add("hex-cell-last-to");
            }
            if (gameState.isInCheck(gameState.turn())
                    && gameState.board().kingPosition(gameState.turn()).filter(coordinate::equals).isPresent()) {
                polygon.getStyleClass().add("hex-cell-check");
            }
        });
    }

    private void renderPieces() {
        pieceTexts.forEach((coordinate, text) -> {
            Optional<HexPiece> piece = gameState.board().pieceAt(coordinate);
            text.setText(piece.map(HexPiece::displayText).orElse(""));
            text.getStyleClass().setAll("hex-piece");
            piece.map(HexPiece::color)
                    .map(color -> color == HexPieceColor.WHITE ? "hex-piece-white" : "hex-piece-black")
                    .ifPresent(styleClass -> text.getStyleClass().add(styleClass));
        });
    }

    private void renderLabels() {
        modeLabel.setText(setup.mode() == HexGameMode.BOT
                ? setup.whiteName() + " vs Bot"
                : setup.whiteName() + " vs " + setup.blackName());
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
        drawOfferLabel.setText(drawOfferVisible
                ? gameState.drawOfferBy().displayName() + " offered a draw."
                : "");
        acceptDrawButton.setVisible(drawOfferVisible);
        acceptDrawButton.setManaged(drawOfferVisible);
        declineDrawButton.setVisible(drawOfferVisible);
        declineDrawButton.setManaged(drawOfferVisible);
    }

    private Point2D centerOf(HexCoordinate coordinate) {
        double q = HexBoardGeometry.axialQ(coordinate);
        double r = HexBoardGeometry.axialR(coordinate);
        double x = (HEX_SIZE * 1.5 * q) + (BOARD_WIDTH / 2.0);
        double y = (-HEX_SIZE * Math.sqrt(3) * (r + q / 2.0)) + (BOARD_HEIGHT / 2.0);

        return new Point2D(x, y);
    }

    private Polygon createHexagon(Point2D center) {
        Polygon polygon = new Polygon();
        IntStream.range(0, 6)
                .mapToDouble(index -> Math.toRadians(30 + 60 * index))
                .forEach(angle -> polygon.getPoints().addAll(
                        center.getX() + HEX_SIZE * Math.cos(angle),
                        center.getY() + HEX_SIZE * Math.sin(angle)));
        polygon.getStyleClass().add("hex-cell");
        return polygon;
    }

    private Text createNotationText(HexCoordinate coordinate, Point2D center) {
        Text text = new Text(coordinate.notation());
        text.getStyleClass().add("hex-notation");
        text.setX(center.getX() - 10);
        text.setY(center.getY() + 18);
        text.setMouseTransparent(true);
        return text;
    }

    private Text createPieceText(Point2D center) {
        Text text = new Text();
        text.setFill(Color.BLACK);
        text.getStyleClass().add("hex-piece");
        text.setX(center.getX() - 9);
        text.setY(center.getY() + 8);
        text.setMouseTransparent(true);
        return text;
    }

    private void clearSelection() {
        selectedCoordinate = null;
        selectedMoves = List.of();
    }

    private String formatScore(double score) {
        return score == Math.rint(score)
                ? String.valueOf((int) score)
                : String.valueOf(score);
    }
}
