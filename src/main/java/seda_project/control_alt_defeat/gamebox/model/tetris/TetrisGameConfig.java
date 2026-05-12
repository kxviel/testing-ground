package seda_project.control_alt_defeat.gamebox.model.tetris;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record TetrisGameConfig(List<String> pieces, List<PieceShape> customPieces) {

    public TetrisGameConfig {
        pieces = normalizePieces(pieces);
        customPieces = customPieces == null ? List.of() : List.copyOf(customPieces);
    }

    public TetrisGameConfig(List<String> pieces) {
        this(pieces, List.of());
    }

    public static TetrisGameConfig defaultConfig() {
        return new TetrisGameConfig(List.of("Standard"));
    }

    public boolean hasPieces() {
        return pieces != null && !pieces.isEmpty();
    }

    public String displayText() {
        String text = String.join(", ", pieces);

        if (customPieces.isEmpty()) {
            return text;
        }

        return text + " (" + customPieces.size() + " custom)";
    }

    public List<PieceShape> availableShapes() {
        List<PieceShape> shapes = new ArrayList<>();

        if (pieces.contains("Standard")) {
            shapes.addAll(PieceShape.standardShapes());
        }
        if (pieces.contains("Custom")) {
            shapes.addAll(customPieces);
        }

        return shapes.isEmpty() ? PieceShape.standardShapes() : List.copyOf(shapes);
    }

    public String serialize() {
        if (customPieces.isEmpty()) {
            return String.join(",", pieces);
        }

        return String.join(",", pieces) + "~" + customPieces.stream()
                .map(this::serializeShape)
                .collect(Collectors.joining(";"));
    }

    public static TetrisGameConfig deserialize(String value) {
        if (value == null || value.isBlank()) {
            return defaultConfig();
        }

        String[] sections = value.split("~", 2);
        List<String> pieces = normalizePieces(Arrays.stream(sections[0].split(","))
                .map(String::trim)
                .filter(piece -> !piece.isEmpty())
                .toList());
        List<PieceShape> customPieces = sections.length == 2 ? parseCustomPieces(sections[1]) : List.of();

        return pieces.isEmpty() ? defaultConfig() : new TetrisGameConfig(pieces, customPieces);
    }

    private static List<String> normalizePieces(List<String> pieces) {
        if (pieces == null) {
            return List.of();
        }

        return pieces.stream()
                .map(piece -> piece == null ? "" : piece.trim())
                .filter(piece -> piece.equals("Standard") || piece.equals("Custom"))
                .distinct()
                .toList();
    }

    private String serializeShape(PieceShape shape) {
        return shape.cells().stream()
                .map(cell -> cell.row() + "." + cell.column())
                .collect(Collectors.joining("_"));
    }

    private static List<PieceShape> parseCustomPieces(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<PieceShape> shapes = new ArrayList<>();
        String[] shapeValues = value.split(";");

        for (String shapeValue : shapeValues) {
            List<BoardPosition> cells = parseCells(shapeValue);
            if (!cells.isEmpty()) {
                shapes.add(CustomPieceBuilder.build("Custom " + (shapes.size() + 1), cells));
            }
        }

        return List.copyOf(shapes);
    }

    private static List<BoardPosition> parseCells(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<BoardPosition> cells = new ArrayList<>();
        String[] cellValues = value.split("_");

        for (String cellValue : cellValues) {
            String[] parts = cellValue.split("\\.", 2);
            if (parts.length != 2) {
                continue;
            }

            try {
                cells.add(new BoardPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
            }
        }

        return cells;
    }
}
