package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TetrisBoard {

    public static final int DEFAULT_ROWS = 20;
    public static final int MIN_ROWS = 4;
    public static final int COLUMNS = 10;

    /**
     * Kept for backwards-compatibility so existing call-sites that reference
     * TetrisBoard.ROWS still compile.  New code should call board.rows() instead.
     */
    public static final int ROWS = DEFAULT_ROWS;

    private final int rows;
    private final TetrisCell[][] cells;
    private final int[][] colorIndexes;

    public TetrisBoard() {
        this(DEFAULT_ROWS, createEmptyCells(DEFAULT_ROWS), createEmptyColors(DEFAULT_ROWS));
    }

    public TetrisBoard(TetrisCell[][] cells) {
        this(cells == null ? DEFAULT_ROWS : cells.length, cells, null);
    }

    public TetrisBoard(TetrisCell[][] cells, int[][] colorIndexes) {
        this(cells == null ? DEFAULT_ROWS : cells.length, cells, colorIndexes);
    }

    /** Primary internal constructor – all other constructors delegate here. */
    private TetrisBoard(int rows, TetrisCell[][] cells, int[][] colorIndexes) {
        this.rows = Math.max(MIN_ROWS, rows);
        this.cells = copyCells(cells, this.rows);
        this.colorIndexes = copyColors(colorIndexes, this.cells, this.rows);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public int rows() {
        return rows;
    }

    public int columns() {
        return COLUMNS;
    }

    public boolean isInside(BoardPosition position) {
        return position.row() >= 0
                && position.row() < rows
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

        TetrisCell[][] nextCells = copyCells(cells, rows);
        int[][] nextColors = copyColors(colorIndexes, nextCells, rows);
        for (BoardPosition cell : piece.boardCells()) {
            nextCells[cell.row()][cell.column()] = TetrisCell.FILLED;
            nextColors[cell.row()][cell.column()] = piece.colorIndex();
        }

        return new TetrisBoard(rows, nextCells, nextColors);
    }

    public List<Integer> fullRows() {
        return IntStream.range(0, rows)
                .filter(this::isFullRow)
                .boxed()
                .toList();
    }

    public TetrisBoard clearRows(List<Integer> rows) {
        if (rows == null || rows.isEmpty()) {
            return this;
        }

        Set<Integer> rowsToClear = rows.stream()
                .filter(row -> row != null && row >= 0 && row < this.rows)
                .collect(Collectors.toSet());

        if (rowsToClear.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = createEmptyCells(this.rows);
        int[][] nextColors = createEmptyColors(this.rows);
        int writeRow = this.rows - 1;

        for (int row = this.rows - 1; row >= 0; row--) {
            if (rowsToClear.contains(row)) {
                continue;
            }

            for (int column = 0; column < COLUMNS; column++) {
                nextCells[writeRow][column] = cells[row][column];
                nextColors[writeRow][column] = colorIndexes[row][column];
            }
            writeRow--;
        }

        return new TetrisBoard(this.rows, nextCells, nextColors);
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

        Set<BoardPosition> positions = IntStream.range(impact.row(), rows)
                .mapToObj(row -> new BoardPosition(row, impact.column()))
                .filter(this::isInside)
                .collect(Collectors.toSet());

        return clearPositions(positions);
    }

    public TetrisBoard addGarbageLines(int count, int holeColumn) {
        int lines = Math.min(rows, Math.max(0, count));
        if (lines == 0) {
            return this;
        }

        int safeHoleColumn = Math.floorMod(holeColumn, COLUMNS);
        TetrisCell[][] nextCells = createEmptyCells(rows);
        int[][] nextColors = createEmptyColors(rows);

        for (int row = 0; row < rows - lines; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                nextCells[row][column] = cells[row + lines][column];
                nextColors[row][column] = colorIndexes[row + lines][column];
            }
        }

        for (int row = rows - lines; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (column == safeHoleColumn) {
                    continue;
                }
                nextCells[row][column] = TetrisCell.FILLED;
                nextColors[row][column] = 0;
            }
        }

        return new TetrisBoard(rows, nextCells, nextColors);
    }

    // -----------------------------------------------------------------------
    // Variable-height row operations (Feature 2)
    // -----------------------------------------------------------------------

    /**
     * Adds {@code count} empty rows at the TOP (row-0 side) of the board,
     * shifting all existing content DOWN by {@code count}. Used as the
     * reward for the clearing player whose gravity is DOWN.
     */
    public TetrisBoard addRowsAtTop(int count) {
        int n = Math.max(0, count);
        if (n == 0) return this;
        int newRows = rows + n;
        TetrisCell[][] newCells = createEmptyCells(newRows);
        int[][] newColors = createEmptyColors(newRows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                newCells[row + n][col] = cells[row][col];
                newColors[row + n][col] = colorIndexes[row][col];
            }
        }
        return new TetrisBoard(newRows, newCells, newColors);
    }

    /**
     * Appends {@code count} empty rows at the BOTTOM (high-index side) of the
     * board. Used as the reward for the clearing player whose gravity is UP.
     */
    public TetrisBoard addRowsAtBottom(int count) {
        int n = Math.max(0, count);
        if (n == 0) return this;
        int newRows = rows + n;
        TetrisCell[][] newCells = createEmptyCells(newRows);
        int[][] newColors = createEmptyColors(newRows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                newCells[row][col] = cells[row][col];
                newColors[row][col] = colorIndexes[row][col];
            }
        }
        return new TetrisBoard(newRows, newCells, newColors);
    }

    /**
     * Removes {@code count} rows from the TOP (row-0 side), shifting the
     * remaining content UP. Board height is capped at MIN_ROWS minimum.
     * Used as the penalty for an opponent whose gravity is DOWN (spawn side = top).
     */
    public TetrisBoard removeRowsFromTop(int count) {
        int n = Math.min(count, rows - MIN_ROWS);
        if (n <= 0) return this;
        int newRows = rows - n;
        TetrisCell[][] newCells = createEmptyCells(newRows);
        int[][] newColors = createEmptyColors(newRows);
        for (int row = 0; row < newRows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                newCells[row][col] = cells[row + n][col];
                newColors[row][col] = colorIndexes[row + n][col];
            }
        }
        return new TetrisBoard(newRows, newCells, newColors);
    }

    /**
     * Removes {@code count} rows from the BOTTOM (high-index side).
     * Board height is capped at MIN_ROWS minimum.
     * Used as the penalty for an opponent whose gravity is UP (spawn side = bottom).
     */
    public TetrisBoard removeRowsFromBottom(int count) {
        int n = Math.min(count, rows - MIN_ROWS);
        if (n <= 0) return this;
        int newRows = rows - n;
        TetrisCell[][] newCells = createEmptyCells(newRows);
        int[][] newColors = createEmptyColors(newRows);
        for (int row = 0; row < newRows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                newCells[row][col] = cells[row][col];
                newColors[row][col] = colorIndexes[row][col];
            }
        }
        return new TetrisBoard(newRows, newCells, newColors);
    }

    // -----------------------------------------------------------------------
    // Cell mutation
    // -----------------------------------------------------------------------

    public TetrisBoard withCell(BoardPosition position, TetrisCell cell) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position outside board: " + position);
        }

        TetrisCell[][] nextCells = copyCells(cells, rows);
        int[][] nextColors = copyColors(colorIndexes, nextCells, rows);
        nextCells[position.row()][position.column()] = cell == null ? TetrisCell.EMPTY : cell;
        nextColors[position.row()][position.column()] = nextCells[position.row()][position.column()] == TetrisCell.EMPTY
                ? -1
                : 0;
        return new TetrisBoard(rows, nextCells, nextColors);
    }

    public TetrisCell[][] cells() {
        return copyCells(cells, rows);
    }

    public int[][] colors() {
        return copyColors(colorIndexes, cells, rows);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private boolean isFullRow(int row) {
        return IntStream.range(0, COLUMNS)
                .allMatch(column -> cells[row][column] != TetrisCell.EMPTY);
    }

    private TetrisBoard clearPositions(Set<BoardPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = copyCells(cells, rows);
        int[][] nextColors = copyColors(colorIndexes, nextCells, rows);

        positions.stream()
                .filter(this::isInside)
                .forEach(position -> {
                    nextCells[position.row()][position.column()] = TetrisCell.EMPTY;
                    nextColors[position.row()][position.column()] = -1;
                });

        return new TetrisBoard(rows, nextCells, nextColors);
    }

    private static int distanceSquared(BoardPosition first, BoardPosition second) {
        int rowDelta = first.row() - second.row();
        int columnDelta = first.column() - second.column();

        return rowDelta * rowDelta + columnDelta * columnDelta;
    }

    private static TetrisCell[][] createEmptyCells(int rows) {
        TetrisCell[][] cells = new TetrisCell[rows][COLUMNS];
        for (TetrisCell[] row : cells) {
            Arrays.fill(row, TetrisCell.EMPTY);
        }
        return cells;
    }

    private static int[][] createEmptyColors(int rows) {
        int[][] colors = new int[rows][COLUMNS];
        for (int[] row : colors) {
            Arrays.fill(row, -1);
        }
        return colors;
    }

    private static TetrisCell[][] copyCells(TetrisCell[][] source, int rows) {
        if (source == null || source.length != rows) {
            return createEmptyCells(rows);
        }

        TetrisCell[][] copy = new TetrisCell[rows][COLUMNS];
        for (int row = 0; row < rows; row++) {
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

    private static int[][] copyColors(int[][] source, TetrisCell[][] copiedCells, int rows) {
        int[][] copy = createEmptyColors(rows);
        if (source == null || source.length != rows) {
            return copy;
        }

        for (int row = 0; row < rows; row++) {
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
