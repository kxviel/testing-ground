package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import javafx.scene.image.Image;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPiece;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceColor;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexPieceType;

import java.util.EnumMap;
import java.util.Map;

final class HexChessPieceImages {

    private static final String PIECE_ROOT = "/icons/chess_pieces/";
    private static final Map<HexPieceColor, Map<HexPieceType, Image>> CACHE = new EnumMap<>(HexPieceColor.class);

    private HexChessPieceImages() {
    }

    static Image image(HexPiece piece) {
        Map<HexPieceType, Image> colorImages = CACHE.computeIfAbsent(piece.color(), ignored -> new EnumMap<>(HexPieceType.class));
        return colorImages.computeIfAbsent(piece.type(), type -> load(piece.color(), type));
    }

    private static Image load(HexPieceColor color, HexPieceType type) {
        String fileName = fileName(color, type);
        var imageUrl = HexChessPieceImages.class.getResource(PIECE_ROOT + fileName);
        if (imageUrl == null) {
            throw new IllegalStateException("Missing chess piece PNG: " + fileName);
        }
        return new Image(imageUrl.toExternalForm());
    }

    private static String fileName(HexPieceColor color, HexPieceType type) {
        String prefix = color == HexPieceColor.WHITE ? "W_" : "B_";
        return switch (type) {
            case KING -> prefix + "King.png";
            case QUEEN -> prefix + "Queen.png";
            case ROOK -> prefix + "Rook.png";
            case BISHOP -> prefix + "Bishop.png";
            case KNIGHT -> prefix + "Knight.png";
            case PAWN, CUSTOM -> prefix + "Pawn.png";
        };
    }
}
