package seda_project.control_alt_defeat.gamebox.model.hexchess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class HexChessBotTest {

    @Test
    void nullAndFinishedGamesProduceNoMove() {
        assertTrue(HexChessBot.chooseMove(null).isEmpty());
        assertTrue(HexChessBot.chooseMove(HexGameState.standard().resign()).isEmpty());
    }

    @Test
    void standardPositionProducesADeterministicLegalMoveQuickly() {
        HexGameState state = HexGameState.standard();

        Optional<HexMove> first = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(3), () -> HexChessBot.chooseMove(state));
        Optional<HexMove> second = HexChessBot.chooseMove(state);

        assertTrue(first.isPresent());
        assertEquals(first, second);
        assertTrue(state.legalMovesForTurn().contains(first.orElseThrow()));
    }

    @Test
    void botPrefersASafeCaptureWhenOneIsAvailable() {
        HexBoard board = HexBoard.empty()
                .withPiece(HexCoordinate.of("a1"), white(HexPieceType.KING))
                .withPiece(HexCoordinate.of("l6"), black(HexPieceType.KING))
                .withPiece(HexCoordinate.of("f6"), white(HexPieceType.ROOK))
                .withPiece(HexCoordinate.of("f8"), black(HexPieceType.PAWN));
        HexGameState state = HexGameState.create(board, HexPieceColor.WHITE, false);
        List<HexMove> captures = state.legalMovesForTurn().stream()
                .filter(move -> board.pieceAt(move.to()).isPresent())
                .toList();

        assertFalse(captures.isEmpty());
        HexMove chosen = HexChessBot.chooseMove(state).orElseThrow();

        assertTrue(state.legalMovesForTurn().contains(chosen));
        assertTrue(board.pieceAt(chosen.to()).isPresent(), () -> "Expected capture, got " + chosen.notation());
    }

    @Test
    void everyChosenMoveCanBeAppliedWithoutLeavingTheMoverInCheck() {
        HexGameState state = HexGameState.standard();

        for (int ply = 0; ply < 12 && state.isActive(); ply++) {
            HexPieceColor mover = state.turn();
            HexMove chosen = HexChessBot.chooseMove(state).orElseThrow();
            assertTrue(state.legalMovesForTurn().contains(chosen));

            HexGameState next = state.play(chosen);
            assertFalse(next.isInCheck(mover), () -> "Bot selected a self-checking move: " + chosen.notation());
            state = next;
        }
    }

    private static HexPiece white(HexPieceType type) {
        return new HexPiece(HexPieceColor.WHITE, type);
    }

    private static HexPiece black(HexPieceType type) {
        return new HexPiece(HexPieceColor.BLACK, type);
    }
}
