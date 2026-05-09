package seda_project.control_alt_defeat.gamebox.model.tetris;

import java.util.List;
import java.util.Objects;

public record PieceShape(PieceType type, String name, List<BoardPosition> cells) {

    public PieceShape {
        Objects.requireNonNull(type, "type");
        name = name == null || name.isBlank() ? type.name() : name.trim();
        cells = cells == null ? List.of() : List.copyOf(cells);
    }

    public int height() {
        int max = 0;
        for (BoardPosition cell : cells) {
            max = Math.max(max, cell.row());
        }
        return max + 1;
    }

    public int width() {
        int max = 0;
        for (BoardPosition cell : cells) {
            max = Math.max(max, cell.column());
        }
        return max + 1;
    }

    public static PieceShape standardShape(PieceType type) {
        for (PieceShape shape : standardShapes()) {
            if (shape.type() == type) {
                return shape;
            }
        }

        return standardShapes().get(0);
    }

    public static List<PieceShape> standardShapes() {
        return List.of(
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
    }
}
