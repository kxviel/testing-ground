package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.Objects;

public record TetrisBoardObject(TetrisItemType type, BoardPosition position, int lifetimeTicks) {

    public static final int DEFAULT_LIFETIME_TICKS = 100;

    public TetrisBoardObject(TetrisItemType type, BoardPosition position) {
        this(type, position, DEFAULT_LIFETIME_TICKS);
    }

    public TetrisBoardObject {
        type = type == null ? TetrisItemType.TELEPORT_SWAP : type;
        Objects.requireNonNull(position, "position");
        lifetimeTicks = Math.max(0, lifetimeTicks);
    }

    public TetrisBoardObject tick() {
        return new TetrisBoardObject(type, position, lifetimeTicks - 1);
    }

    public boolean isExpired() {
        return lifetimeTicks <= 0;
    }
}
