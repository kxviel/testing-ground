package seda_project.control_alt_defeat.gamebox.model.memory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class BoardVariant {

    public static final int MAX_CARDS = 45;
    public static final double MAX_ASPECT = 2.5;

    public final int k;
    public final int n;
    public final int totalCards;
    public final int rows;
    public final int cols;
    public final String difficulty;

    public BoardVariant(int k, int n, String difficulty) {
        this.k = k;
        this.n = n;
        this.totalCards = n * k;
        this.difficulty = difficulty;
        int[] dims = bestDimensions(totalCards);
        this.rows = dims[0];
        this.cols = dims[1];
    }

    public static int[] bestDimensions(int total) {
        List<int[]> candidates = IntStream.rangeClosed(1, (int) Math.sqrt(total))
                .filter(rows -> total % rows == 0)
                .mapToObj(rows -> new int[] { rows, total / rows })
                .toList();

        return candidates.stream()
                .filter(pair -> aspectRatio(pair) <= MAX_ASPECT)
                .max(Comparator.comparingInt(pair -> pair[0]))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(BoardVariant::aspectRatio))
                        .orElse(new int[] { 1, total }));
    }

    public static List<BoardVariant> computeVariants(int k) {
        if (k < 1 || k > MAX_CARDS) {
            return List.of();
        }

        int maxN = MAX_CARDS / k;
        if (maxN == 0) {
            return List.of();
        }

        Map<Integer, String> sizes = new LinkedHashMap<>();
        sizes.put(maxN, "Large Board");
        sizes.putIfAbsent((int) Math.floor(maxN * 2.0 / 3), "Medium Board");
        sizes.putIfAbsent((int) Math.floor(maxN * 1.0 / 3), "Small Board");

        return sizes.entrySet().stream()
                .filter(entry -> entry.getKey() > 0)
                .map(entry -> new BoardVariant(k, entry.getKey(), entry.getValue()))
                .toList();
    }

    private static double aspectRatio(int[] pair) {
        return (double) pair[1] / pair[0];
    }

    @Override
    public String toString() {
        return difficulty + ": " + totalCards + " cards (" + rows + "×" + cols + ")";
    }
}
