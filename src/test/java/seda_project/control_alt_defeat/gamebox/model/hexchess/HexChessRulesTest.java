package seda_project.control_alt_defeat.gamebox.model.hexchess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexChessRulesTest {

    @Test
    void boardHasNinetyOneCellsAndAdjacentCellsUseDifferentTones() {
        assertEquals(91, HexBoardGeometry.coordinates().size());

        HexBoardGeometry.coordinates().forEach(coordinate -> HexDirection.rookDirections()
                .stream()
                .map(direction -> HexBoardGeometry.neighbor(coordinate, direction))
                .flatMap(java.util.Optional::stream)
                .forEach(neighbor -> assertNotEquals(
                        HexBoardGeometry.tone(coordinate),
                        HexBoardGeometry.tone(neighbor))));
    }

    @Test
    void standardSetupUsesGlinskiMaterialCounts() {
        HexBoard board = HexBoard.standard();

        assertEquals(18, board.piecesOf(HexPieceColor.WHITE).count());
        assertEquals(18, board.piecesOf(HexPieceColor.BLACK).count());
        assertEquals(9, countPieces(board, HexPieceColor.WHITE, HexPieceType.PAWN));
        assertEquals(9, countPieces(board, HexPieceColor.BLACK, HexPieceType.PAWN));
        assertEquals(3, countPieces(board, HexPieceColor.WHITE, HexPieceType.BISHOP));
        assertEquals(3, countPieces(board, HexPieceColor.BLACK, HexPieceType.BISHOP));
    }

    @Test
    void customPawnsOnStandardStartSquaresDoNotGetInitialDoubleMove() {
        HexBoard board = HexBoard.empty()
                .withPiece(HexCoordinate.of("g1"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(HexCoordinate.of("g10"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(HexCoordinate.of("b1"), new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN));

        HexGameState state = HexGameState.create(board, HexPieceColor.WHITE, false);
        List<HexMove> moves = state.legalMovesFrom(HexCoordinate.of("b1"));

        assertTrue(moves.stream().anyMatch(move -> move.to().equals(HexCoordinate.of("b2"))));
        assertFalse(moves.stream().anyMatch(move -> move.to().equals(HexCoordinate.of("b3"))));
    }

    @Test
    void promotionMoveOffersAllOfficialPromotionPieces() {
        HexBoard board = HexBoard.empty()
                .withPiece(HexCoordinate.of("g1"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(HexCoordinate.of("g10"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(HexCoordinate.of("b6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN));

        List<HexPieceType> promotions = HexGameState.create(board, HexPieceColor.WHITE, false)
                .legalMovesFrom(HexCoordinate.of("b6"))
                .stream()
                .filter(move -> move.to().equals(HexCoordinate.of("b7")))
                .map(HexMove::promotion)
                .toList();

        assertEquals(List.of(
                HexPieceType.QUEEN,
                HexPieceType.ROOK,
                HexPieceType.BISHOP,
                HexPieceType.KNIGHT), promotions);
    }

    @Test
    void drawOfferCannotBeOverwrittenAndIsRevokedByMove() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        HexGameState secondOffer = offered.offerDraw(HexPieceColor.BLACK);

        assertEquals(HexPieceColor.WHITE, secondOffer.drawOfferBy());
        assertEquals("A draw offer is already pending.", secondOffer.statusMessage());

        HexGameState moved = offered.play(new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b3")));

        assertNull(moved.drawOfferBy());
        assertTrue(moved.statusMessage().startsWith("Draw offer revoked after move."));
    }

    private long countPieces(HexBoard board, HexPieceColor color, HexPieceType type) {
        return board.piecesOf(color)
                .filter(entry -> entry.getValue().type() == type)
                .count();
    }
}
