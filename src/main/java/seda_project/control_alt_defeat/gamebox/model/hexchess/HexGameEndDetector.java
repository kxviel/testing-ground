package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class HexGameEndDetector {

    private HexGameEndDetector() {
    }

    static HexGameResolution evaluate(
            HexBoard board,
            HexPieceColor turn,
            HexCoordinate enPassantTarget,
            int halfMoveClock,
            Map<String, Integer> repetitionCounts) {
        return evaluate(
                board,
                turn,
                enPassantTarget,
                halfMoveClock,
                repetitionCounts,
                HexMoveRules.standardDoubleMoveEligibleSquares());
    }

    static HexGameResolution evaluate(
            HexBoard board,
            HexPieceColor turn,
            HexCoordinate enPassantTarget,
            int halfMoveClock,
            Map<String, Integer> repetitionCounts,
            Set<HexCoordinate> doubleMoveEligibleSquares) {
        boolean check = HexLegalMoveValidator.isInCheck(board, turn);
        boolean noLegalMoves = HexLegalMoveValidator.legalMoves(
                board,
                turn,
                enPassantTarget,
                doubleMoveEligibleSquares).isEmpty();
        HexPieceColor mover = turn.opponent();

        if (check && noLegalMoves) {
            return HexGameResolution.terminal(
                    HexGameStatus.CHECKMATE,
                    "Checkmate. " + mover.displayName() + " wins.",
                    mover == HexPieceColor.WHITE ? 1 : 0,
                    mover == HexPieceColor.BLACK ? 1 : 0);
        }

        if (!check && noLegalMoves) {
            return HexGameResolution.terminal(
                    HexGameStatus.STALEMATE,
                    "Stalemate. " + mover.displayName() + " gets 0.75 points.",
                    mover == HexPieceColor.WHITE ? 0.75 : 0.25,
                    mover == HexPieceColor.BLACK ? 0.75 : 0.25);
        }

        if (halfMoveClock >= HexMoveRules.FIFTY_MOVE_RULE_PLY) {
            return HexGameResolution.draw("Draw by 50-move rule.");
        }

        if (repetitionCounts.values().stream().anyMatch(count -> count >= 3)) {
            return HexGameResolution.draw("Draw by threefold repetition.");
        }

        if (hasInsufficientMaterial(board)) {
            return HexGameResolution.draw("Draw by insufficient material.");
        }

        return HexGameResolution.active(
                check ? HexGameStatus.CHECK : HexGameStatus.RUNNING,
                check ? turn.displayName() + " is in check." : turn.displayName() + " to move.");
    }

    static boolean hasInsufficientMaterial(HexBoard board) {
        boolean hasMajorOrPawn = board.pieces()
                .values()
                .stream()
                .anyMatch(piece -> switch (piece.type()) {
                    case PAWN, ROOK, QUEEN, CUSTOM -> true;
                    default -> false;
                });

        if (hasMajorOrPawn) {
            return false;
        }

        Map<HexPieceColor, Long> minorCounts = board.pieces()
                .values()
                .stream()
                .filter(piece -> piece.type() != HexPieceType.KING)
                .collect(Collectors.groupingBy(HexPiece::color, Collectors.counting()));

        return minorCounts.getOrDefault(HexPieceColor.WHITE, 0L) <= 1
                && minorCounts.getOrDefault(HexPieceColor.BLACK, 0L) <= 1;
    }
}

record HexGameResolution(
        HexGameStatus status,
        String message,
        double whiteScore,
        double blackScore,
        boolean terminal) {

    static HexGameResolution active(HexGameStatus status, String message) {
        return new HexGameResolution(status, message, 0, 0, false);
    }

    static HexGameResolution terminal(
            HexGameStatus status,
            String message,
            double whiteScore,
            double blackScore) {
        return new HexGameResolution(status, message, whiteScore, blackScore, true);
    }

    static HexGameResolution draw(String message) {
        return terminal(HexGameStatus.DRAW, message, 0.5, 0.5);
    }
}
