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
    RADIUS_BOMB("*"),
    COLUMN_BOMB("v"),
    TELEPORT("P"),
    BOARD_SWAP("T"),
    FALLING_PIECE_SWAP("G");

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
                 TELEPORT,
                 BOARD_SWAP,
                 FALLING_PIECE_SWAP -> true;
            case SLOW_SELF,
                 ROTATION_DELAY_SELF,
                 RADIUS_BOMB,
                 COLUMN_BOMB -> false;
        };
    }

    public static EnumSet<TetrisItemType> eligibleForOpponentState(boolean opponentPlaying) {
        return Arrays.stream(values())
                .filter(type -> opponentPlaying || !type.requiresActiveOpponent())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TetrisItemType.class)));
    }
}
