package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class HexChessBot {

    private HexChessBot() {
    }

    public static Optional<HexMove> chooseMove(HexGameState state) {
        if (state == null || !state.isActive()) {
            return Optional.empty();
        }

        List<HexMove> legalMoves = state.legalMovesForTurn();
        if (legalMoves.size() == 1) {
            return Optional.of(legalMoves.getFirst());
        }

        BotPosition position = BotPosition.from(state);
        List<MoveCandidate> candidates = legalMoves
                .stream()
                .map(move -> MoveCandidate.from(position, move))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Optional<HexMove> mateInOne = candidates.stream()
                .filter(MoveCandidate::isMate)
                .map(MoveCandidate::move)
                .findFirst();

        if (mateInOne.isPresent()) {
            return mateInOne;
        }

        List<MoveCandidate> safeMoves = candidates.stream()
                .filter(candidate -> !opponentHasMateInOne(candidate.nextPosition()))
                .toList();

        return safeMoves.stream()
                .min(moveOrder())
                .or(() -> candidates.stream().min(moveOrder()))
                .map(MoveCandidate::move);
    }

    private static boolean opponentHasMateInOne(BotPosition position) {
        return HexLegalMoveValidator.legalMoves(
                        position.board(),
                        position.turn(),
                        position.enPassantTarget(),
                        position.doubleMoveEligibleSquares())
                .stream()
                .map(position::apply)
                .anyMatch(BotPosition::isCheckmate);
    }

    private static Comparator<MoveCandidate> moveOrder() {
        return Comparator
                .comparing((MoveCandidate candidate) -> !candidate.isCapture())
                .thenComparing(candidate -> candidate.givesCheck() ? 0 : 1)
                .thenComparing(candidate -> candidate.move().from().notation())
                .thenComparing(candidate -> candidate.move().to().notation());
    }

    private static boolean isCapture(BotPosition position, HexMove move) {
        return position.board().pieceAt(move.to()).isPresent() || move.enPassant();
    }

    private record BotPosition(
            HexBoard board,
            HexPieceColor turn,
            HexCoordinate enPassantTarget,
            Set<HexCoordinate> doubleMoveEligibleSquares) {

        private static BotPosition from(HexGameState state) {
            return new BotPosition(
                    state.board(),
                    state.turn(),
                    state.enPassantTarget(),
                    state.doubleMoveEligibleSquares());
        }

        private BotPosition apply(HexMove move) {
            HexPiece movingPiece = board.pieceAt(move.from())
                    .orElseThrow(() -> new IllegalStateException("Bot move has no moving piece."));
            HexCoordinate capturedAt = move.enPassant()
                    ? HexMoveRules.enPassantCapturedAt(move, movingPiece.color()).orElse(move.to())
                    : move.to();
            HexPieceType promotion = HexMoveRules.promotionFor(move, movingPiece);
            HexBoard nextBoard = board.applyMove(move, capturedAt, promotion);

            return new BotPosition(
                    nextBoard,
                    turn.opponent(),
                    HexMoveRules.nextEnPassantTarget(move, movingPiece).orElse(null),
                    updateDoubleMoveEligibility(move, capturedAt));
        }

        private boolean isCheckmate() {
            return HexLegalMoveValidator.isInCheck(board, turn)
                    && HexLegalMoveValidator.legalMoves(
                            board,
                            turn,
                            enPassantTarget,
                            doubleMoveEligibleSquares).isEmpty();
        }

        private Set<HexCoordinate> updateDoubleMoveEligibility(HexMove move, HexCoordinate capturedAt) {
            Set<HexCoordinate> next = new LinkedHashSet<>(doubleMoveEligibleSquares);
            next.remove(move.from());
            next.remove(move.to());
            if (capturedAt != null) {
                next.remove(capturedAt);
            }
            return Set.copyOf(next);
        }
    }

    private record MoveCandidate(
            HexMove move,
            BotPosition nextPosition,
            boolean isCapture,
            boolean givesCheck,
            boolean isMate) {

        private static MoveCandidate from(BotPosition position, HexMove move) {
            BotPosition nextPosition = position.apply(move);

            return new MoveCandidate(
                    move,
                    nextPosition,
                    HexChessBot.isCapture(position, move),
                    HexLegalMoveValidator.isInCheck(nextPosition.board(), nextPosition.turn()),
                    nextPosition.isCheckmate());
        }
    }
}
