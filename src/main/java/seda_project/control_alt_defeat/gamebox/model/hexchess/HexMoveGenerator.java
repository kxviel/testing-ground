package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class HexMoveGenerator {

    private HexMoveGenerator() {
    }

    public static List<HexMove> pseudoLegalMoves(
            HexBoard board,
            HexPieceColor color,
            HexCoordinate enPassantTarget) {
        return board.piecesOf(color)
                .flatMap(entry -> pseudoLegalMovesFrom(board, entry.getKey(), enPassantTarget, false).stream())
                .toList();
    }

    public static List<HexMove> pseudoLegalMovesFrom(
            HexBoard board,
            HexCoordinate from,
            HexCoordinate enPassantTarget,
            boolean attacksOnly) {
        HexPiece piece = board.pieceAt(from).orElse(null);
        if (piece == null) {
            return List.of();
        }

        return switch (piece.type()) {
            case KING -> stepMoves(board, from, HexDirection.queenDirections(), piece.color(), attacksOnly);
            case QUEEN -> slidingMoves(board, from, HexDirection.queenDirections(), piece.color(), attacksOnly);
            case ROOK -> slidingMoves(board, from, HexDirection.rookDirections(), piece.color(), attacksOnly);
            case BISHOP -> slidingMoves(board, from, HexDirection.bishopDirections(), piece.color(), attacksOnly);
            case KNIGHT -> jumpMoves(board, from, HexMoveRules.knightJumps(), piece.color(), attacksOnly);
            case PAWN -> pawnMoves(board, from, piece.color(), enPassantTarget, attacksOnly);
            case CUSTOM -> List.of();
        };
    }

    private static List<HexMove> slidingMoves(
            HexBoard board,
            HexCoordinate from,
            List<HexDirection> directions,
            HexPieceColor color,
            boolean attacksOnly) {
        return directions.stream()
                .flatMap(direction -> slidingMovesInDirection(board, from, direction, color, attacksOnly).stream())
                .toList();
    }

    private static List<HexMove> slidingMovesInDirection(
            HexBoard board,
            HexCoordinate from,
            HexDirection direction,
            HexPieceColor color,
            boolean attacksOnly) {
        Stream.Builder<HexMove> moves = Stream.builder();

        for (HexCoordinate target : HexBoardGeometry.ray(from, direction)) {
            Optional<HexPiece> targetPiece = board.pieceAt(target);

            if (targetPiece.isEmpty()) {
                moves.add(new HexMove(from, target));
                continue;
            }

            if (attacksOnly || targetPiece.filter(piece -> piece.color() != color).isPresent()) {
                moves.add(new HexMove(from, target));
            }
            break;
        }

        return moves.build().toList();
    }

    private static List<HexMove> stepMoves(
            HexBoard board,
            HexCoordinate from,
            List<HexDirection> directions,
            HexPieceColor color,
            boolean attacksOnly) {
        return directions.stream()
                .map(direction -> HexBoardGeometry.neighbor(from, direction))
                .flatMap(Optional::stream)
                .filter(canLandOn(board, color, attacksOnly))
                .map(target -> new HexMove(from, target))
                .toList();
    }

    private static List<HexMove> jumpMoves(
            HexBoard board,
            HexCoordinate from,
            List<HexMoveRules.Jump> jumps,
            HexPieceColor color,
            boolean attacksOnly) {
        return jumps.stream()
                .map(jump -> HexBoardGeometry.shift(from, jump.qDelta(), jump.rDelta()))
                .flatMap(Optional::stream)
                .filter(canLandOn(board, color, attacksOnly))
                .map(target -> new HexMove(from, target))
                .toList();
    }

    private static Predicate<HexCoordinate> canLandOn(HexBoard board, HexPieceColor color, boolean attacksOnly) {
        return target -> attacksOnly
                || board.pieceAt(target)
                .map(piece -> piece.color() != color)
                .orElse(true);
    }

    private static List<HexMove> pawnMoves(
            HexBoard board,
            HexCoordinate from,
            HexPieceColor color,
            HexCoordinate enPassantTarget,
            boolean attacksOnly) {
        List<HexMoveRules.Jump> attacks = HexMoveRules.pawnAttacks(color);

        if (attacksOnly) {
            return attacks.stream()
                    .map(jump -> HexBoardGeometry.shift(from, jump.qDelta(), jump.rDelta()))
                    .flatMap(Optional::stream)
                    .map(target -> new HexMove(from, target))
                    .toList();
        }

        Stream<HexMove> captures = attacks.stream()
                .map(jump -> HexBoardGeometry.shift(from, jump.qDelta(), jump.rDelta()))
                .flatMap(Optional::stream)
                .filter(target -> isEnemyPiece(board, target, color)
                        || target.equals(enPassantTarget))
                .map(target -> new HexMove(
                        from,
                        target,
                        HexMoveRules.promotionAt(target, color).orElse(null),
                        target.equals(enPassantTarget)));

        return Stream.concat(forwardPawnMoves(board, from, color), captures).toList();
    }

    private static Stream<HexMove> forwardPawnMoves(HexBoard board, HexCoordinate from, HexPieceColor color) {
        HexDirection forward = color == HexPieceColor.WHITE ? HexDirection.NORTH : HexDirection.SOUTH;
        Optional<HexCoordinate> oneStep = HexBoardGeometry.neighbor(from, forward)
                .filter(board::isEmpty);

        Stream<HexMove> singleMove = oneStep.stream()
                .map(target -> new HexMove(from, target, HexMoveRules.promotionAt(target, color).orElse(null), false));

        Stream<HexMove> doubleMove = oneStep
                .filter(ignored -> HexMoveRules.isPawnStart(from, color))
                .flatMap(target -> HexBoardGeometry.neighbor(target, forward))
                .filter(board::isEmpty)
                .map(target -> new HexMove(from, target))
                .stream();

        return Stream.concat(singleMove, doubleMove);
    }

    private static boolean isEnemyPiece(HexBoard board, HexCoordinate target, HexPieceColor color) {
        return board.pieceAt(target)
                .map(piece -> piece.color() != color)
                .orElse(false);
    }
}
