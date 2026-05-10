package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TetrisBoard {

    public static final int ROWS = 20;
    public static final int COLUMNS = 10;

    private final TetrisCell[][] cells;

    public TetrisBoard() {
        this(createEmptyCells());
    }

    public TetrisBoard(TetrisCell[][] cells) {
        this.cells = copyCells(cells);
    }

    public int rows() {
        return ROWS;
    }

    public int columns() {
        return COLUMNS;
    }

    public boolean isInside(BoardPosition position) {
        return position.row() >= 0
                && position.row() < ROWS
                && position.column() >= 0
                && position.column() < COLUMNS;
    }

    public TetrisCell cellAt(BoardPosition position) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position outside board: " + position);
        }

        return cells[position.row()][position.column()];
    }

    public boolean isEmpty(BoardPosition position) {
        return isInside(position) && cellAt(position) == TetrisCell.EMPTY;
    }

    public boolean canPlace(TetrisPiece piece) {
        if (piece == null) {
            return false;
        }

        for (BoardPosition cell : piece.boardCells()) {
            if (!isEmpty(cell)) {
                return false;
            }
        }

        return true;
    }

    public TetrisBoard lockPiece(TetrisPiece piece) {
        if (!canPlace(piece)) {
            return this;
        }

        TetrisCell[][] nextCells = copyCells(cells);
        for (BoardPosition cell : piece.boardCells()) {
            nextCells[cell.row()][cell.column()] = TetrisCell.FILLED;
        }

        return new TetrisBoard(nextCells);
    }

    public List<Integer> fullRows() {
        List<Integer> rows = new ArrayList<>();

        for (int row = 0; row < ROWS; row++) {
            if (isFullRow(row)) {
                rows.add(row);
            }
        }

        return rows;
    }

    public TetrisBoard clearRows(List<Integer> rows) {
        if (rows == null || rows.isEmpty()) {
            return this;
        }

        Set<Integer> rowsToClear = new HashSet<>();
        for (Integer row : rows) {
            if (row != null && row >= 0 && row < ROWS) {
                rowsToClear.add(row);
            }
        }

        if (rowsToClear.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = createEmptyCells();
        int writeRow = ROWS - 1;

        for (int row = ROWS - 1; row >= 0; row--) {
            if (rowsToClear.contains(row)) {
                continue;
            }

            for (int column = 0; column < COLUMNS; column++) {
                nextCells[writeRow][column] = cells[row][column];
            }
            writeRow--;
        }

        return new TetrisBoard(nextCells);
    }

    public TetrisBoard withCell(BoardPosition position, TetrisCell cell) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position outside board: " + position);
        }

        TetrisCell[][] nextCells = copyCells(cells);
        nextCells[position.row()][position.column()] = cell == null ? TetrisCell.EMPTY : cell;
        return new TetrisBoard(nextCells);
    }

    public TetrisCell[][] cells() {
        return copyCells(cells);
    }

    private boolean isFullRow(int row) {
        for (int column = 0; column < COLUMNS; column++) {
            if (cells[row][column] == TetrisCell.EMPTY) {
                return false;
            }
        }

        return true;
    }

    private static TetrisCell[][] createEmptyCells() {
        TetrisCell[][] cells = new TetrisCell[ROWS][COLUMNS];
        for (TetrisCell[] row : cells) {
            Arrays.fill(row, TetrisCell.EMPTY);
        }
        return cells;
    }

    private static TetrisCell[][] copyCells(TetrisCell[][] source) {
        if (source == null || source.length != ROWS) {
            return createEmptyCells();
        }

        TetrisCell[][] copy = new TetrisCell[ROWS][COLUMNS];
        for (int row = 0; row < ROWS; row++) {
            if (source[row] == null || source[row].length != COLUMNS) {
                Arrays.fill(copy[row], TetrisCell.EMPTY);
                continue;
            }

            for (int column = 0; column < COLUMNS; column++) {
                TetrisCell cell = source[row][column];
                copy[row][column] = cell == null ? TetrisCell.EMPTY : cell;
            }
        }

        return copy;
    }
}
