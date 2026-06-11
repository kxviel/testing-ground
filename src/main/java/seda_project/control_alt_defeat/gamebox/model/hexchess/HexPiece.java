package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Objects;

public record HexPiece(HexPieceColor color, HexPieceType type) {

    public HexPiece {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(type, "type");
    }

    public String displayText() {
        return switch (type) {
            case KING -> "♚";
            case QUEEN -> "♛";
            case ROOK -> "♜";
            case BISHOP -> "♝";
            case KNIGHT -> "♞";
            case PAWN -> "♟";
            case CUSTOM -> "◆";
        };
    }
}
