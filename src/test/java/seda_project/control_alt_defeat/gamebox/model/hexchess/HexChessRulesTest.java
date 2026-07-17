package seda_project.control_alt_defeat.gamebox.model.hexchess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @Test
    void customMaterialAllowsAtMostTenQueensAfterNinePromotions() {
        Map<HexCoordinate, HexPiece> tenQueenPieces = new LinkedHashMap<>();
        var coordinates = HexBoardGeometry.coordinates().iterator();
        tenQueenPieces.put(coordinates.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        tenQueenPieces.put(coordinates.next(), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        for (int queen = 0; queen < 10; queen++) {
            tenQueenPieces.put(coordinates.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.QUEEN));
        }

        assertTrue(HexPositionValidator.validateMaterial(new HexBoard(tenQueenPieces)).isValid());

        tenQueenPieces.put(coordinates.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.QUEEN));
        HexPositionValidation validation = HexPositionValidator.validateMaterial(new HexBoard(tenQueenPieces));
        assertFalse(validation.isValid());
        assertTrue(validation.message().contains("maximum is 10"));
    }

    @Test
    void customMaterialRestrictsEachSideToEighteenPieces() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        var coordinates = HexBoardGeometry.coordinates().iterator();
        for (int pawn = 0; pawn < 19; pawn++) {
            pieces.put(coordinates.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN));
        }

        HexPositionValidation validation = HexPositionValidator.validateMaterial(new HexBoard(pieces));
        assertFalse(validation.isValid());
        assertTrue(validation.message().contains("maximum is 18"));
    }
}
