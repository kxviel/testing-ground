package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public class HexChessSetupController implements RouteDataReceiver {

    private static final double HEX_SIZE = 24.0;
    private static final double BOARD_WIDTH = 720.0;
    private static final double BOARD_HEIGHT = 590.0;

    @FXML
    private Pane boardPane;
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

    private final Map<HexCoordinate, Polygon> cellPolygons = new HashMap<>();
    private final Map<HexCoordinate, Text> pieceTexts = new HashMap<>();

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
        selectedCoordinate = coordinate;
        board = board.withPiece(coordinate, new HexPiece(
                colorChoiceBox.getSelectionModel().getSelectedItem(),
                typeChoiceBox.getSelectionModel().getSelectedItem()));
        render();
    }

    private void render() {
        renderCells();
        renderPieces();
        renderValidation();
    }

    private void renderCells() {
        cellPolygons.forEach((coordinate, polygon) -> {
            polygon.getStyleClass().setAll("hex-cell", HexBoardGeometry.tone(coordinate).styleClass());

            if (coordinate.equals(selectedCoordinate)) {
                polygon.getStyleClass().add("hex-cell-selected");
            }
            if (HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.WHITE)
                    || HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.BLACK)) {
                polygon.getStyleClass().add("hex-cell-promotion");
            }
        });
    }

    private void renderPieces() {
        pieceTexts.forEach((coordinate, text) -> {
            Optional<HexPiece> piece = board.pieceAt(coordinate);
            text.setText(piece.map(HexPiece::displayText).orElse(""));
            text.getStyleClass().setAll("hex-piece");
            piece.map(HexPiece::color)
                    .map(color -> color == HexPieceColor.WHITE ? "hex-piece-white" : "hex-piece-black")
                    .ifPresent(styleClass -> text.getStyleClass().add(styleClass));
        });
    }

    private void renderValidation() {
        selectedLabel.setText(selectedCoordinate == null
                ? "Selected: none"
                : "Selected: " + selectedCoordinate.notation());
        HexPositionValidation validation = HexPositionValidator.validate(
                board,
                turnChoiceBox.getSelectionModel().getSelectedItem());
        validationLabel.setText(validation.message());
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
        Text text = new Text(promotionLabel(coordinate));
        text.getStyleClass().add("hex-notation");
        text.setX(center.getX() - 10);
        text.setY(center.getY() + 16);
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

    private String promotionLabel(HexCoordinate coordinate) {
        boolean promotionSquare = HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.WHITE)
                || HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.BLACK);

        return promotionSquare ? coordinate.notation() + "*" : coordinate.notation();
    }
}
