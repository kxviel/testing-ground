package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TetrisBoard {

    public static final int ROWS = 20;
    public static final int COLUMNS = 10;

    private final TetrisCell[][] cells;
    private final int[][] colorIndexes;

    public TetrisBoard() {
        this(createEmptyCells(), createEmptyColors());
    }

    public TetrisBoard(TetrisCell[][] cells) {
        this(cells, createEmptyColors());
    }

    public TetrisBoard(TetrisCell[][] cells, int[][] colorIndexes) {
        this.cells = copyCells(cells);
        this.colorIndexes = copyColors(colorIndexes, this.cells);
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

    public int colorAt(BoardPosition position) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position outside board: " + position);
        }

        return colorIndexes[position.row()][position.column()];
    }

    public boolean canPlace(TetrisPiece piece) {
        return piece != null && piece.boardCells().stream().allMatch(this::isEmpty);
    }

    public TetrisBoard lockPiece(TetrisPiece piece) {
        if (!canPlace(piece)) {
            return this;
        }

        TetrisCell[][] nextCells = copyCells(cells);
        int[][] nextColors = copyColors(colorIndexes, nextCells);
        for (BoardPosition cell : piece.boardCells()) {
            nextCells[cell.row()][cell.column()] = TetrisCell.FILLED;
            nextColors[cell.row()][cell.column()] = piece.colorIndex();
        }

        return new TetrisBoard(nextCells, nextColors);
    }

    public List<Integer> fullRows() {
        return IntStream.range(0, ROWS)
                .filter(this::isFullRow)
                .boxed()
                .toList();
    }

    public TetrisBoard clearRows(List<Integer> rows) {
        if (rows == null || rows.isEmpty()) {
            return this;
        }

        Set<Integer> rowsToClear = rows.stream()
                .filter(row -> row != null && row >= 0 && row < ROWS)
                .collect(Collectors.toSet());

        if (rowsToClear.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = createEmptyCells();
        int[][] nextColors = createEmptyColors();
        int writeRow = ROWS - 1;

        for (int row = ROWS - 1; row >= 0; row--) {
            if (rowsToClear.contains(row)) {
                continue;
            }

            for (int column = 0; column < COLUMNS; column++) {
                nextCells[writeRow][column] = cells[row][column];
                nextColors[writeRow][column] = colorIndexes[row][column];
            }
            writeRow--;
        }

        return new TetrisBoard(nextCells, nextColors);
    }

    public TetrisBoard destroyRadius(BoardPosition center, int radius) {
        if (center == null || radius < 0) {
            return this;
        }

        Set<BoardPosition> positions = IntStream.rangeClosed(center.row() - radius, center.row() + radius)
                .boxed()
                .flatMap(row -> IntStream.rangeClosed(center.column() - radius, center.column() + radius)
                        .mapToObj(column -> new BoardPosition(row, column)))
                .filter(this::isInside)
                .filter(position -> distanceSquared(center, position) <= radius * radius)
                .collect(Collectors.toSet());

        return clearPositions(positions);
    }

    public TetrisBoard destroyBelow(BoardPosition impact) {
        if (impact == null) {
            return this;
        }

        Set<BoardPosition> positions = IntStream.range(impact.row(), ROWS)
                .mapToObj(row -> new BoardPosition(row, impact.column()))
                .filter(this::isInside)
                .collect(Collectors.toSet());

        return clearPositions(positions);
    }

    public TetrisBoard addGarbageLines(int count, int holeColumn) {
        int lines = Math.min(ROWS, Math.max(0, count));
        if (lines == 0) {
            return this;
        }

        int safeHoleColumn = Math.floorMod(holeColumn, COLUMNS);
        TetrisCell[][] nextCells = createEmptyCells();
        int[][] nextColors = createEmptyColors();

        for (int row = 0; row < ROWS - lines; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                nextCells[row][column] = cells[row + lines][column];
                nextColors[row][column] = colorIndexes[row + lines][column];
            }
        }

        for (int row = ROWS - lines; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (column == safeHoleColumn) {
                    continue;
                }
                nextCells[row][column] = TetrisCell.FILLED;
                nextColors[row][column] = 0;
            }
        }

        return new TetrisBoard(nextCells, nextColors);
    }

    public TetrisBoard withCell(BoardPosition position, TetrisCell cell) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position outside board: " + position);
        }

        TetrisCell[][] nextCells = copyCells(cells);
        int[][] nextColors = copyColors(colorIndexes, nextCells);
        nextCells[position.row()][position.column()] = cell == null ? TetrisCell.EMPTY : cell;
        nextColors[position.row()][position.column()] = nextCells[position.row()][position.column()] == TetrisCell.EMPTY
                ? -1
                : 0;
        return new TetrisBoard(nextCells, nextColors);
    }

    public TetrisCell[][] cells() {
        return copyCells(cells);
    }

    public int[][] colors() {
        return copyColors(colorIndexes, cells);
    }

    private boolean isFullRow(int row) {
        return IntStream.range(0, COLUMNS)
                .allMatch(column -> cells[row][column] != TetrisCell.EMPTY);
    }

    private TetrisBoard clearPositions(Set<BoardPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = copyCells(cells);
        int[][] nextColors = copyColors(colorIndexes, nextCells);

        positions.stream()
                .filter(this::isInside)
                .forEach(position -> {
                    nextCells[position.row()][position.column()] = TetrisCell.EMPTY;
                    nextColors[position.row()][position.column()] = -1;
                });

        return new TetrisBoard(nextCells, nextColors);
    }

    private static int distanceSquared(BoardPosition first, BoardPosition second) {
        int rowDelta = first.row() - second.row();
        int columnDelta = first.column() - second.column();

        return rowDelta * rowDelta + columnDelta * columnDelta;
    }

    private static TetrisCell[][] createEmptyCells() {
        TetrisCell[][] cells = new TetrisCell[ROWS][COLUMNS];
        for (TetrisCell[] row : cells) {
            Arrays.fill(row, TetrisCell.EMPTY);
        }
        return cells;
    }

    private static int[][] createEmptyColors() {
        int[][] colors = new int[ROWS][COLUMNS];
        for (int[] row : colors) {
            Arrays.fill(row, -1);
        }
        return colors;
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

    private static int[][] copyColors(int[][] source, TetrisCell[][] copiedCells) {
        int[][] copy = createEmptyColors();
        if (source == null || source.length != ROWS) {
            return copy;
        }

        for (int row = 0; row < ROWS; row++) {
            if (source[row] == null || source[row].length != COLUMNS) {
                continue;
            }

            for (int column = 0; column < COLUMNS; column++) {
                if (copiedCells[row][column] == TetrisCell.FILLED) {
                    copy[row][column] = Math.max(-1, source[row][column]);
                }
            }
        }

        return copy;
    }
}
