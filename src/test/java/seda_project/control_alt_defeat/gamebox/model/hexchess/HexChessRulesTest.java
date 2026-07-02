package seda_project.control_alt_defeat.gamebox.model.hexchess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HexChessRulesTest {

    @Test
    void standardBoardHasExpectedShapeAndMaterial() {
        HexBoard board = HexBoard.standard();

        assertEquals(91, HexBoardGeometry.coordinates().size());
        assertEquals(18, board.piecesOf(HexPieceColor.WHITE).count());
        assertEquals(18, board.piecesOf(HexPieceColor.BLACK).count());
        assertEquals(9, board.piecesOf(HexPieceColor.WHITE)
                .filter(entry -> entry.getValue().type() == HexPieceType.PAWN)
                .count());
    }
}
