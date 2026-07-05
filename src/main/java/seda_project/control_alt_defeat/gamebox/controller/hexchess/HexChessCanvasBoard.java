package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoardGeometry;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

final class HexChessCanvasBoard {

    private static final int CORNER_COUNT = 6;
    private static final double CORNER_ANGLE_DEGREES = 60.0;
    private static final double HEX_APOTHEM_RATIO = Math.sqrt(3.0) / 2.0;
    public static final Color CELL_LIGHT = Color.web("#f7c895");
    public static final Color CELL_MID = Color.web("#e5aa68");
    public static final Color CELL_DARK = Color.web("#cf873d");
    public static final Color STROKE_BASE = Color.web("#6b4a28");
    public static final Color STROKE_SELECTED = Color.web("#0f62fe");
    public static final Color NOTATION_COLOR = Color.rgb(23, 23, 23, 0.45);
    private static final Color WHITE_PIECE = Color.WHITE;
    private static final Color BLACK_PIECE = Color.web("#31111D");
    private static final Color WHITE_PIECE_STROKE = Color.web("#31111D");

    @SuppressWarnings("SpellCheckingInspection")
    private static final String PIECE_FONT_FAMILY = "Segoe UI Symbol";

    private final double hexSize;
    private final double width;
    private final double height;
    private final double notationYOffset;
    private final double boardOffsetX;
    private final double boardOffsetY;
    private final Font notationFont;
    private final Font pieceFont;
    private final Font promotionFont;
    private final Map<HexCoordinate, Point2D> cellCenters = new HashMap<>();

    HexChessCanvasBoard(
            double hexSize,
            double width,
            double height,
            double notationYOffset,
            double pieceFontSize) {
        this.hexSize = hexSize;
        this.width = width;
        this.height = height;
        this.notationYOffset = notationYOffset;
        BoardBounds bounds = computeBoardBounds(hexSize);
        this.boardOffsetX = (width - bounds.width()) / 2.0 - bounds.minX();
        this.boardOffsetY = (height - bounds.height()) / 2.0 - bounds.minY();
        this.notationFont = Font.font("Inter Variable", FontWeight.BOLD, Math.max(9, hexSize * 0.41) + 2);
        this.pieceFont = Font.font(PIECE_FONT_FAMILY, FontWeight.NORMAL, pieceFontSize + 2);
        this.promotionFont = Font.font("Inter Variable", FontWeight.BOLD, Math.max(16, hexSize * 0.73) + 2);
    }

    void attach(Canvas canvas, Consumer<HexCoordinate> onCellClicked) {
        attach(canvas, (coordinate, button) -> {
            if (button == MouseButton.PRIMARY) {
                onCellClicked.accept(coordinate);
            }
        });
    }

    void attach(Canvas canvas, BiConsumer<HexCoordinate, MouseButton> onCellClicked) {
        canvas.setWidth(width);
        canvas.setHeight(height);
        canvas.setFocusTraversable(true);
        canvas.setOnMouseClicked(event -> coordinateAt(event.getX(), event.getY())
                .ifPresent(coordinate -> onCellClicked.accept(coordinate, event.getButton())));
        cellCenters.clear();
        HexBoardGeometry.displayOrder()
                .forEach(coordinate -> cellCenters.put(coordinate, centerOf(coordinate)));
    }

    void redraw(
            Canvas canvas,
            BiConsumer<GraphicsContext, HexCoordinate> drawCell,
            BiConsumer<GraphicsContext, HexCoordinate> drawPiece) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        HexBoardGeometry.displayOrder().forEach(coordinate -> drawCell.accept(graphics, coordinate));
        HexBoardGeometry.displayOrder().forEach(coordinate -> drawPiece.accept(graphics, coordinate));
    }

    void fillCell(GraphicsContext graphics, HexCoordinate coordinate, Color fill) {
        cellShape(coordinate).ifPresent(shape -> {
            graphics.setFill(fill);
            graphics.fillPolygon(shape.xPoints(), shape.yPoints(), CORNER_COUNT);
        });
    }

    void strokeCell(GraphicsContext graphics, HexCoordinate coordinate, Color color, double lineWidth) {
        cellShape(coordinate).ifPresent(shape -> {
            graphics.setStroke(color);
            graphics.setLineWidth(lineWidth);
            graphics.strokePolygon(shape.xPoints(), shape.yPoints(), CORNER_COUNT);
        });
    }

    static Color baseFill(HexCoordinate coordinate) {
        return switch (HexBoardGeometry.tone(coordinate)) {
            case LIGHT -> CELL_LIGHT;
            case MID -> CELL_MID;
            case DARK -> CELL_DARK;
        };
    }

    void drawNotation(GraphicsContext graphics, HexCoordinate coordinate, boolean occupied, Color notationColor) {
        if (occupied) {
            return;
        }

        Point2D center = cellCenters.get(coordinate);
        if (center == null) {
            return;
        }

        graphics.setFill(notationColor);
        graphics.setFont(notationFont);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText(coordinate.notation(), center.getX(), center.getY() + notationYOffset);
        if (isPromotionSquare(coordinate)) {
            drawPromotionStar(graphics, center, notationColor);
        }
    }

    void drawPiece(GraphicsContext graphics, HexCoordinate coordinate, HexPiece piece) {
        Point2D center = cellCenters.get(coordinate);
        if (piece == null || center == null) {
            return;
        }

        graphics.setFont(pieceFont);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        if (piece.color() == HexPieceColor.WHITE) {
            graphics.setStroke(WHITE_PIECE_STROKE);
            graphics.setLineWidth(0.75);
            graphics.strokeText(piece.displayText(), center.getX(), center.getY() - 1);
        }
        graphics.setFill(piece.color() == HexPieceColor.WHITE ? WHITE_PIECE : BLACK_PIECE);
        graphics.fillText(piece.displayText(), center.getX(), center.getY() - 1);
    }

    private Optional<CellShape> cellShape(HexCoordinate coordinate) {
        return Optional.ofNullable(cellCenters.get(coordinate))
                .map(center -> new CellShape(xPoints(center), yPoints(center)));
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

    private Point2D centerOf(HexCoordinate coordinate) {
        double x = rawX(coordinate, hexSize) + boardOffsetX;
        double y = rawY(coordinate, hexSize) + boardOffsetY;

        return new Point2D(x, y);
    }

    private static BoardBounds computeBoardBounds(double hexSize) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double yRadius = hexSize * HEX_APOTHEM_RATIO;

        for (HexCoordinate coordinate : HexBoardGeometry.displayOrder()) {
            double x = rawX(coordinate, hexSize);
            double y = rawY(coordinate, hexSize);
            minX = Math.min(minX, x - hexSize);
            maxX = Math.max(maxX, x + hexSize);
            minY = Math.min(minY, y - yRadius);
            maxY = Math.max(maxY, y + yRadius);
        }

        return new BoardBounds(minX, maxX, minY, maxY);
    }

    static double boardWidth(double hexSize) {
        return computeBoardBounds(hexSize).width();
    }

    static double boardHeight(double hexSize) {
        return computeBoardBounds(hexSize).height();
    }

    private static double rawX(HexCoordinate coordinate, double hexSize) {
        return hexSize * 1.5 * HexBoardGeometry.axialQ(coordinate);
    }

    private static double rawY(HexCoordinate coordinate, double hexSize) {
        double q = HexBoardGeometry.axialQ(coordinate);
        double r = HexBoardGeometry.axialR(coordinate);
        return -hexSize * Math.sqrt(3) * (r + q / 2.0);
    }

    private double[] xPoints(Point2D center) {
        return IntStream.range(0, CORNER_COUNT)
                .mapToDouble(index -> center.getX() + hexSize * Math.cos(hexAngle(index)))
                .toArray();
    }

    private double[] yPoints(Point2D center) {
        return IntStream.range(0, CORNER_COUNT)
                .mapToDouble(index -> center.getY() + hexSize * Math.sin(hexAngle(index)))
                .toArray();
    }

    private double hexAngle(int index) {
        return Math.toRadians(CORNER_ANGLE_DEGREES * index);
    }

    private void drawPromotionStar(GraphicsContext graphics, Point2D center, Color notationColor) {
        graphics.setFill(notationColor);
        graphics.setFont(promotionFont);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText("*", center.getX(), center.getY() + 1);
    }

    private boolean isPromotionSquare(HexCoordinate coordinate) {
        return HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.WHITE)
                || HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.BLACK);
    }

    private record CellShape(double[] xPoints, double[] yPoints) {
    }

    private record BoardBounds(double minX, double maxX, double minY, double maxY) {
        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }
    }
}
