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

        List<HexMove> legalMoves = state.legalMovesForTurn();
        if (legalMoves.isEmpty()) {
            return Optional.empty();
        }

        Optional<HexMove> mateInOne = legalMoves.stream()
                .filter(move -> state.play(move).status() == HexGameStatus.CHECKMATE)
                .findFirst();

        if (mateInOne.isPresent()) {
            return mateInOne;
        }

        List<HexMove> safeMoves = legalMoves.stream()
                .filter(move -> !opponentHasMateInOne(state.play(move)))
                .toList();

        return safeMoves.stream()
                .min(moveOrder(state))
                .or(() -> legalMoves.stream().min(moveOrder(state)));
    }

    private static boolean opponentHasMateInOne(HexGameState state) {
        return state.isActive()
                && state.legalMovesForTurn()
                .stream()
                .anyMatch(move -> state.play(move).status() == HexGameStatus.CHECKMATE);
    }

    private static Comparator<HexMove> moveOrder(HexGameState state) {
        return Comparator
                .comparing((HexMove move) -> !isCapture(state, move))
                .thenComparing(move -> state.play(move).isInCheck(state.play(move).turn()) ? 0 : 1)
                .thenComparing(move -> move.from().notation())
                .thenComparing(move -> move.to().notation());
    }

    private static boolean isCapture(HexGameState state, HexMove move) {
        return state.board().pieceAt(move.to()).isPresent() || move.enPassant();
    }
}
