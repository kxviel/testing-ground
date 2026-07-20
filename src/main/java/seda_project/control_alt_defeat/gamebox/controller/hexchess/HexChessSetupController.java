package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
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
import seda_project.control_alt_defeat.gamebox.util.ResponsiveLayout;
import seda_project.control_alt_defeat.gamebox.util.RouteDataReceiver;
import seda_project.control_alt_defeat.gamebox.util.Router;
import seda_project.control_alt_defeat.gamebox.util.SafeText;
import seda_project.control_alt_defeat.gamebox.util.UiInputGuards;

import java.util.Map;

public class HexChessSetupController implements RouteDataReceiver {

    private static final double BASE_HEX_SIZE = 24.0;
    private static final double FALLBACK_CANVAS_WIDTH = 720.0;
    private static final double FALLBACK_CANVAS_HEIGHT = 590.0;
    private static final double BOARD_CONTENT_PADDING = 20.0;
    private static final double NOTATION_OFFSET_RATIO = 13.0 / BASE_HEX_SIZE;
    private static final double PIECE_FONT_RATIO = 28.0 / BASE_HEX_SIZE;

    @FXML
    private GridPane setupMain;
    @FXML
    private StackPane canvasFrame;
    @FXML
    private Canvas boardCanvas;
    @FXML
    private TextField playerOneNameField;
    @FXML
    private TextField playerTwoNameField;
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
    @FXML
    private Button startButton;

    private HexChessCanvasBoard canvasBoard = createCanvasBoard(
            BASE_HEX_SIZE,
            FALLBACK_CANVAS_WIDTH,
            FALLBACK_CANVAS_HEIGHT);

    private HexChessGameSetup setup = HexChessGameSetup.local();
    private HexBoard board = HexBoard.standard();
    private HexCoordinate selectedCoordinate;

    @FXML
    public void initialize() {
        ResponsiveLayout.bindTwoColumnGrid(setupMain, 60.0);
        UiInputGuards.limitPlayerNames(playerOneNameField, playerTwoNameField);
        colorChoiceBox.getItems().setAll(HexPieceColor.values());
        colorChoiceBox.getSelectionModel().select(HexPieceColor.WHITE);
        typeChoiceBox.getItems().setAll(HexPieceType.KING, HexPieceType.QUEEN, HexPieceType.ROOK,
                HexPieceType.BISHOP, HexPieceType.KNIGHT, HexPieceType.PAWN);
        typeChoiceBox.getSelectionModel().select(HexPieceType.KING);
        turnChoiceBox.getItems().setAll(HexPieceColor.values());
        turnChoiceBox.getSelectionModel().select(HexPieceColor.WHITE);
        turnChoiceBox.valueProperty().addListener((ignored, previous, next) -> renderValidation());

        bindBoardResize();
        attachBoardHandlers();
        render();
    }

    @Override
    public void setRouteData(Object data) {
        if (data instanceof HexChessGameSetup nextSetup) {
            setup = nextSetup;
            board = nextSetup.initialBoard();
            playerOneNameField.setText(nextSetup.whiteName());
            playerTwoNameField.setText(nextSetup.blackName());
            turnChoiceBox.getSelectionModel().select(nextSetup.startingTurn());
            render();
        }
    }

    @FXML
    private void onRemovePiece() {
        removeSelectedPiece();
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
        if (startingTurn == null) {
            startingTurn = HexPieceColor.WHITE;
            turnChoiceBox.getSelectionModel().select(startingTurn);
        }
        HexPositionValidation validation = HexPositionValidator.validate(board, startingTurn);

        if (!validation.isValid()) {
            validationLabel.setText(validation.message());
            return;
        }

        startButton.setDisable(true);
        Router.goTo(event, "/hexchess/HexChessGame.fxml", new HexChessGameSetup(
                playerOneName(),
                playerTwoName(),
                HexGameMode.LOCAL,
                board,
                startingTurn,
                true));
    }

    @FXML
    private void onBack(ActionEvent event) {
        Router.goTo(event, "/hexchess/HexChessMenu.fxml", setupWithNames());
    }

    private HexChessGameSetup setupWithNames() {
        return new HexChessGameSetup(
                playerOneName(),
                playerTwoName(),
                setup.mode(),
                board,
                turnChoiceBox.getSelectionModel().getSelectedItem(),
                setup.customPosition());
    }

    private String playerOneName() {
        return SafeText.playerName(text(playerOneNameField), SafeText.PLAYER_ONE_NAME);
    }

    private String playerTwoName() {
        return SafeText.playerName(text(playerTwoNameField), SafeText.PLAYER_TWO_NAME);
    }

    private static String text(TextField field) {
        return field == null ? "" : field.getText();
    }

    private void attachBoardHandlers() {
        canvasBoard.attach(boardCanvas, this::onCellClicked);
        boardCanvas.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                removeSelectedPiece();
            }
        });
    }

    private void bindBoardResize() {
        if (boardCanvas == null || canvasFrame == null) {
            return;
        }

        boardCanvas.setManaged(false);
        canvasFrame.widthProperty().addListener((observable, oldValue, newValue) -> resizeBoardCanvas());
        canvasFrame.heightProperty().addListener((observable, oldValue, newValue) -> resizeBoardCanvas());
        Platform.runLater(this::resizeBoardCanvas);
    }

    private void resizeBoardCanvas() {
        if (boardCanvas == null || canvasFrame == null) {
            return;
        }

        CanvasSize size = canvasSize();
        double hexSize = fitHexSize(size.width(), size.height());
        canvasBoard = createCanvasBoard(hexSize, size.width(), size.height());
        canvasBoard.attach(boardCanvas, this::onCellClicked);
        Insets insets = canvasInsets();
        boardCanvas.relocate(insets.getLeft(), insets.getTop());
        drawBoard();
    }

    private CanvasSize canvasSize() {
        double frameWidth = canvasFrame.getWidth();
        double frameHeight = canvasFrame.getHeight();

        if (frameWidth <= 0 || frameHeight <= 0) {
            return new CanvasSize(FALLBACK_CANVAS_WIDTH, FALLBACK_CANVAS_HEIGHT);
        }

        Insets insets = canvasInsets();

        return new CanvasSize(
                Math.max(1.0, frameWidth - insets.getLeft() - insets.getRight()),
                Math.max(1.0, frameHeight - insets.getTop() - insets.getBottom()));
    }

    private Insets canvasInsets() {
        Insets insets = canvasFrame.getInsets();
        if (insets == null
                || (insets.getLeft() + insets.getRight() <= 0
                && insets.getTop() + insets.getBottom() <= 0)) {
            return new Insets(BOARD_CONTENT_PADDING);
        }
        return insets;
    }

    private double fitHexSize(double width, double height) {
        double widthFit = width / HexChessCanvasBoard.boardWidth(1.0);
        double heightFit = height / HexChessCanvasBoard.boardHeight(1.0);
        return Math.max(1.0, Math.min(widthFit, heightFit));
    }

    private static HexChessCanvasBoard createCanvasBoard(double hexSize, double width, double height) {
        return new HexChessCanvasBoard(
                hexSize,
                width,
                height,
                Math.max(1.0, hexSize * NOTATION_OFFSET_RATIO),
                Math.max(1.0, hexSize * PIECE_FONT_RATIO));
    }

    private void onCellClicked(HexCoordinate coordinate, MouseButton button) {
        if (!HexBoardGeometry.isValid(coordinate) || button == null) {
            validationLabel.setText("Select a valid board cell.");
            return;
        }
        selectedCoordinate = coordinate;

        if (button == MouseButton.SECONDARY) {
            board = board.withoutPiece(coordinate);
            boardCanvas.requestFocus();
            render();
            return;
        }

        if (button != MouseButton.PRIMARY) {
            render();
            return;
        }

        HexPieceColor selectedColor = colorChoiceBox.getSelectionModel().getSelectedItem();
        HexPieceType selectedType = typeChoiceBox.getSelectionModel().getSelectedItem();
        if (selectedColor == null || selectedType == null) {
            colorChoiceBox.getSelectionModel().select(HexPieceColor.WHITE);
            typeChoiceBox.getSelectionModel().select(HexPieceType.KING);
            validationLabel.setText("Choose a piece color and type first.");
            return;
        }
        HexPiece piece = new HexPiece(selectedColor, selectedType);
        HexBoard candidateBoard = withoutExistingKing(piece).withPiece(coordinate, piece);
        HexPositionValidation materialValidation = HexPositionValidator.validateMaterial(candidateBoard);
        if (!materialValidation.isValid()) {
            validationLabel.setText(materialValidation.message());
            validationLabel.getStyleClass().setAll("status-box", "status-box-warn");
            startButton.setDisable(true);
            boardCanvas.requestFocus();
            return;
        }
        board = candidateBoard;
        boardCanvas.requestFocus();
        render();
    }

    private void removeSelectedPiece() {
        if (selectedCoordinate == null) {
            validationLabel.setText("Select a cell first.");
            return;
        }

        board = board.withoutPiece(selectedCoordinate);
        render();
    }

    private HexBoard withoutExistingKing(HexPiece piece) {
        if (piece.type() != HexPieceType.KING) {
            return board;
        }

        return board.piecesOf(piece.color())
                .filter(entry -> entry.getValue().type() == HexPieceType.KING)
                .map(Map.Entry::getKey)
                .findFirst()
                .map(board::withoutPiece)
                .orElse(board);
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
        if (startButton != null) {
            startButton.setDisable(!validation.isValid());
        }
    }

    private void drawCell(GraphicsContext graphics, HexCoordinate coordinate) {
        canvasBoard.fillCell(graphics, coordinate, cellFill(coordinate));
        canvasBoard.strokeCell(graphics, coordinate, HexChessCanvasBoard.STROKE_BASE, 1);
        if (coordinate.equals(selectedCoordinate)) {
            canvasBoard.strokeCell(graphics, coordinate, HexChessCanvasBoard.STROKE_SELECTED, 3);
        }
        canvasBoard.drawNotation(
                graphics,
                coordinate,
                board.pieceAt(coordinate).isPresent(),
                HexChessCanvasBoard.NOTATION_COLOR);
    }

    private Color cellFill(HexCoordinate coordinate) {
        return HexChessCanvasBoard.baseFill(coordinate);
    }

    private void drawPiece(GraphicsContext graphics, HexCoordinate coordinate) {
        HexPiece piece = board.pieceAt(coordinate).orElse(null);
        canvasBoard.drawPiece(graphics, coordinate, piece);
    }

    private record CanvasSize(double width, double height) {
    }
}
