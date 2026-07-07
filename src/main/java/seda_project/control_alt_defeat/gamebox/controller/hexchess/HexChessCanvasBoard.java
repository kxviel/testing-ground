package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Transform;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexBoardGeometry;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexCoordinate;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
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

    private static final String FONT_FAMILY = "Source Sans 3";

    private final double hexSize;
    private final double width;
    private final double height;
    private final double notationYOffset;
    private final double boardOffsetX;
    private final double boardOffsetY;
    private final Font notationFont;
    private final Font promotionFont;
    private final double pieceSize;
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
        BoardBounds bounds = computeBoardBounds(hexSize);
        this.boardOffsetX = (width - bounds.width()) / 2.0 - bounds.minX();
        this.boardOffsetY = (height - bounds.height()) / 2.0 - bounds.minY();
        this.notationFont = Font.font(FONT_FAMILY, FontWeight.BOLD, Math.max(9, hexSize * 0.41) + 2);
        this.promotionFont = Font.font(FONT_FAMILY, FontWeight.BOLD, Math.max(16, hexSize * 0.73) + 2);
        this.pieceSize = Math.min(pieceSize + 2, hexSize * 1.2);
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

        Image image = PieceSvgCache.image(piece);
        double drawSize = Math.min(pieceSize, hexSize * 1.24);
        graphics.drawImage(image, center.getX() - drawSize / 2.0, center.getY() - drawSize / 2.0, drawSize, drawSize);
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

    private static final class PieceSvgCache {

        private static final double SNAPSHOT_SIZE = 256.0;
        private static final String PIECE_ROOT = "/icons/chess_pieces/";
        private static final Map<HexPieceColor, Map<HexPieceType, Image>> IMAGES = new EnumMap<>(HexPieceColor.class);

        private PieceSvgCache() {
        }

        static Image image(HexPiece piece) {
            Map<HexPieceType, Image> colorImages = IMAGES.computeIfAbsent(piece.color(), ignored -> new EnumMap<>(HexPieceType.class));
            return colorImages.computeIfAbsent(piece.type(), type -> render(piece.color(), type));
        }

        private static Image render(HexPieceColor color, HexPieceType type) {
            String fileName = fileName(color, type);
            try (InputStream input = HexChessCanvasBoard.class.getResourceAsStream(PIECE_ROOT + fileName)) {
                if (input == null) {
                    throw new IllegalStateException("Missing chess piece SVG: " + fileName);
                }

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

                Document document = factory.newDocumentBuilder().parse(input);
                Element svg = document.getDocumentElement();
                Group group = new Group();
                addPaths(group, svg);

                SvgViewBox viewBox = viewBox(svg);
                double scale = SNAPSHOT_SIZE / Math.max(viewBox.width(), viewBox.height());
                double scaledWidth = viewBox.width() * scale;
                double scaledHeight = viewBox.height() * scale;
                double offsetX = (SNAPSHOT_SIZE - scaledWidth) / 2.0 - viewBox.minX() * scale;
                double offsetY = (SNAPSHOT_SIZE - scaledHeight) / 2.0 - viewBox.minY() * scale;
                group.getTransforms().addAll(Transform.translate(offsetX, offsetY), Transform.scale(scale, scale));

                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);
                parameters.setViewport(new Rectangle2D(0, 0, SNAPSHOT_SIZE, SNAPSHOT_SIZE));

                return group.snapshot(parameters, new WritableImage((int) SNAPSHOT_SIZE, (int) SNAPSHOT_SIZE));
            } catch (IOException | ParserConfigurationException | SAXException e) {
                throw new IllegalStateException("Unable to load chess piece SVG: " + type, e);
            }
        }

        private static void addPaths(Group group, Element svg) {
            NodeList paths = svg.getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++) {
                Element element = (Element) paths.item(i);
                SVGPath path = new SVGPath();
                path.setContent(element.getAttribute("d"));
                path.setFill(fillFor(element));
                path.setOpacity(opacityFor(element));
                path.setStroke(Color.TRANSPARENT);
                applyTranslate(path, element.getAttribute("transform"));
                group.getChildren().add(path);
            }
        }

        private static Color fillFor(Element element) {
            String fillValue = element.getAttribute("fill");
            if ("none".equalsIgnoreCase(fillValue)) {
                return Color.TRANSPARENT;
            }

            return parseColor(fillValue).orElse(Color.web("#475B63"));
        }

        private static Optional<Color> parseColor(String value) {
            if (value == null || value.isBlank() || "none".equals(value)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Color.web(value));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        private static double opacityFor(Element element) {
            return parseOpacity(element.getAttribute("opacity"))
                    * parseOpacity(element.getAttribute("fill-opacity"));
        }

        private static double parseOpacity(String value) {
            if (value == null || value.isBlank()) {
                return 1.0;
            }
            try {
                return Math.clamp(Double.parseDouble(value), 0.0, 1.0);
            } catch (NumberFormatException e) {
                return 1.0;
            }
        }

        private static void applyTranslate(SVGPath path, String transform) {
            if (transform == null || !transform.startsWith("translate(") || !transform.endsWith(")")) {
                return;
            }

            String[] parts = transform.substring("translate(".length(), transform.length() - 1)
                    .split("[,\\s]+");
            if (parts.length == 0) {
                return;
            }

            try {
                double x = Double.parseDouble(parts[0]);
                double y = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.0;
                path.getTransforms().add(Transform.translate(x, y));
            } catch (NumberFormatException ignored) {
            }
        }

        private static SvgViewBox viewBox(Element svg) {
            String[] parts = svg.getAttribute("viewBox").trim().split("\\s+");
            if (parts.length == 4) {
                try {
                    return new SvgViewBox(
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                } catch (NumberFormatException ignored) {
                }
            }

            return new SvgViewBox(0, 0, 16, 32);
        }

        private static String fileName(HexPieceColor color, HexPieceType type) {
            String prefix = color == HexPieceColor.WHITE ? "W_" : "B_";
            return switch (type) {
                case KING -> prefix + "King.svg";
                case QUEEN -> prefix + "Queen.svg";
                case ROOK -> prefix + "Rook.svg";
                case BISHOP -> prefix + "Bishop.svg";
                case KNIGHT -> prefix + "Knight.svg";
                case PAWN, CUSTOM -> prefix + "Pawn.svg";
            };
        }

        private record SvgViewBox(double minX, double minY, double width, double height) {
        }
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
