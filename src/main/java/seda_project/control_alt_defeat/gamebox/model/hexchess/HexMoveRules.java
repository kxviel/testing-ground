package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class HexMoveRules {

    public static final int FIFTY_MOVE_RULE_PLY = 100;
    public static final int MAX_PAWNS_PER_SIDE = 9;
    public static final int MAX_PIECES_PER_SIDE = 18;

    private static final List<HexCoordinate> WHITE_PAWN_STARTS = HexStartingPosition.whitePawnStarts();
    private static final List<HexCoordinate> BLACK_PAWN_STARTS = HexStartingPosition.blackPawnStarts();
    // Glinski pawns capture the two forward edge-adjacent hexes, not bishop diagonals.
    private static final List<Jump> WHITE_PAWN_ATTACKS = List.of(new Jump(1, 0), new Jump(-1, 1));
    private static final List<Jump> BLACK_PAWN_ATTACKS = List.of(new Jump(-1, 0), new Jump(1, -1));
    private static final List<Jump> KNIGHT_JUMPS = createKnightJumps();
    private static final List<HexPieceType> PROMOTION_OPTIONS = List.of(
            HexPieceType.QUEEN,
            HexPieceType.ROOK,
            HexPieceType.BISHOP,
            HexPieceType.KNIGHT);

    private HexMoveRules() {
    }

    static List<Jump> pawnAttacks(HexPieceColor color) {
        return color == HexPieceColor.WHITE ? WHITE_PAWN_ATTACKS : BLACK_PAWN_ATTACKS;
    }

    static List<Jump> knightJumps() {
        return KNIGHT_JUMPS;
    }

    static boolean isPawnStart(HexCoordinate coordinate, HexPieceColor color) {
        return color == HexPieceColor.WHITE
                ? WHITE_PAWN_STARTS.contains(coordinate)
                : BLACK_PAWN_STARTS.contains(coordinate);
    }

    static Set<HexCoordinate> standardDoubleMoveEligibleSquares() {
        return Stream.concat(WHITE_PAWN_STARTS.stream(), BLACK_PAWN_STARTS.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static Set<HexCoordinate> initialDoubleMoveEligibleSquares(HexBoard board) {
        if (board == null) {
            return Set.of();
        }

        return Stream.concat(
                        eligiblePawnStarts(board, WHITE_PAWN_STARTS, HexPieceColor.WHITE),
                        eligiblePawnStarts(board, BLACK_PAWN_STARTS, HexPieceColor.BLACK))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static Set<HexCoordinate> updateDoubleMoveEligibility(
            Set<HexCoordinate> current,
            HexMove move,
            HexCoordinate capturedAt) {
        Set<HexCoordinate> next = new LinkedHashSet<>(current == null ? Set.of() : current);
        next.remove(move.from());
        next.remove(move.to());
        if (capturedAt != null) {
            next.remove(capturedAt);
        }
        return Set.copyOf(next);
    }

    static boolean allowsPawnDoubleMoveFrom(
            HexCoordinate coordinate,
            HexPieceColor color,
            Set<HexCoordinate> doubleMoveEligibleSquares) {
        if (!isPawnStart(coordinate, color)) {
            return false;
        }

        return doubleMoveEligibleSquares != null && doubleMoveEligibleSquares.contains(coordinate);
    }

    private static Stream<HexCoordinate> eligiblePawnStarts(
            HexBoard board,
            List<HexCoordinate> starts,
            HexPieceColor color) {
        return starts.stream()
                .filter(start -> board.pieceAt(start)
                        .filter(piece -> piece.color() == color && piece.type() == HexPieceType.PAWN)
                        .isPresent());
    }

    static List<HexPieceType> promotionOptionsAt(HexCoordinate target, HexPieceColor color) {
        return HexBoardGeometry.isPromotionSquare(target, color)
                ? PROMOTION_OPTIONS
                : List.of();
    }

    static HexPieceType promotionFor(HexMove move, HexPiece piece) {
        if (piece == null || piece.type() != HexPieceType.PAWN) {
            return null;
        }

        return move.promotion();
    }

    static Optional<HexCoordinate> nextEnPassantTarget(HexMove move, HexPiece movingPiece) {
        if (movingPiece.type() != HexPieceType.PAWN) {
            return Optional.empty();
        }

        AxialCoordinate from = HexBoardGeometry.axial(move.from());
        AxialCoordinate to = HexBoardGeometry.axial(move.to());

        if (Math.abs(to.r() - from.r()) != 2 || to.q() != from.q()) {
            return Optional.empty();
        }

        int step = movingPiece.color() == HexPieceColor.WHITE ? 1 : -1;
        return HexBoardGeometry.shift(move.from(), 0, step);
    }

    static Optional<HexCoordinate> enPassantCapturedAt(HexMove move, HexPieceColor color) {
        return enPassantCapturedAt(move.to(), color);
    }

    static Optional<HexCoordinate> enPassantCapturedAt(HexCoordinate target, HexPieceColor color) {
        int behind = color == HexPieceColor.WHITE ? -1 : 1;
        return HexBoardGeometry.shift(target, 0, behind);
    }

    static boolean canCaptureEnPassant(
            HexBoard board,
            HexCoordinate target,
            HexCoordinate enPassantTarget,
            HexPieceColor color) {
        if (enPassantTarget == null || !target.equals(enPassantTarget) || !board.isEmpty(target)) {
            return false;
        }

        return enPassantCapturedAt(target, color)
                .flatMap(board::pieceAt)
                .filter(piece -> piece.type() == HexPieceType.PAWN)
                .filter(piece -> piece.color() == color.opponent())
                .isPresent();
    }

    static boolean sameMoveIntent(HexMove legalMove, HexMove requestedMove) {
        return legalMove.from().equals(requestedMove.from())
                && legalMove.to().equals(requestedMove.to())
                && legalMove.promotion() == requestedMove.promotion();
    }

    private static List<Jump> createKnightJumps() {
        List<HexDirection> directions = HexDirection.rookDirections();

        return IntStream.range(0, directions.size())
                .boxed()
                .flatMap(index -> {
                    HexDirection forward = directions.get(index);
                    HexDirection left = directions.get((index + 1) % directions.size());
                    HexDirection right = directions.get((index + directions.size() - 1) % directions.size());

                    return Stream.of(
                            new Jump(
                                    2 * forward.qDelta() + left.qDelta(),
                                    2 * forward.rDelta() + left.rDelta()),
                            new Jump(
                                    2 * forward.qDelta() + right.qDelta(),
                                    2 * forward.rDelta() + right.rDelta()));
                })
                .distinct()
                .toList();
    }

    record Jump(int qDelta, int rDelta) {
    }
}
