package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class HexChessCanvasBoard {

    private static final int HEX_CORNERS = 6;
    private static final double HEX_ANGLE_STEP = 60.0;
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double HEX_APOTHEM_RATIO = SQRT_3 / 2.0;
    private static final double MAX_PIECE_SIZE_RATIO = 1.5;
    private static final String FONT_FAMILY = "Source Sans 3";

    public static final Color CELL_LIGHT = Color.web("#f7c895");
    public static final Color CELL_MID = Color.web("#e5aa68");
    public static final Color CELL_DARK = Color.web("#cf873d");
    public static final Color STROKE_BASE = Color.web("#6b4a28");
    public static final Color STROKE_SELECTED = Color.web("#0f62fe");
    public static final Color NOTATION_COLOR = Color.rgb(23, 23, 23, 0.45);

    private final double hexSize;
    private final double width;
    private final double height;
    private final double notationYOffset;
    private final double boardOffsetX;
    private final double boardOffsetY;
    private final double pieceSize;
    private final Font notationFont;
    private final Font promotionFont;
    private final Map<HexCoordinate, Point2D> cellCenters = new HashMap<>();

    HexChessCanvasBoard(
            double hexSize,
            double width,
            double height,
            double notationYOffset,
            double pieceSize) {
        this.hexSize = hexSize;
        this.width = width;
        this.height = height;
        this.notationYOffset = notationYOffset;
        this.pieceSize = Math.min(pieceSize + 2.0, hexSize * MAX_PIECE_SIZE_RATIO);

        BoardBounds bounds = computeBoardBounds(hexSize);
        this.boardOffsetX = (width - bounds.width()) / 2.0 - bounds.minX();
        this.boardOffsetY = (height - bounds.height()) / 2.0 - bounds.minY();
        this.notationFont = Font.font(FONT_FAMILY, FontWeight.BOLD, Math.max(9, hexSize * 0.41) + 2);
        this.promotionFont = Font.font(FONT_FAMILY, FontWeight.BOLD, Math.max(16, hexSize * 0.73) + 2);
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
        canvas.setOnMouseClicked(event -> {
            HexCoordinate coordinate = coordinateAt(event.getX(), event.getY());
            if (coordinate != null) {
                onCellClicked.accept(coordinate, event.getButton());
            }
        });

        cellCenters.clear();
        for (HexCoordinate coordinate : HexBoardGeometry.displayOrder()) {
            cellCenters.put(coordinate, centerOf(coordinate));
        }
    }

    void redraw(
            Canvas canvas,
            BiConsumer<GraphicsContext, HexCoordinate> drawCell,
            BiConsumer<GraphicsContext, HexCoordinate> drawPiece) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (HexCoordinate coordinate : HexBoardGeometry.displayOrder()) {
            drawCell.accept(graphics, coordinate);
        }
        for (HexCoordinate coordinate : HexBoardGeometry.displayOrder()) {
            drawPiece.accept(graphics, coordinate);
        }
    }

    void fillCell(GraphicsContext graphics, HexCoordinate coordinate, Color fill) {
        CellShape shape = cellShape(coordinate);
        if (shape == null) {
            return;
        }

        graphics.setFill(fill);
        graphics.fillPolygon(shape.xPoints(), shape.yPoints(), HEX_CORNERS);
    }

    void strokeCell(GraphicsContext graphics, HexCoordinate coordinate, Color color, double lineWidth) {
        CellShape shape = cellShape(coordinate);
        if (shape == null) {
            return;
        }

        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        graphics.strokePolygon(shape.xPoints(), shape.yPoints(), HEX_CORNERS);
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

        drawCenteredText(graphics, coordinate.notation(), center, notationFont, notationColor, notationYOffset);
        if (isPromotionSquare(coordinate)) {
            drawCenteredText(graphics, "*", center, promotionFont, notationColor, 1);
        }
    }

    void drawPiece(GraphicsContext graphics, HexCoordinate coordinate, HexPiece piece) {
        Point2D center = cellCenters.get(coordinate);
        if (piece == null || center == null) {
            return;
        }

        Image image = HexChessPieceImages.image(piece);
        double x = center.getX() - pieceSize / 2.0;
        double y = center.getY() - pieceSize / 2.0;
        graphics.drawImage(image, x, y, pieceSize, pieceSize);
    }

    static double boardWidth(double hexSize) {
        return computeBoardBounds(hexSize).width();
    }

    static double boardHeight(double hexSize) {
        return computeBoardBounds(hexSize).height();
    }

    private void drawCenteredText(
            GraphicsContext graphics,
            String text,
            Point2D center,
            Font font,
            Color color,
            double yOffset) {
        graphics.setFill(color);
        graphics.setFont(font);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText(text, center.getX(), center.getY() + yOffset);
    }

    private CellShape cellShape(HexCoordinate coordinate) {
        Point2D center = cellCenters.get(coordinate);
        return center == null ? null : polygonAround(center);
    }

    private CellShape polygonAround(Point2D center) {
        double[] xPoints = new double[HEX_CORNERS];
        double[] yPoints = new double[HEX_CORNERS];
        for (int i = 0; i < HEX_CORNERS; i++) {
            double angle = Math.toRadians(HEX_ANGLE_STEP * i);
            xPoints[i] = center.getX() + hexSize * Math.cos(angle);
            yPoints[i] = center.getY() + hexSize * Math.sin(angle);
        }
        return new CellShape(xPoints, yPoints);
    }

    private HexCoordinate coordinateAt(double x, double y) {
        for (Map.Entry<HexCoordinate, Point2D> entry : cellCenters.entrySet()) {
            if (containsPoint(entry.getValue(), x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean containsPoint(Point2D center, double x, double y) {
        CellShape shape = polygonAround(center);
        double[] xPoints = shape.xPoints();
        double[] yPoints = shape.yPoints();
        boolean inside = false;

        for (int current = 0, previous = HEX_CORNERS - 1; current < HEX_CORNERS; previous = current++) {
            boolean crossesY = (yPoints[current] > y) != (yPoints[previous] > y);
            if (!crossesY) {
                continue;
            }

            double intersectX = (xPoints[previous] - xPoints[current])
                    * (y - yPoints[current])
                    / (yPoints[previous] - yPoints[current])
                    + xPoints[current];
            if (x < intersectX) {
                inside = !inside;
            }
        }

        return inside;
    }

    private Point2D centerOf(HexCoordinate coordinate) {
        return new Point2D(
                rawX(coordinate, hexSize) + boardOffsetX,
                rawY(coordinate, hexSize) + boardOffsetY);
    }

    private boolean isPromotionSquare(HexCoordinate coordinate) {
        return HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.WHITE)
                || HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.BLACK);
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

    private static double rawX(HexCoordinate coordinate, double hexSize) {
        return hexSize * 1.5 * HexBoardGeometry.axialQ(coordinate);
    }

    private static double rawY(HexCoordinate coordinate, double hexSize) {
        double q = HexBoardGeometry.axialQ(coordinate);
        double r = HexBoardGeometry.axialR(coordinate);
        return -hexSize * SQRT_3 * (r + q / 2.0);
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
