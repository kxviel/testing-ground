package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HexStartingPosition {

    public static final List<String> WHITE_PAWN_SQUARES = List.of(
            "b1", "c2", "d3", "e4", "f5", "g4", "h3", "i2", "k1");
    public static final List<String> BLACK_PAWN_SQUARES = List.of(
            "b7", "c7", "d7", "e7", "f7", "g7", "h7", "i7", "k7");

    private HexStartingPosition() {
    }

    public static HexBoard build() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();

        put(pieces, HexPieceColor.WHITE, HexPieceType.KING, "g1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.QUEEN, "e1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.ROOK, "c1", "i1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.KNIGHT, "d1", "h1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.BISHOP, "f1", "f2", "f3");
        put(pieces, HexPieceColor.WHITE, HexPieceType.PAWN, WHITE_PAWN_SQUARES);

        put(pieces, HexPieceColor.BLACK, HexPieceType.KING, "g10");
        put(pieces, HexPieceColor.BLACK, HexPieceType.QUEEN, "e10");
        put(pieces, HexPieceColor.BLACK, HexPieceType.ROOK, "c8", "i8");
        put(pieces, HexPieceColor.BLACK, HexPieceType.KNIGHT, "d9", "h9");
        put(pieces, HexPieceColor.BLACK, HexPieceType.BISHOP, "f9", "f10", "f11");
        put(pieces, HexPieceColor.BLACK, HexPieceType.PAWN, BLACK_PAWN_SQUARES);

        return new HexBoard(pieces);
    }

    public static List<HexCoordinate> whitePawnStarts() {
        return toCoordinates(WHITE_PAWN_SQUARES);
    }

    public static List<HexCoordinate> blackPawnStarts() {
        return toCoordinates(BLACK_PAWN_SQUARES);
    }

    private static List<HexCoordinate> toCoordinates(List<String> notations) {
        return notations.stream()
                .map(HexCoordinate::of)
                .toList();
    }

    private static void put(
            Map<HexCoordinate, HexPiece> pieces,
            HexPieceColor color,
            HexPieceType type,
            String... notations) {
        put(pieces, color, type, List.of(notations));
    }

    private static void put(
            Map<HexCoordinate, HexPiece> pieces,
            HexPieceColor color,
            HexPieceType type,
            List<String> notations) {
        notations.forEach(notation -> pieces.put(HexCoordinate.of(notation), new HexPiece(color, type)));
    }
}
