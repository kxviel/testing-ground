package seda_project.control_alt_defeat.gamebox.model.memory;

import java.util.ArrayList;
import java.util.List;

// Describes one playable board option for a selected matching size.
public class BoardVariant {

    /** Maximum number of cards supported by the symbol pool and UI. */
    public static final int MAX_CARDS = 45;
    /** Maximum preferred width-to-height ratio for generated grids. */
    public static final double MAX_ASPECT = 2.5;

    /** Number of identical cards required for one match. */
    public final int k;
    /** Number of distinct symbol groups on the board. */
    public final int n;
    /** Total number of cards, equal to {@code k * n}. */
    public final int totalCards;
    /** Number of rows in the generated grid. */
    public final int rows;
    /** Number of columns in the generated grid. */
    public final int cols;
    /** Human-readable size label shown in the menu. */
    public final String difficulty;

    /**
     * Creates a board variant and calculates balanced grid dimensions.
     *
     * @param k          number of identical cards required for a match
     * @param n          number of distinct symbol groups
     * @param difficulty display label for the option
     */
    public BoardVariant(int k, int n, String difficulty) {
        this.k = k;
        this.n = n;
        this.totalCards = n * k;
        this.difficulty = difficulty;
        int[] dims = bestDimensions(totalCards);
        this.rows = dims[0];
        this.cols = dims[1];
    }

    /**
     * Finds the most balanced factor pair for a board with {@code N} cards.
     *
     * @param N total number of cards
     * @return two-element array containing rows and columns
     */
    public static int[] bestDimensions(int N) {
        List<int[]> candidates = new ArrayList<>();
        for (int r = 1; r <= (int) Math.sqrt(N); r++) {
            if (N % r == 0) {
                int c = N / r;
                candidates.add(new int[] { r, c });
            }
        }

        int[] best = null;
        double bestRatio = Double.MAX_VALUE;
        for (int[] pair : candidates) {
            double ratio = (double) pair[1] / pair[0];
            if (ratio <= MAX_ASPECT) {
                if (best == null || pair[0] > best[0]) {
                    best = pair;
                    bestRatio = ratio;
                }
            }
        }

        if (best != null)
            return best;

        for (int[] pair : candidates) {
            double ratio = (double) pair[1] / pair[0];
            if (best == null || ratio < bestRatio) {
                best = pair;
                bestRatio = ratio;
            }
        }

        return best != null ? best : new int[] { 1, N };
    }

    /**
     * Calculates the large, medium, and small board options for a k value.
     *
     * @param k number of identical cards required for a match
     * @return up to three unique playable variants
     */
    public static List<BoardVariant> computeVariants(int k) {
        if (k < 1 || k > 45)
            return List.of();

        int maxN = MAX_CARDS / k;
        if (maxN == 0)
            return List.of();

        int nLarge = maxN;
        int nMed = (int) Math.floor(maxN * 2.0 / 3);
        int nSmall = (int) Math.floor(maxN * 1.0 / 3);

        List<BoardVariant> result = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();

        // Avoid duplicate variants for large k values where rounded sizes collide.
        if (nLarge > 0 && !seen.contains(nLarge)) {
            result.add(new BoardVariant(k, nLarge, "Large Board"));
            seen.add(nLarge);
        }

        if (nMed > 0 && !seen.contains(nMed)) {
            result.add(new BoardVariant(k, nMed, "Medium Board"));
            seen.add(nMed);
        }

        if (nSmall > 0 && !seen.contains(nSmall)) {
            result.add(new BoardVariant(k, nSmall, "Small Board"));
            seen.add(nSmall);
        }

        return result;
    }

    @Override
    public String toString() {
        return difficulty + ": " + totalCards + " cards (" + rows + "×" + cols + ")";
    }
}