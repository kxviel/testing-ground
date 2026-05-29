package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

public enum TetrisItemType {
    SPEED_UP_OPPONENT("+"),
    SLOW_SELF("-"),
    ROTATION_DELAY_OPPONENT("R"),
    ROTATION_DELAY_SELF("r"),
    SLOW_OPPONENT("S"),
    EXPLODE_RADIUS("*"),
    EXPLODE_BELOW("v"),
    PORTAL("P"),
    TELEPORT_SWAP("T"),
    PIECE_SWAP("G");

    private final String symbol;

    TetrisItemType(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean requiresActiveOpponent() {
        return switch (this) {
            case SPEED_UP_OPPONENT,
                 ROTATION_DELAY_OPPONENT,
                 SLOW_OPPONENT,
                 PORTAL,
                 TELEPORT_SWAP,
                 PIECE_SWAP -> true;
            case SLOW_SELF,
                 ROTATION_DELAY_SELF,
                 EXPLODE_RADIUS,
                 EXPLODE_BELOW -> false;
        };
    }

    public static EnumSet<TetrisItemType> eligibleForOpponentState(boolean opponentPlaying) {
        return Arrays.stream(values())
                .filter(type -> opponentPlaying || !type.requiresActiveOpponent())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TetrisItemType.class)));
    }
}
