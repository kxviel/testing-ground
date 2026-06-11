package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoard;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoardGeometry;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPositionValidation;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPositionValidator;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;

public class HexChessSetupController implements RouteDataReceiver {

    private static final double HEX_SIZE = 24.0;
    private static final double BOARD_WIDTH = 720.0;
    private static final double BOARD_HEIGHT = 590.0;
    private static final Color CELL_LIGHT = Color.web("#f7c895");
    private static final Color CELL_MID = Color.web("#e5aa68");
    private static final Color CELL_DARK = Color.web("#cf873d");
    private static final Color STROKE_BASE = Color.web("#6b4a28");
    private static final Color STROKE_SELECTED = Color.web("#0f62fe");
    private static final Color NOTATION_COLOR = Color.rgb(23, 23, 23, 0.45);

    @FXML
    private Canvas boardCanvas;
    @FXML
    private TextField whiteNameField;
    @FXML
    private TextField blackNameField;
    @FXML
    private ComboBox<HexPieceColor> colorChoiceBox;
    @FXML
    private ComboBox<HexPieceType> typeChoiceBox;
    @FXML
    private ComboBox<HexPieceColor> turnChoiceBox;
    @FXML
    private Label selectedLabel;
    @FXML
    private Label validationLabel;

    private final HexChessCanvasBoard canvasBoard = new HexChessCanvasBoard(
            HEX_SIZE,
            BOARD_WIDTH,
            BOARD_HEIGHT,
            13,
            28);

    private HexChessGameSetup setup = HexChessGameSetup.local();
    private HexBoard board = HexBoard.standard();
    private HexCoordinate selectedCoordinate;

    @FXML
    public void initialize() {
        colorChoiceBox.getItems().setAll(HexPieceColor.values());
        colorChoiceBox.getSelectionModel().select(HexPieceColor.WHITE);
        typeChoiceBox.getItems().setAll(HexPieceType.KING, HexPieceType.QUEEN, HexPieceType.ROOK,
                HexPieceType.BISHOP, HexPieceType.KNIGHT, HexPieceType.PAWN);
        typeChoiceBox.getSelectionModel().select(HexPieceType.KING);
        turnChoiceBox.getItems().setAll(HexPieceColor.values());
        turnChoiceBox.getSelectionModel().select(HexPieceColor.WHITE);

        buildBoard();
        render();
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof HexChessGameSetup nextSetup) {
            setup = nextSetup;
            board = nextSetup.initialBoard();
            whiteNameField.setText(nextSetup.whiteName());
            blackNameField.setText(nextSetup.blackName());
            turnChoiceBox.getSelectionModel().select(nextSetup.startingTurn());
            render();
        }
    }

    @FXML
    private void onRemovePiece() {
        if (selectedCoordinate == null) {
            validationLabel.setText("Select a cell first.");
            return;
        }

        board = board.withoutPiece(selectedCoordinate);
        render();
    }

    @FXML
    private void onLoadStandard() {
        board = HexBoard.standard();
        selectedCoordinate = null;
        turnChoiceBox.getSelectionModel().select(HexPieceColor.WHITE);
        render();
    }

    @FXML
    private void onClearBoard() {
        board = HexBoard.empty();
        selectedCoordinate = null;
        render();
    }

    @FXML
    private void onStart(ActionEvent event) {
        HexPieceColor startingTurn = turnChoiceBox.getSelectionModel().getSelectedItem();
        HexPositionValidation validation = HexPositionValidator.validate(board, startingTurn);

        if (!validation.isValid()) {
            validationLabel.setText(validation.message());
            return;
        }

        Router.goTo(event, "/hexchess/HexChessGame.fxml", new HexChessGameSetup(
                whiteNameField.getText(),
                blackNameField.getText(),
                HexGameMode.LOCAL,
                board,
                startingTurn));
    }

    @FXML
    private void onBack(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessMenu.fxml", setup);
    }

    private void buildBoard() {
        canvasBoard.attach(boardCanvas, this::onCellClicked);
    }

    private void onCellClicked(HexCoordinate coordinate) {
        selectedCoordinate = coordinate;
        board = board.withPiece(coordinate, new HexPiece(
                colorChoiceBox.getSelectionModel().getSelectedItem(),
                typeChoiceBox.getSelectionModel().getSelectedItem()));
        render();
    }

    private void render() {
        drawBoard();
        renderValidation();
    }

    private void drawBoard() {
        canvasBoard.redraw(boardCanvas, this::drawCell, this::drawPiece);
    }

    private void renderValidation() {
        selectedLabel.setText(selectedCoordinate == null
                ? "Selected: none"
                : "Selected: " + selectedCoordinate.notation());
        HexPositionValidation validation = HexPositionValidator.validate(
                board,
                turnChoiceBox.getSelectionModel().getSelectedItem());
        validationLabel.setText(validation.message());
        validationLabel.getStyleClass().setAll(
                "status-box",
                validation.isValid() ? "status-box-good" : "status-box-warn");
    }

    private void drawCell(GraphicsContext graphics, HexCoordinate coordinate) {
        canvasBoard.fillCell(graphics, coordinate, cellFill(coordinate));
        canvasBoard.strokeCell(graphics, coordinate, STROKE_BASE, 1);
        if (coordinate.equals(selectedCoordinate)) {
            canvasBoard.strokeCell(graphics, coordinate, STROKE_SELECTED, 3);
        }
        canvasBoard.drawNotation(graphics, coordinate, board.pieceAt(coordinate).isPresent(), NOTATION_COLOR);
    }

    private Color cellFill(HexCoordinate coordinate) {
        return switch (HexBoardGeometry.tone(coordinate)) {
            case LIGHT -> CELL_LIGHT;
            case MID -> CELL_MID;
            case DARK -> CELL_DARK;
        };
    }

    private void drawPiece(GraphicsContext graphics, HexCoordinate coordinate) {
        HexPiece piece = board.pieceAt(coordinate).orElse(null);
        canvasBoard.drawPiece(graphics, coordinate, piece);
    }
}
