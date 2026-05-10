package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CustomPieceBuilder {

    private CustomPieceBuilder() {
    }

    public static PieceShape build(String name, List<BoardPosition> selectedCells) {
        List<BoardPosition> normalizedCells = normalize(selectedCells);

        if (normalizedCells.isEmpty()) {
            throw new IllegalArgumentException("Select at least one custom cell.");
        }
        if (!isConnected(normalizedCells)) {
            throw new IllegalArgumentException("Custom cells must touch by side.");
        }

        return new PieceShape(PieceType.CUSTOM, name, normalizedCells);
    }

    public static List<BoardPosition> normalize(List<BoardPosition> selectedCells) {
        if (selectedCells == null || selectedCells.isEmpty()) {
            return List.of();
        }

        Set<BoardPosition> uniqueCells = new HashSet<>();
        int minRow = Integer.MAX_VALUE;
        int minColumn = Integer.MAX_VALUE;

        for (BoardPosition cell : selectedCells) {
            if (cell == null) {
                continue;
            }

            uniqueCells.add(cell);
            minRow = Math.min(minRow, cell.row());
            minColumn = Math.min(minColumn, cell.column());
        }

        if (uniqueCells.isEmpty()) {
            return List.of();
        }

        List<BoardPosition> normalizedCells = new ArrayList<>();
        for (BoardPosition cell : uniqueCells) {
            normalizedCells.add(new BoardPosition(cell.row() - minRow, cell.column() - minColumn));
        }

        normalizedCells.sort(Comparator.comparingInt(BoardPosition::row)
                .thenComparingInt(BoardPosition::column));
        return List.copyOf(normalizedCells);
    }

    private static boolean isConnected(List<BoardPosition> cells) {
        Set<BoardPosition> allCells = new HashSet<>(cells);
        Set<BoardPosition> visited = new HashSet<>();
        ArrayDeque<BoardPosition> queue = new ArrayDeque<>();

        queue.add(cells.get(0));
        visited.add(cells.get(0));

        while (!queue.isEmpty()) {
            BoardPosition current = queue.removeFirst();

            for (BoardPosition neighbor : neighbors(current)) {
                if (allCells.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == allCells.size();
    }

    private static List<BoardPosition> neighbors(BoardPosition cell) {
        return List.of(
                new BoardPosition(cell.row() - 1, cell.column()),
                new BoardPosition(cell.row() + 1, cell.column()),
                new BoardPosition(cell.row(), cell.column() - 1),
                new BoardPosition(cell.row(), cell.column() + 1));
    }
}
