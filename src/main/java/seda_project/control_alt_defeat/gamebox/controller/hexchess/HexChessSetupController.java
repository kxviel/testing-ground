package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
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
    private static final Color CELL_LIGHT = Color.web("#f7c895");
    private static final Color CELL_MID = Color.web("#e5aa68");
    private static final Color CELL_DARK = Color.web("#cf873d");
    private static final Color STROKE_BASE = Color.web("#6b4a28");
    private static final Color STROKE_SELECTED = Color.web("#0f62fe");
    private static final Color NOTATION_COLOR = Color.rgb(23, 23, 23, 0.45);
    private static final Color WHITE_PIECE = Color.WHITE;
    private static final Color BLACK_PIECE = Color.web("#171717");

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

    private final Map<HexCoordinate, Point2D> cellCenters = new HashMap<>();

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
        boardCanvas.setWidth(BOARD_WIDTH);
        boardCanvas.setHeight(BOARD_HEIGHT);
        boardCanvas.setOnMouseClicked(event -> coordinateAt(event.getX(), event.getY())
                .ifPresent(this::onCellClicked));
        cellCenters.clear();
        HexBoardGeometry.displayOrder()
                .forEach(coordinate -> cellCenters.put(coordinate, centerOf(coordinate)));
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
        GraphicsContext graphics = boardCanvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, boardCanvas.getWidth(), boardCanvas.getHeight());

        HexBoardGeometry.displayOrder()
                .forEach(coordinate -> drawCell(graphics, coordinate));
        HexBoardGeometry.displayOrder()
                .forEach(coordinate -> drawPiece(graphics, coordinate));
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

    private Point2D centerOf(HexCoordinate coordinate) {
        double q = HexBoardGeometry.axialQ(coordinate);
        double r = HexBoardGeometry.axialR(coordinate);
        double x = (HEX_SIZE * 1.5 * q) + (BOARD_WIDTH / 2.0);
        double y = (-HEX_SIZE * Math.sqrt(3) * (r + q / 2.0)) + (BOARD_HEIGHT / 2.0);

        return new Point2D(x, y);
    }

    private void drawCell(GraphicsContext graphics, HexCoordinate coordinate) {
        Point2D center = cellCenters.get(coordinate);
        double[] xPoints = xPoints(center);
        double[] yPoints = yPoints(center);

        graphics.setFill(cellFill(coordinate));
        graphics.fillPolygon(xPoints, yPoints, 6);
        graphics.setStroke(STROKE_BASE);
        graphics.setLineWidth(1);
        graphics.strokePolygon(xPoints, yPoints, 6);

        if (coordinate.equals(selectedCoordinate)) {
            strokePolygon(graphics, xPoints, yPoints, STROKE_SELECTED, 3);
        }

        drawNotation(graphics, coordinate, center);
    }

    private void strokePolygon(
            GraphicsContext graphics,
            double[] xPoints,
            double[] yPoints,
            Color color,
            double lineWidth) {
        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        graphics.strokePolygon(xPoints, yPoints, 6);
    }

    private Color cellFill(HexCoordinate coordinate) {
        return switch (HexBoardGeometry.tone(coordinate)) {
            case LIGHT -> CELL_LIGHT;
            case MID -> CELL_MID;
            case DARK -> CELL_DARK;
        };
    }

    private void drawNotation(GraphicsContext graphics, HexCoordinate coordinate, Point2D center) {
        if (board.pieceAt(coordinate).isPresent()) {
            return;
        }

        graphics.setFill(NOTATION_COLOR);
        graphics.setFont(Font.font("Lato", FontWeight.BOLD, 9));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText(promotionLabel(coordinate), center.getX(), center.getY() + 13);
        if (isPromotionSquare(coordinate)) {
            drawPromotionArrow(graphics, center);
        }
    }

    private void drawPromotionArrow(GraphicsContext graphics, Point2D center) {
        double tipY = center.getY() + 2;
        double baseY = center.getY() + 9;
        double wingY = tipY + 4;
        double wingOffset = 4;

        graphics.setStroke(NOTATION_COLOR);
        graphics.setLineWidth(1.2);
        graphics.strokeLine(center.getX(), baseY, center.getX(), tipY);
        graphics.strokeLine(center.getX(), tipY, center.getX() - wingOffset, wingY);
        graphics.strokeLine(center.getX(), tipY, center.getX() + wingOffset, wingY);
    }

    private void drawPiece(GraphicsContext graphics, HexCoordinate coordinate) {
        Point2D center = cellCenters.get(coordinate);
        Optional<HexPiece> piece = board.pieceAt(coordinate);
        if (piece.isEmpty()) {
            return;
        }

        graphics.setFont(Font.font("Segoe UI Symbol", FontWeight.NORMAL, 28));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        if (piece.get().color() == HexPieceColor.WHITE) {
            graphics.setStroke(Color.web("#171717"));
            graphics.setLineWidth(0.75);
            graphics.strokeText(piece.get().displayText(), center.getX(), center.getY() - 1);
        }
        graphics.setFill(piece.get().color() == HexPieceColor.WHITE ? WHITE_PIECE : BLACK_PIECE);
        graphics.fillText(piece.get().displayText(), center.getX(), center.getY() - 1);
    }

    private Optional<HexCoordinate> coordinateAt(double x, double y) {
        return cellCenters.entrySet()
                .stream()
                .filter(entry -> containsPoint(entry.getValue(), x, y))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private boolean containsPoint(Point2D center, double x, double y) {
        double[] xPoints = xPoints(center);
        double[] yPoints = yPoints(center);
        boolean inside = false;

        for (int current = 0, previous = xPoints.length - 1; current < xPoints.length; previous = current++) {
            boolean crosses = (yPoints[current] > y) != (yPoints[previous] > y);
            double intersectX = (xPoints[previous] - xPoints[current])
                    * (y - yPoints[current])
                    / (yPoints[previous] - yPoints[current])
                    + xPoints[current];
            if (crosses && x < intersectX) {
                inside = !inside;
            }
        }

        return inside;
    }

    private double[] xPoints(Point2D center) {
        return IntStream.range(0, 6)
                .mapToDouble(index -> center.getX() + HEX_SIZE * Math.cos(hexAngle(index)))
                .toArray();
    }

    private double[] yPoints(Point2D center) {
        return IntStream.range(0, 6)
                .mapToDouble(index -> center.getY() + HEX_SIZE * Math.sin(hexAngle(index)))
                .toArray();
    }

    private double hexAngle(int index) {
        return Math.toRadians(60 * index);
    }

    private boolean isPromotionSquare(HexCoordinate coordinate) {
        return HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.WHITE)
                || HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.BLACK);
    }

    private String promotionLabel(HexCoordinate coordinate) {
        return coordinate.notation();
    }
}
