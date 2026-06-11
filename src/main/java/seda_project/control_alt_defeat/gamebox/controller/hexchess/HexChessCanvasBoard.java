package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
    private static final Color WHITE_PIECE = Color.WHITE;
    private static final Color BLACK_PIECE = Color.web("#171717");
    private static final Color WHITE_PIECE_STROKE = Color.web("#171717");
    private static final Font NOTATION_FONT = Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 9);

    @SuppressWarnings("SpellCheckingInspection")
    private static final String PIECE_FONT_FAMILY = "Segoe UI Symbol";

    private final double hexSize;
    private final double width;
    private final double height;
    private final double notationYOffset;
    private final Font pieceFont;
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
        this.pieceFont = Font.font(PIECE_FONT_FAMILY, FontWeight.NORMAL, pieceFontSize);
    }

    void attach(Canvas canvas, Consumer<HexCoordinate> onCellClicked) {
        canvas.setWidth(width);
        canvas.setHeight(height);
        canvas.setOnMouseClicked(event -> coordinateAt(event.getX(), event.getY()).ifPresent(onCellClicked));
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

    void drawNotation(GraphicsContext graphics, HexCoordinate coordinate, boolean occupied, Color notationColor) {
        if (occupied) {
            return;
        }

        Point2D center = cellCenters.get(coordinate);
        if (center == null) {
            return;
        }

        graphics.setFill(notationColor);
        graphics.setFont(NOTATION_FONT);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText(coordinate.notation(), center.getX(), center.getY() + notationYOffset);
        if (isPromotionSquare(coordinate)) {
            drawPromotionArrow(graphics, center, notationColor);
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
        double q = HexBoardGeometry.axialQ(coordinate);
        double r = HexBoardGeometry.axialR(coordinate);
        double x = (hexSize * 1.5 * q) + (width / 2.0);
        double y = (-hexSize * Math.sqrt(3) * (r + q / 2.0)) + (height / 2.0);

        return new Point2D(x, y);
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

    private void drawPromotionArrow(GraphicsContext graphics, Point2D center, Color notationColor) {
        double tipY = center.getY() + 2;
        double baseY = center.getY() + 9;
        double wingY = tipY + 4;
        double wingOffset = 4;

        graphics.setStroke(notationColor);
        graphics.setLineWidth(1.2);
        graphics.strokeLine(center.getX(), baseY, center.getX(), tipY);
        graphics.strokeLine(center.getX(), tipY, center.getX() - wingOffset, wingY);
        graphics.strokeLine(center.getX(), tipY, center.getX() + wingOffset, wingY);
    }

    private boolean isPromotionSquare(HexCoordinate coordinate) {
        return HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.WHITE)
                || HexBoardGeometry.isPromotionSquare(coordinate, HexPieceColor.BLACK);
    }

    private record CellShape(double[] xPoints, double[] yPoints) {
    }
}
