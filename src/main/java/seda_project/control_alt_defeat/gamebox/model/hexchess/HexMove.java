package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Objects;

public record HexMove(
        HexCoordinate from,
        HexCoordinate to,
        HexPieceType promotion,
        boolean enPassant) {

    public HexMove {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }

    public HexMove(HexCoordinate from, HexCoordinate to) {
        this(from, to, null, false);
    }

    public String notation() {
        String promotionText = promotion == null ? "" : "=" + promotion.symbol();
        return from.notation() + "-" + to.notation() + promotionText;
    }
}
