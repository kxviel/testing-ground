package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Objects;

public record HexPiece(HexPieceColor color, HexPieceType type) {

    public HexPiece {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(type, "type");
    }

    public String displayText() {
        return color == HexPieceColor.WHITE
                ? type.symbol()
                : type.symbol().toLowerCase();
    }
}
