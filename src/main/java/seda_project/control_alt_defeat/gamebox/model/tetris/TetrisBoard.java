package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TetrisBoard {

    public static final int DEFAULT_ROWS = 20;
    public static final int DEFAULT_COLUMNS = 10;
    public static final int HORIZONTAL_ROWS = 10;
    public static final int HORIZONTAL_COLUMNS = 20;
    public static final int MIN_ROWS = 4;
    public static final int MIN_COLUMNS = 4;
    public static final int MAX_ROWS = 30;
    public static final int MAX_COLUMNS = 30;

    private final int rows;
    private final int columns;
    private final TetrisCell[][] cells;
    private final int[][] colorIndexes;
    private final PlayerSide themeSide;

    public static TetrisBoard createDefault(boolean horizontalMode) {
        return createDefault(horizontalMode, PlayerSide.BOTTOM);
    }

    public static TetrisBoard createDefault(boolean horizontalMode, PlayerSide themeSide) {
        return horizontalMode
                ? new TetrisBoard(HORIZONTAL_ROWS, HORIZONTAL_COLUMNS, themeSide)
                : new TetrisBoard(DEFAULT_ROWS, DEFAULT_COLUMNS, themeSide);
    }

    public TetrisBoard() {
        this(DEFAULT_ROWS, DEFAULT_COLUMNS, PlayerSide.BOTTOM);
    }

    public TetrisBoard(int rows, int columns) {
        this(rows, columns, PlayerSide.BOTTOM);
    }

    public TetrisBoard(int rows, int columns, PlayerSide themeSide) {
        int safeRows = clamp(rows, MIN_ROWS, MAX_ROWS);
        int safeColumns = clamp(columns, MIN_COLUMNS, MAX_COLUMNS);
        this.rows = safeRows;
        this.columns = safeColumns;
        this.cells = createEmptyCells(safeRows, safeColumns);
        this.colorIndexes = createEmptyColors(safeRows, safeColumns);
        this.themeSide = safeThemeSide(themeSide);
    }

    public TetrisBoard(TetrisCell[][] cells) {
        this(cells, null);
    }

    public TetrisBoard(TetrisCell[][] cells, int[][] colorIndexes) {
        this(cells, colorIndexes, PlayerSide.BOTTOM);
    }

    public TetrisBoard(TetrisCell[][] cells, int[][] colorIndexes, PlayerSide themeSide) {
        int inferredRows = cells == null ? DEFAULT_ROWS : cells.length;
        int inferredColumns = inferColumns(cells, DEFAULT_COLUMNS);
        this.rows = clamp(inferredRows, MIN_ROWS, MAX_ROWS);
        this.columns = clamp(inferredColumns, MIN_COLUMNS, MAX_COLUMNS);
        this.cells = copyCells(cells, this.rows, this.columns);
        this.colorIndexes = copyColors(colorIndexes, this.cells, this.rows, this.columns);
        this.themeSide = safeThemeSide(themeSide);
    }

    private TetrisBoard(
            int rows,
            int columns,
            TetrisCell[][] cells,
            int[][] colorIndexes,
            PlayerSide themeSide) {
        this.rows = clamp(rows, MIN_ROWS, MAX_ROWS);
        this.columns = clamp(columns, MIN_COLUMNS, MAX_COLUMNS);
        this.cells = copyCells(cells, this.rows, this.columns);
        this.colorIndexes = copyColors(colorIndexes, this.cells, this.rows, this.columns);
        this.themeSide = safeThemeSide(themeSide);
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public PlayerSide themeSide() {
        return themeSide;
    }

    public boolean isInside(BoardPosition position) {
        return position != null
                && position.row() >= 0
                && position.row() < rows
                && position.column() >= 0
                && position.column() < columns;
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

        TetrisCell[][] nextCells = copyCells(cells, rows, columns);
        int[][] nextColors = copyColors(colorIndexes, nextCells, rows, columns);
        for (BoardPosition cell : piece.boardCells()) {
            nextCells[cell.row()][cell.column()] = TetrisCell.FILLED;
            nextColors[cell.row()][cell.column()] = piece.colorIndex();
        }

        return new TetrisBoard(rows, columns, nextCells, nextColors, themeSide);
    }

    public List<Integer> fullRows() {
        return IntStream.range(0, rows)
                .filter(this::isFullRow)
                .boxed()
                .toList();
    }

    public List<Integer> fullColumns() {
        return IntStream.range(0, columns)
                .filter(this::isFullColumn)
                .boxed()
                .toList();
    }

    public TetrisBoard clearRows(List<Integer> rowsToClear) {
        if (rowsToClear == null || rowsToClear.isEmpty()) {
            return this;
        }

        Set<Integer> clearedRows = rowsToClear.stream()
                .filter(row -> row != null && row >= 0 && row < rows)
                .collect(Collectors.toSet());

        if (clearedRows.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = createEmptyCells(rows, columns);
        int[][] nextColors = createEmptyColors(rows, columns);
        int writeRow = rows - 1;

        for (int row = rows - 1; row >= 0; row--) {
            if (clearedRows.contains(row)) {
                continue;
            }

            System.arraycopy(cells[row], 0, nextCells[writeRow], 0, columns);
            System.arraycopy(colorIndexes[row], 0, nextColors[writeRow], 0, columns);
            writeRow--;
        }

        return new TetrisBoard(rows, columns, nextCells, nextColors, themeSide);
    }

    public TetrisBoard clearColumns(List<Integer> columnsToClear) {
        return clearColumns(columnsToClear, GravityDirection.RIGHT);
    }

    public TetrisBoard clearColumns(
            List<Integer> columnsToClear,
            GravityDirection gravityDirection) {
        if (columnsToClear == null || columnsToClear.isEmpty()) {
            return this;
        }

        Set<Integer> clearedColumns = columnsToClear.stream()
                .filter(column -> column != null && column >= 0 && column < columns)
                .collect(Collectors.toSet());

        if (clearedColumns.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = createEmptyCells(rows, columns);
        int[][] nextColors = createEmptyColors(rows, columns);
        if (gravityDirection == GravityDirection.LEFT) {
            int writeColumn = 0;
            for (int column = 0; column < columns; column++) {
                if (!clearedColumns.contains(column)) {
                    copyColumn(column, writeColumn++, nextCells, nextColors);
                }
            }
        } else {
            int writeColumn = columns - 1;
            for (int column = columns - 1; column >= 0; column--) {
                if (!clearedColumns.contains(column)) {
                    copyColumn(column, writeColumn--, nextCells, nextColors);
                }
            }
        }

        return new TetrisBoard(rows, columns, nextCells, nextColors, themeSide);
    }

    public TetrisBoard destroyRadius(BoardPosition center, int radius) {
        if (center == null || radius < 0 || !isInside(center)) {
            return this;
        }

        int safeRadius = Math.min(radius, Math.max(rows, columns));
        long radiusSquared = (long) safeRadius * safeRadius;
        Set<BoardPosition> positions = IntStream.range(0, rows)
                .boxed()
                .flatMap(row -> IntStream.range(0, columns)
                        .mapToObj(column -> new BoardPosition(row, column)))
                .filter(position -> distanceSquared(center, position) <= radiusSquared)
                .collect(Collectors.toSet());

        return clearPositions(positions);
    }

    public TetrisBoard destroyAlongGravity(BoardPosition impact, GravityDirection gravityDirection) {
        if (impact == null || !isInside(impact)) {
            return this;
        }

        GravityDirection direction = gravityDirection == null ? GravityDirection.DOWN : gravityDirection;
        Set<BoardPosition> positions = switch (direction) {
            case DOWN -> IntStream.range(impact.row(), rows)
                    .mapToObj(row -> new BoardPosition(row, impact.column()))
                    .collect(Collectors.toSet());
            case UP -> IntStream.range(0, impact.row() + 1)
                    .mapToObj(row -> new BoardPosition(row, impact.column()))
                    .collect(Collectors.toSet());
            case RIGHT -> IntStream.range(impact.column(), columns)
                    .mapToObj(column -> new BoardPosition(impact.row(), column))
                    .collect(Collectors.toSet());
            case LEFT -> IntStream.range(0, impact.column() + 1)
                    .mapToObj(column -> new BoardPosition(impact.row(), column))
                    .collect(Collectors.toSet());
        };

        return clearPositions(positions);
    }

    public TetrisBoard addRowsAtTop(int count) {
        int n = Math.min(Math.max(0, count), MAX_ROWS - rows);
        if (n == 0) {
            return this;
        }

        return resizeRows(rows + n, 0, n);
    }

    public TetrisBoard addRowsAtBottom(int count) {
        int n = Math.min(Math.max(0, count), MAX_ROWS - rows);
        if (n == 0) {
            return this;
        }

        return resizeRows(rows + n, 0, 0);
    }

    public TetrisBoard removeRowsFromTop(int count) {
        int n = Math.min(count, rows - MIN_ROWS);
        if (n <= 0) {
            return this;
        }

        return resizeRows(rows - n, n, 0);
    }

    public TetrisBoard removeRowsFromBottom(int count) {
        int n = Math.min(count, rows - MIN_ROWS);
        if (n <= 0) {
            return this;
        }

        return resizeRows(rows - n, 0, 0);
    }

    public TetrisBoard addColumnsAtLeft(int count) {
        int n = Math.min(Math.max(0, count), MAX_COLUMNS - columns);
        if (n == 0) {
            return this;
        }

        return resizeColumns(columns + n, 0, n);
    }

    public TetrisBoard addColumnsAtRight(int count) {
        int n = Math.min(Math.max(0, count), MAX_COLUMNS - columns);
        if (n == 0) {
            return this;
        }

        return resizeColumns(columns + n, 0, 0);
    }

    public TetrisBoard removeColumnsFromLeft(int count) {
        int n = Math.min(count, columns - MIN_COLUMNS);
        if (n <= 0) {
            return this;
        }

        return resizeColumns(columns - n, n, 0);
    }

    public TetrisBoard removeColumnsFromRight(int count) {
        int n = Math.min(count, columns - MIN_COLUMNS);
        if (n <= 0) {
            return this;
        }

        return resizeColumns(columns - n, 0, 0);
    }

    private TetrisBoard resizeRows(int newRows, int sourceStartRow, int targetStartRow) {
        TetrisCell[][] newCells = createEmptyCells(newRows, columns);
        int[][] newColors = createEmptyColors(newRows, columns);
        int rowsToCopy = Math.min(rows - sourceStartRow, newRows - targetStartRow);

        for (int row = 0; row < rowsToCopy; row++) {
            System.arraycopy(cells[sourceStartRow + row], 0, newCells[targetStartRow + row], 0, columns);
            System.arraycopy(colorIndexes[sourceStartRow + row], 0, newColors[targetStartRow + row], 0, columns);
        }

        return new TetrisBoard(newRows, columns, newCells, newColors, themeSide);
    }

    private TetrisBoard resizeColumns(int newColumns, int sourceStartColumn, int targetStartColumn) {
        TetrisCell[][] newCells = createEmptyCells(rows, newColumns);
        int[][] newColors = createEmptyColors(rows, newColumns);
        int columnsToCopy = Math.min(columns - sourceStartColumn, newColumns - targetStartColumn);

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columnsToCopy; column++) {
                newCells[row][targetStartColumn + column] = cells[row][sourceStartColumn + column];
                newColors[row][targetStartColumn + column] = colorIndexes[row][sourceStartColumn + column];
            }
        }

        return new TetrisBoard(rows, newColumns, newCells, newColors, themeSide);
    }

    private void copyColumn(
            int sourceColumn,
            int targetColumn,
            TetrisCell[][] targetCells,
            int[][] targetColors) {
        for (int row = 0; row < rows; row++) {
            targetCells[row][targetColumn] = cells[row][sourceColumn];
            targetColors[row][targetColumn] = colorIndexes[row][sourceColumn];
        }
    }

    public TetrisCell[][] cells() {
        return copyCells(cells, rows, columns);
    }

    public int[][] colors() {
        return copyColors(colorIndexes, cells, rows, columns);
    }

    private boolean isFullRow(int row) {
        return IntStream.range(0, columns)
                .allMatch(column -> cells[row][column] != TetrisCell.EMPTY);
    }

    private boolean isFullColumn(int column) {
        return IntStream.range(0, rows)
                .allMatch(row -> cells[row][column] != TetrisCell.EMPTY);
    }

    private TetrisBoard clearPositions(Set<BoardPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return this;
        }

        TetrisCell[][] nextCells = copyCells(cells, rows, columns);
        int[][] nextColors = copyColors(colorIndexes, nextCells, rows, columns);

        positions.stream()
                .filter(this::isInside)
                .forEach(position -> {
                    nextCells[position.row()][position.column()] = TetrisCell.EMPTY;
                    nextColors[position.row()][position.column()] = -1;
                });

        return new TetrisBoard(rows, columns, nextCells, nextColors, themeSide);
    }

    private static long distanceSquared(BoardPosition first, BoardPosition second) {
        long rowDelta = (long) first.row() - second.row();
        long columnDelta = (long) first.column() - second.column();

        return rowDelta * rowDelta + columnDelta * columnDelta;
    }

    private static int inferColumns(TetrisCell[][] cells, int defaultColumns) {
        if (cells == null || cells.length == 0 || cells[0] == null) {
            return defaultColumns;
        }

        return clamp(cells[0].length, MIN_COLUMNS, MAX_COLUMNS);
    }

    private static TetrisCell[][] createEmptyCells(int rows, int columns) {
        TetrisCell[][] boardCells = new TetrisCell[rows][columns];
        for (TetrisCell[] row : boardCells) {
            Arrays.fill(row, TetrisCell.EMPTY);
        }
        return boardCells;
    }

    private static int[][] createEmptyColors(int rows, int columns) {
        int[][] colors = new int[rows][columns];
        for (int[] row : colors) {
            Arrays.fill(row, -1);
        }
        return colors;
    }

    private static TetrisCell[][] copyCells(TetrisCell[][] source, int rows, int columns) {
        if (source == null || source.length != rows) {
            return createEmptyCells(rows, columns);
        }

        TetrisCell[][] copy = new TetrisCell[rows][columns];
        for (int row = 0; row < rows; row++) {
            if (source[row] == null || source[row].length < columns) {
                Arrays.fill(copy[row], TetrisCell.EMPTY);
                continue;
            }

            for (int column = 0; column < columns; column++) {
                TetrisCell cell = source[row][column];
                copy[row][column] = cell == null ? TetrisCell.EMPTY : cell;
            }
        }

        return copy;
    }

    private static int[][] copyColors(int[][] source, TetrisCell[][] copiedCells, int rows, int columns) {
        int[][] copy = createEmptyColors(rows, columns);
        if (source == null || source.length != rows) {
            return copy;
        }

        for (int row = 0; row < rows; row++) {
            if (source[row] == null || source[row].length < columns) {
                continue;
            }

            for (int column = 0; column < columns; column++) {
                if (copiedCells[row][column] == TetrisCell.FILLED) {
                    copy[row][column] = Math.max(-1, source[row][column]);
                }
            }
        }

        return copy;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static PlayerSide safeThemeSide(PlayerSide themeSide) {
        return themeSide == null ? PlayerSide.BOTTOM : themeSide;
    }
}
