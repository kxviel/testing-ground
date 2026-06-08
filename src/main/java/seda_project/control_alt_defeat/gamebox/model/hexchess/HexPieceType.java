package seda_project.control_alt_defeat.gamebox.model.hexchess;

public enum HexPieceType {
    KING("K"),
    QUEEN("Q"),
    ROOK("R"),
    BISHOP("B"),
    KNIGHT("N"),
    PAWN("P"),
    CUSTOM("C");

    private final String symbol;

    HexPieceType(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
