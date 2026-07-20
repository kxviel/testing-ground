package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

public enum PieceType {
    I,
    O,
    T,
    S,
    Z,
    J,
    L,
    CUSTOM,
    RADIUS_BOMB,
    COLUMN_BOMB;

    public boolean isBomb() {
        return this == RADIUS_BOMB || this == COLUMN_BOMB;
    }
}
