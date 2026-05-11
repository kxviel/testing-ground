package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TetrisPiece(PieceShape shape, BoardPosition position, Rotation rotation, int colorIndex) {

    public TetrisPiece {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(position, "position");
        rotation = rotation == null ? Rotation.SPAWN : rotation;
        colorIndex = Math.max(-1, colorIndex);
    }

    public TetrisPiece(PieceShape shape, BoardPosition position, Rotation rotation) {
        this(shape, position, rotation, -1);
    }

    public TetrisPiece withPosition(BoardPosition nextPosition) {
        return new TetrisPiece(shape, nextPosition, rotation, colorIndex);
    }

    public TetrisPiece withRotation(Rotation nextRotation) {
        return new TetrisPiece(shape, position, nextRotation, colorIndex);
    }

    public TetrisPiece withColorIndex(int nextColorIndex) {
        return new TetrisPiece(shape, position, rotation, nextColorIndex);
    }

    public List<BoardPosition> boardCells() {
        List<BoardPosition> cells = new ArrayList<>();

        for (BoardPosition cell : shape.cells()) {
            BoardPosition rotated = rotate(cell);
            cells.add(new BoardPosition(
                    position.row() + rotated.row(),
                    position.column() + rotated.column()));
        }

        return cells;
    }

    private BoardPosition rotate(BoardPosition cell) {
        int height = shape.height();
        int width = shape.width();

        return switch (rotation) {
            case SPAWN -> cell;
            case RIGHT -> new BoardPosition(cell.column(), height - 1 - cell.row());
            case HALF -> new BoardPosition(height - 1 - cell.row(), width - 1 - cell.column());
            case LEFT -> new BoardPosition(width - 1 - cell.column(), cell.row());
        };
    }
}
