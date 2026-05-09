package seda_project.control_alt_defeat.gamebox.model.tetris;

import java.util.Arrays;
import java.util.List;

public record TetrisGameConfig(List<String> pieces) {

    public TetrisGameConfig {
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
    }

    public static TetrisGameConfig defaultConfig() {
        return new TetrisGameConfig(List.of("Standard"));
    }

    public boolean hasPieces() {
        return pieces != null && !pieces.isEmpty();
    }

    public String displayText() {
        return String.join(", ", pieces);
    }

    public String serialize() {
        return String.join(",", pieces);
    }

    public static TetrisGameConfig deserialize(String value) {
        if (value == null || value.isBlank()) {
            return defaultConfig();
        }

        List<String> pieces = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(piece -> !piece.isEmpty())
                .toList();

        return pieces.isEmpty() ? defaultConfig() : new TetrisGameConfig(pieces);
    }
}
