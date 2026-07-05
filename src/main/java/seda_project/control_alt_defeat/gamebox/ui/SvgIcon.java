package seda_project.control_alt_defeat.gamebox.ui;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

public class SvgIcon extends StackPane {

    private static final double ICON_SIZE = 24.0;
    private static final String ICON_ROOT = "/icons/";
    private static final Map<String, String> ICON_FILES = Map.ofEntries(
            Map.entry("bot", "bot.svg"),
            Map.entry("chevron-right", "chevron-right.svg"),
            Map.entry("custom", "custom.svg"),
            Map.entry("exit", "exit.svg"),
            Map.entry("lan", "lan.svg"),
            Map.entry("local", "local.svg"),
            Map.entry("play", "play.svg"),
            Map.entry("player-one", "player 1.svg"),
            Map.entry("player-two", "player 2.svg"));

    private String icon;
    private double fitSize = ICON_SIZE;
    private boolean themed = true;

    public SvgIcon() {
        setAlignment(Pos.CENTER);
        setMouseTransparent(true);
        resizeBox();
        getStyleClass().add("svg-icon");
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
        rebuild();
    }

    public double getFitSize() {
        return fitSize;
    }

    public void setFitSize(double fitSize) {
        this.fitSize = Math.max(1.0, fitSize);
        resizeBox();
        rebuild();
    }

    public boolean isThemed() {
        return themed;
    }

    public void setThemed(boolean themed) {
        this.themed = themed;
        rebuild();
    }

    private void rebuild() {
        getChildren().clear();
        if (icon == null || icon.isBlank()) {
            return;
        }

        try (InputStream input = SvgIcon.class.getResourceAsStream(ICON_ROOT + iconFile(icon))) {
            if (input == null) {
                throw new IllegalArgumentException("Missing icon resource: " + icon);
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(input);
            Element svg = document.getDocumentElement();
            Group group = new Group();
            group.setMouseTransparent(true);
            addShapes(group, svg, SvgStyle.from(svg));

            double scale = fitSize / Math.max(1.0, Math.max(viewBoxWidth(svg), viewBoxHeight(svg)));
            group.setScaleX(scale);
            group.setScaleY(scale);

            getChildren().setAll(group);
        } catch (IllegalArgumentException | IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Unable to load SVG icon: " + icon, e);
        }
    }

    private void resizeBox() {
        setMinSize(fitSize, fitSize);
        setPrefSize(fitSize, fitSize);
        setMaxSize(fitSize, fitSize);
    }

    private static String iconFile(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".svg")) {
            return normalized;
        }
        return ICON_FILES.getOrDefault(normalized, normalized + ".svg");
    }

    private void addShapes(Group group, Element parent, SvgStyle inherited) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element element)) {
                continue;
            }

            SvgStyle style = inherited.apply(element);
            if ("g".equals(element.getTagName())) {
                addShapes(group, element, style);
                continue;
            }

            Shape shape = shapeFor(element);
            if (shape != null) {
                styleShape(shape, style);
                group.getChildren().add(shape);
            }
        }
    }

    private static Shape shapeFor(Element element) {
        return switch (element.getTagName()) {
            case "path" -> path(element);
            case "rect" -> rect(element);
            case "circle" -> circle(element);
            default -> null;
        };
    }

    private static SVGPath path(Element element) {
        SVGPath path = new SVGPath();
        path.setContent(element.getAttribute("d"));
        return path;
    }

    private static Rectangle rect(Element element) {
        Rectangle rect = new Rectangle(
                number(element, "x"),
                number(element, "y"),
                number(element, "width"),
                number(element, "height"));
        double rx = number(element, "rx");
        if (rx > 0) {
            rect.setArcWidth(rx * 2.0);
            rect.setArcHeight(rx * 2.0);
        }
        return rect;
    }

    private static Circle circle(Element element) {
        return new Circle(
                number(element, "cx"),
                number(element, "cy"),
                number(element, "r"));
    }

    private void styleShape(Shape shape, SvgStyle style) {
        shape.setSmooth(themed);
        shape.setStrokeWidth(style.strokeWidth());
        shape.setStrokeLineCap(lineCap(style.strokeLineCap()));
        shape.setStrokeLineJoin(lineJoin(style.strokeLineJoin()));
        if (themed) {
            styleThemedShape(shape);
        } else {
            styleColoredShape(shape, style);
        }
    }

    private static void styleThemedShape(Shape shape) {
        shape.getStyleClass().add("svg-icon-shape");
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(Color.BLACK);
    }

    private static void styleColoredShape(Shape shape, SvgStyle style) {
        Paint fill = color(style.fill(), style.fillOpacity());
        Paint stroke = color(style.stroke(), style.strokeOpacity());
        shape.setFill(fill == null ? Color.TRANSPARENT : fill);
        shape.setStroke(stroke);
    }

    private static StrokeLineCap lineCap(String value) {
        return "round".equals(value) ? StrokeLineCap.ROUND : StrokeLineCap.BUTT;
    }

    private static StrokeLineJoin lineJoin(String value) {
        return "round".equals(value) ? StrokeLineJoin.ROUND : StrokeLineJoin.MITER;
    }

    private static Paint color(String value, double opacity) {
        if (value == null || value.isBlank() || "none".equals(value)) {
            return null;
        }
        String resolved = "currentColor".equals(value) ? "#000000" : value;
        try {
            return Color.web(resolved, opacity);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double number(Element element, String name) {
        return parse(element.getAttribute(name), 0.0);
    }

    private static double viewBoxWidth(Element svg) {
        double[] viewBox = viewBox(svg);
        return viewBox.length == 4 ? viewBox[2] : parse(svg.getAttribute("width"), ICON_SIZE);
    }

    private static double viewBoxHeight(Element svg) {
        double[] viewBox = viewBox(svg);
        return viewBox.length == 4 ? viewBox[3] : parse(svg.getAttribute("height"), ICON_SIZE);
    }

    private static double[] viewBox(Element svg) {
        String viewBox = svg.getAttribute("viewBox");
        if (viewBox == null || viewBox.isBlank()) {
            return new double[0];
        }
        String[] parts = viewBox.trim().split("\\s+");
        if (parts.length != 4) {
            return new double[0];
        }
        return new double[] {
                parse(parts[0], 0.0),
                parse(parts[1], 0.0),
                parse(parts[2], ICON_SIZE),
                parse(parts[3], ICON_SIZE)
        };
    }

    private static double parse(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record SvgStyle(
            String fill,
            double fillOpacity,
            String stroke,
            double strokeOpacity,
            double strokeWidth,
            String strokeLineCap,
            String strokeLineJoin) {

        static SvgStyle from(Element svg) {
            return new SvgStyle(
                    value(svg, "fill", "black"),
                    number(svg, "fill-opacity", 1.0),
                    value(svg, "stroke", "none"),
                    number(svg, "stroke-opacity", 1.0),
                    number(svg, "stroke-width", 2.0),
                    value(svg, "stroke-linecap", "butt"),
                    value(svg, "stroke-linejoin", "miter"));
        }

        SvgStyle apply(Element element) {
            return new SvgStyle(
                    value(element, "fill", fill),
                    number(element, "fill-opacity", fillOpacity),
                    value(element, "stroke", stroke),
                    number(element, "stroke-opacity", strokeOpacity),
                    number(element, "stroke-width", strokeWidth),
                    value(element, "stroke-linecap", strokeLineCap),
                    value(element, "stroke-linejoin", strokeLineJoin));
        }

        private static String value(Element element, String name, String fallback) {
            String value = element.getAttribute(name);
            return value == null || value.isBlank() ? fallback : value;
        }

        private static double number(Element element, String name, double fallback) {
            return parse(element.getAttribute(name), fallback);
        }
    }
}
