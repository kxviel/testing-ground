package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

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
}
