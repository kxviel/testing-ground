package seda_project.control_alt_defeat.gamebox.model.hexchess;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class HexChessBot {

    private HexChessBot() {
    }

    public static Optional<HexMove> chooseMove(HexGameState state) {
        if (state == null || !state.isActive()) {
            return Optional.empty();
        }

        List<MoveCandidate> candidates = state.legalMovesForTurn()
                .stream()
                .map(move -> MoveCandidate.from(state, move))
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
                .filter(candidate -> !opponentHasMateInOne(candidate.nextState()))
                .toList();

        return safeMoves.stream()
                .min(moveOrder())
                .or(() -> candidates.stream().min(moveOrder()))
                .map(MoveCandidate::move);
    }

    private static boolean opponentHasMateInOne(HexGameState state) {
        return state.isActive()
                && state.legalMovesForTurn()
                .stream()
                .anyMatch(move -> state.play(move).status() == HexGameStatus.CHECKMATE);
    }

    private static Comparator<MoveCandidate> moveOrder() {
        return Comparator
                .comparing((MoveCandidate candidate) -> !candidate.isCapture())
                .thenComparing(candidate -> candidate.givesCheck() ? 0 : 1)
                .thenComparing(candidate -> candidate.move().from().notation())
                .thenComparing(candidate -> candidate.move().to().notation());
    }

    private static boolean isCapture(HexGameState state, HexMove move) {
        return state.board().pieceAt(move.to()).isPresent() || move.enPassant();
    }

    private record MoveCandidate(
            HexMove move,
            HexGameState nextState,
            boolean isCapture,
            boolean givesCheck,
            boolean isMate) {

        private static MoveCandidate from(HexGameState state, HexMove move) {
            HexGameState nextState = state.play(move);

            return new MoveCandidate(
                    move,
                    nextState,
                    HexChessBot.isCapture(state, move),
                    nextState.isInCheck(nextState.turn()),
                    nextState.status() == HexGameStatus.CHECKMATE);
        }
    }
}
