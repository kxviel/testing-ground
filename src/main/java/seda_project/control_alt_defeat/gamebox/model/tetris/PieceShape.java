package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;

import java.util.List;
import java.util.Objects;

public record PieceShape(PieceType type, String name, List<BoardPosition> cells) {

    private static final List<PieceShape> STANDARD_SHAPES = List.of(
            new PieceShape(PieceType.I, "I", List.of(
                    new BoardPosition(0, 0),
                    new BoardPosition(0, 1),
                    new BoardPosition(0, 2),
                    new BoardPosition(0, 3))),
            new PieceShape(PieceType.O, "O", List.of(
                    new BoardPosition(0, 0),
                    new BoardPosition(0, 1),
                    new BoardPosition(1, 0),
                    new BoardPosition(1, 1))),
            new PieceShape(PieceType.T, "T", List.of(
                    new BoardPosition(0, 0),
                    new BoardPosition(0, 1),
                    new BoardPosition(0, 2),
                    new BoardPosition(1, 1))),
            new PieceShape(PieceType.S, "S", List.of(
                    new BoardPosition(0, 1),
                    new BoardPosition(0, 2),
                    new BoardPosition(1, 0),
                    new BoardPosition(1, 1))),
            new PieceShape(PieceType.Z, "Z", List.of(
                    new BoardPosition(0, 0),
                    new BoardPosition(0, 1),
                    new BoardPosition(1, 1),
                    new BoardPosition(1, 2))),
            new PieceShape(PieceType.J, "J", List.of(
                    new BoardPosition(0, 0),
                    new BoardPosition(1, 0),
                    new BoardPosition(1, 1),
                    new BoardPosition(1, 2))),
            new PieceShape(PieceType.L, "L", List.of(
                    new BoardPosition(0, 2),
                    new BoardPosition(1, 0),
                    new BoardPosition(1, 1),
                    new BoardPosition(1, 2))));

    public PieceShape {
        Objects.requireNonNull(type, "type");
        name = name == null || name.isBlank() ? type.name() : name.trim();
        cells = cells == null ? List.of() : List.copyOf(cells);
    }

    public int height() {
        return cells.stream()
                .mapToInt(BoardPosition::row)
                .max()
                .orElse(0) + 1;
    }

    public int width() {
        return cells.stream()
                .mapToInt(BoardPosition::column)
                .max()
                .orElse(0) + 1;
    }

    /** Rotates the shape 90 degrees clockwise and normalizes it to the origin. */
    public PieceShape rotateClockwise90() {
        int height = height();
        List<BoardPosition> rotated = cells.stream()
                .map(cell -> new BoardPosition(cell.column(), height - 1 - cell.row()))
                .toList();
        return normalize(rotated, type, name);
    }

    private static PieceShape normalize(List<BoardPosition> rotatedCells, PieceType pieceType, String shapeName) {
        int minRow = rotatedCells.stream().mapToInt(BoardPosition::row).min().orElse(0);
        int minColumn = rotatedCells.stream().mapToInt(BoardPosition::column).min().orElse(0);
        List<BoardPosition> normalized = rotatedCells.stream()
                .map(cell -> new BoardPosition(cell.row() - minRow, cell.column() - minColumn))
                .distinct()
                .toList();
        return new PieceShape(pieceType, shapeName, normalized);
    }

    public static PieceShape standardShape(PieceType type) {
        List<PieceShape> shapes = standardShapes();
        return shapes.stream()
                .filter(shape -> shape.type() == type)
                .findFirst()
                .orElseGet(shapes::getFirst);
    }

    public static List<PieceShape> standardShapes() {
        return STANDARD_SHAPES;
    }
}
