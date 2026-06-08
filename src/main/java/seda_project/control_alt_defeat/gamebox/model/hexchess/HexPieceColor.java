package seda_project.control_alt_defeat.gamebox.model.hexchess;

public enum HexPieceColor {
    WHITE("White"),
    BLACK("Black");

    private final String displayName;

    HexPieceColor(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public HexPieceColor opponent() {
        return this == WHITE ? BLACK : WHITE;
    }
}
