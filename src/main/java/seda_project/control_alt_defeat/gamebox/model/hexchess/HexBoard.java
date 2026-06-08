package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public record HexBoard(Map<HexCoordinate, HexPiece> pieces) {

    public HexBoard {
        pieces = pieces == null ? Map.of() : Map.copyOf(pieces);
    }

    public static HexBoard empty() {
        return new HexBoard(Map.of());
    }

    public static HexBoard standard() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();

        addWhitePieces(pieces);
        addBlackPieces(pieces);

        return new HexBoard(pieces);
    }

    public Optional<HexPiece> pieceAt(HexCoordinate coordinate) {
        return Optional.ofNullable(pieces.get(coordinate));
    }

    public boolean isEmpty(HexCoordinate coordinate) {
        return !pieces.containsKey(coordinate);
    }

    public HexBoard withPiece(HexCoordinate coordinate, HexPiece piece) {
        Map<HexCoordinate, HexPiece> nextPieces = new LinkedHashMap<>(pieces);

        if (piece == null) {
            nextPieces.remove(coordinate);
        } else {
            nextPieces.put(coordinate, piece);
        }

        return new HexBoard(nextPieces);
    }

    public HexBoard withoutPiece(HexCoordinate coordinate) {
        return withPiece(coordinate, null);
    }

    public Stream<Map.Entry<HexCoordinate, HexPiece>> piecesOf(HexPieceColor color) {
        return pieces.entrySet()
                .stream()
                .filter(entry -> entry.getValue().color() == color);
    }

    public Optional<HexCoordinate> kingPosition(HexPieceColor color) {
        return piecesOf(color)
                .filter(entry -> entry.getValue().type() == HexPieceType.KING)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    HexBoard applyMove(HexMove move, HexCoordinate capturedAt, HexPieceType promotion) {
        HexPiece movingPiece = pieces.get(move.from());
        if (movingPiece == null) {
            return this;
        }

        HexPiece placedPiece = promotion == null
                ? movingPiece
                : new HexPiece(movingPiece.color(), promotion);

        return withoutPiece(move.from())
                .withoutPiece(capturedAt == null ? move.to() : capturedAt)
                .withPiece(move.to(), placedPiece);
    }

    private static void addWhitePieces(Map<HexCoordinate, HexPiece> pieces) {
        put(pieces, HexPieceColor.WHITE, HexPieceType.KING, "g1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.QUEEN, "e1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.ROOK, "c1", "i1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.KNIGHT, "d1", "h1");
        put(pieces, HexPieceColor.WHITE, HexPieceType.BISHOP, "f1", "f2", "f3");
        put(pieces, HexPieceColor.WHITE, HexPieceType.PAWN, "b1", "c2", "d3", "e4", "f5", "g4", "h3", "i2", "k1");
    }

    private static void addBlackPieces(Map<HexCoordinate, HexPiece> pieces) {
        put(pieces, HexPieceColor.BLACK, HexPieceType.KING, "g10");
        put(pieces, HexPieceColor.BLACK, HexPieceType.QUEEN, "e10");
        put(pieces, HexPieceColor.BLACK, HexPieceType.ROOK, "c8", "i8");
        put(pieces, HexPieceColor.BLACK, HexPieceType.KNIGHT, "d9", "h9");
        put(pieces, HexPieceColor.BLACK, HexPieceType.BISHOP, "f9", "f10", "f11");
        put(pieces, HexPieceColor.BLACK, HexPieceType.PAWN, "b7", "c7", "d7", "e7", "f7", "g7", "h7", "i7", "k7");
    }

    private static void put(
            Map<HexCoordinate, HexPiece> pieces,
            HexPieceColor color,
            HexPieceType type,
            String... notations) {
        Stream.of(notations)
                .forEach(notation -> pieces.put(HexCoordinate.of(notation), new HexPiece(color, type)));
    }
}
