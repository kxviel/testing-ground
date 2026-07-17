package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public record HexBoard(Map<HexCoordinate, HexPiece> pieces) {

    public static final int MAX_PIECES = 91;

    public HexBoard {
        if (pieces == null || pieces.isEmpty()) {
            pieces = Map.of();
        } else {
            Map<HexCoordinate, HexPiece> safePieces = new LinkedHashMap<>();
            pieces.forEach((coordinate, piece) -> {
                if (safePieces.size() < MAX_PIECES
                        && HexBoardGeometry.isValid(coordinate)
                        && piece != null) {
                    safePieces.put(coordinate, piece);
                }
            });
            pieces = Map.copyOf(safePieces);
        }
    }

    public static HexBoard empty() {
        return new HexBoard(Map.of());
    }

    public static HexBoard standard() {
        return HexStartingPosition.build();
    }

    public Optional<HexPiece> pieceAt(HexCoordinate coordinate) {
        return Optional.ofNullable(pieces.get(coordinate));
    }

    public boolean isEmpty(HexCoordinate coordinate) {
        return !pieces.containsKey(coordinate);
    }

    public HexBoard withPiece(HexCoordinate coordinate, HexPiece piece) {
        if (!HexBoardGeometry.isValid(coordinate)) {
            return this;
        }
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
            throw new IllegalStateException("Cannot apply move without a moving piece: " + move.notation());
        }

        HexPiece placedPiece = promotion == null
                ? movingPiece
                : new HexPiece(movingPiece.color(), promotion);

        return withoutPiece(move.from())
                .withoutPiece(capturedAt == null ? move.to() : capturedAt)
                .withPiece(move.to(), placedPiece);
    }

}
