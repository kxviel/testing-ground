package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CustomPieceBuilder {

    public static final int MAX_CUSTOM_CELLS = 25;

    private CustomPieceBuilder() {
    }

    public static PieceShape build(String name, List<BoardPosition> selectedCells) {
        if (selectedCells != null && selectedCells.size() > MAX_CUSTOM_CELLS) {
            throw new IllegalArgumentException("A custom piece can contain at most " + MAX_CUSTOM_CELLS + " cells.");
        }
        if (selectedCells != null && selectedCells.stream()
                .filter(Objects::nonNull)
                .anyMatch(cell -> cell.row() < 0 || cell.column() < 0
                        || cell.row() >= PieceShape.MAX_EXTENT || cell.column() >= PieceShape.MAX_EXTENT)) {
            throw new IllegalArgumentException("Custom piece cells are outside the supported grid.");
        }
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

        Set<BoardPosition> uniqueCells = selectedCells.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (uniqueCells.isEmpty()) {
            return List.of();
        }

        int minRow = uniqueCells.stream()
                .mapToInt(BoardPosition::row)
                .min()
                .orElse(0);
        int minColumn = uniqueCells.stream()
                .mapToInt(BoardPosition::column)
                .min()
                .orElse(0);

        return uniqueCells.stream()
                .map(cell -> new BoardPosition(cell.row() - minRow, cell.column() - minColumn))
                .sorted(Comparator.comparingInt(BoardPosition::row)
                        .thenComparingInt(BoardPosition::column))
                .toList();
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
