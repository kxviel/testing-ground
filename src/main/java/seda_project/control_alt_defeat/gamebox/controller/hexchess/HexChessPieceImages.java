package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Transform;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

final class HexChessPieceImages {

    private static final double IMAGE_SIZE = 256.0;
    private static final String PIECE_ROOT = "/icons/chess_pieces/";
    private static final Map<HexPieceColor, Map<HexPieceType, Image>> CACHE = new EnumMap<>(HexPieceColor.class);

    private HexChessPieceImages() {
    }

    static Image image(HexPiece piece) {
        Map<HexPieceType, Image> colorImages = CACHE.computeIfAbsent(piece.color(), ignored -> new EnumMap<>(HexPieceType.class));
        return colorImages.computeIfAbsent(piece.type(), type -> load(piece.color(), type));
    }

    private static Image load(HexPieceColor color, HexPieceType type) {
        String fileName = fileName(color, type);
        try (InputStream input = HexChessPieceImages.class.getResourceAsStream(PIECE_ROOT + fileName)) {
            if (input == null) {
                throw new IllegalStateException("Missing chess piece SVG: " + fileName);
            }

            Element svg = parse(input).getDocumentElement();
            Group group = paths(svg);
            ViewBox viewBox = ViewBox.from(svg);
            double scale = IMAGE_SIZE / Math.max(viewBox.width(), viewBox.height());
            group.getTransforms().setAll(
                    Transform.translate((IMAGE_SIZE - viewBox.width() * scale) / 2.0 - viewBox.minX() * scale,
                            (IMAGE_SIZE - viewBox.height() * scale) / 2.0 - viewBox.minY() * scale),
                    Transform.scale(scale, scale));

            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            parameters.setViewport(new Rectangle2D(0, 0, IMAGE_SIZE, IMAGE_SIZE));
            return group.snapshot(parameters, new WritableImage((int) IMAGE_SIZE, (int) IMAGE_SIZE));
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Unable to load chess piece SVG: " + fileName, e);
        }
    }

    private static Document parse(InputStream input) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(input);
    }

    private static Group paths(Element svg) {
        Group group = new Group();
        NodeList paths = svg.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            group.getChildren().add(path((Element) paths.item(i)));
        }
        return group;
    }

    private static SVGPath path(Element element) {
        SVGPath path = new SVGPath();
        path.setContent(element.getAttribute("d"));
        path.setFill(color(element.getAttribute("fill")));
        path.setStroke(Color.TRANSPARENT);
        return path;
    }

    private static Color color(String fill) {
        if (fill == null || fill.isBlank() || "none".equalsIgnoreCase(fill)) {
            return Color.TRANSPARENT;
        }
        try {
            return Color.web(fill);
        } catch (IllegalArgumentException e) {
            return Color.web("#475B63");
        }
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

    private record ViewBox(double minX, double minY, double width, double height) {

        static ViewBox from(Element svg) {
            String[] parts = svg.getAttribute("viewBox").trim().split("\\s+");
            if (parts.length == 4) {
                try {
                    return new ViewBox(
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                } catch (NumberFormatException ignored) {
                }
            }
            return new ViewBox(0, 0, 16, 32);
        }
    }
}
