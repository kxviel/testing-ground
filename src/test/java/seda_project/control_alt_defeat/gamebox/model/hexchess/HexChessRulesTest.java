package seda_project.control_alt_defeat.gamebox.model.hexchess;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void customPositionRejectsElevenQueensForOneSide() {
        HexBoard board = addPieces(emptyBoardWithKings(), HexPieceColor.WHITE, HexPieceType.QUEEN, 11);

        HexPositionValidation validation = HexPositionValidator.validate(board, HexPieceColor.WHITE);

        assertFalse(validation.isValid());
        assertTrue(validation.message().contains("too many queens"));
    }

    @Test
    void customPositionRejectsCombinedPromotedMaterialBeyondMissingPawns() {
        HexBoard board = addPieces(emptyBoardWithKings(), HexPieceColor.WHITE, HexPieceType.QUEEN, 10);
        board = addPieces(board, HexPieceColor.WHITE, HexPieceType.ROOK, 3);

        HexPositionValidation validation = HexPositionValidator.validate(board, HexPieceColor.WHITE);

        assertFalse(validation.isValid());
        assertTrue(validation.message().contains("too many promoted pieces"));
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
    void standardOpeningHasSeventeenPawnMovesPerSide() {
        HexGameState whiteState = HexGameState.standard();
        long whitePawnMoves = HexStartingPosition.whitePawnStarts()
                .stream()
                .flatMap(start -> whiteState.legalMovesFrom(start).stream())
                .count();

        HexGameState blackState = HexGameState.create(HexBoard.standard(), HexPieceColor.BLACK, true);
        long blackPawnMoves = HexStartingPosition.blackPawnStarts()
                .stream()
                .flatMap(start -> blackState.legalMovesFrom(start).stream())
                .count();

        assertEquals(17, whitePawnMoves);
        assertEquals(17, blackPawnMoves);
        assertTrue(hasMoveTo(whiteState, "f5", "f6"));
        assertFalse(hasMoveTo(whiteState, "f5", "f7"));
        assertTrue(hasMoveTo(blackState, "f7", "f6"));
        assertFalse(hasMoveTo(blackState, "f7", "f5"));
    }

    @Test
    void standardPawnCanDoubleMoveAfterCapturingOntoOwnStartSquare() {
        HexBoard board = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("e4"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("f5"), piece(HexPieceColor.BLACK, HexPieceType.PAWN));

        HexGameState state = HexGameState.create(board, HexPieceColor.WHITE, true);
        state = state.play(moveTo(state, "e4", "f5"));
        state = state.play(moveTo(state, "l6", "i7"));

        assertTrue(hasMoveTo(state, "f5", "f6"));
        assertTrue(hasMoveTo(state, "f5", "f7"));
    }

    @Test
    void pawnCaptureVectorsUseForwardAdjacentHexes() {
        HexBoard whiteBoard = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("f6"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("e6"), piece(HexPieceColor.BLACK, HexPieceType.PAWN))
                .withPiece(coordinate("g6"), piece(HexPieceColor.BLACK, HexPieceType.PAWN))
                .withPiece(coordinate("e7"), piece(HexPieceColor.BLACK, HexPieceType.PAWN))
                .withPiece(coordinate("g7"), piece(HexPieceColor.BLACK, HexPieceType.PAWN));
        HexGameState whiteState = HexGameState.create(whiteBoard, HexPieceColor.WHITE, false);

        assertTrue(hasMoveTo(whiteState, "f6", "e6"));
        assertTrue(hasMoveTo(whiteState, "f6", "g6"));
        assertFalse(hasMoveTo(whiteState, "f6", "e7"));
        assertFalse(hasMoveTo(whiteState, "f6", "g7"));

        HexBoard blackBoard = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("f6"), piece(HexPieceColor.BLACK, HexPieceType.PAWN))
                .withPiece(coordinate("e5"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("g5"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("e6"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("g6"), piece(HexPieceColor.WHITE, HexPieceType.PAWN));
        HexGameState blackState = HexGameState.create(blackBoard, HexPieceColor.BLACK, false);

        assertTrue(hasMoveTo(blackState, "f6", "e5"));
        assertTrue(hasMoveTo(blackState, "f6", "g5"));
        assertFalse(hasMoveTo(blackState, "f6", "e6"));
        assertFalse(hasMoveTo(blackState, "f6", "g6"));
    }

    @Test
    void forgedEnPassantTargetWithoutCapturedPawnDoesNotCreateCapture() {
        HexBoard board = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("b5"), piece(HexPieceColor.WHITE, HexPieceType.PAWN));

        HexGameState state = new HexGameState(
                board,
                HexPieceColor.WHITE,
                HexGameStatus.RUNNING,
                "",
                null,
                coordinate("c6"),
                null,
                0,
                Map.of(),
                Set.of(),
                0,
                0);

        assertFalse(hasMoveTo(state, "b5", "c6"));
        assertFalse(state.legalMovesFrom(coordinate("b5")).stream().anyMatch(HexMove::enPassant));
    }

    @Test
    void validEnPassantRequiresAdjacentMovedPawnAndExpiresAfterOneReply() {
        HexBoard board = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("b5"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("c7"), piece(HexPieceColor.BLACK, HexPieceType.PAWN));

        HexGameState state = HexGameState.create(board, HexPieceColor.BLACK, true);
        state = state.play(moveTo(state, "c7", "c5"));

        assertTrue(hasEnPassantTo(state, "b5", "c6"));

        HexGameState captured = state.play(moveTo(state, "b5", "c6"));
        assertTrue(captured.board().pieceAt(coordinate("c5")).isEmpty());
        assertTrue(captured.board().pieceAt(coordinate("c6"))
                .filter(piece -> piece.color() == HexPieceColor.WHITE)
                .filter(piece -> piece.type() == HexPieceType.PAWN)
                .isPresent());

        HexGameState expired = state.play(moveTo(state, "a1", "a2"));
        expired = expired.play(moveTo(expired, "l6", "i7"));

        assertFalse(hasMoveTo(expired, "b5", "c6"));
        assertFalse(hasEnPassantTo(expired, "b5", "c6"));
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
    void pawnCapturePromotionOffersAllOfficialPromotionPieces() {
        HexBoard whiteBoard = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("f10"), piece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(coordinate("e10"), piece(HexPieceColor.BLACK, HexPieceType.PAWN));
        HexGameState whiteState = HexGameState.create(whiteBoard, HexPieceColor.WHITE, false);

        HexBoard blackBoard = HexBoard.empty()
                .withPiece(coordinate("a1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("l6"), piece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(coordinate("f2"), piece(HexPieceColor.BLACK, HexPieceType.PAWN))
                .withPiece(coordinate("e1"), piece(HexPieceColor.WHITE, HexPieceType.PAWN));
        HexGameState blackState = HexGameState.create(blackBoard, HexPieceColor.BLACK, false);

        List<HexPieceType> expectedPromotions = List.of(
                HexPieceType.QUEEN,
                HexPieceType.ROOK,
                HexPieceType.BISHOP,
                HexPieceType.KNIGHT);

        assertEquals(expectedPromotions, promotionsForMove(whiteState, "f10", "e10"));
        assertEquals(expectedPromotions, promotionsForMove(blackState, "f2", "e1"));
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

    private HexBoard emptyBoardWithKings() {
        return HexBoard.empty()
                .withPiece(coordinate("g1"), piece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(coordinate("g10"), piece(HexPieceColor.BLACK, HexPieceType.KING));
    }

    private HexBoard addPieces(HexBoard board, HexPieceColor color, HexPieceType type, int count) {
        HexBoard next = board;
        int placed = 0;
        for (HexCoordinate coordinate : HexBoardGeometry.coordinates()) {
            if (next.pieceAt(coordinate).isPresent()) {
                continue;
            }
            next = next.withPiece(coordinate, piece(color, type));
            placed++;
            if (placed == count) {
                return next;
            }
        }
        throw new AssertionError("Not enough empty coordinates to place pieces.");
    }

    private boolean hasMoveTo(HexGameState state, String from, String to) {
        HexCoordinate target = coordinate(to);
        return state.legalMovesFrom(coordinate(from))
                .stream()
                .anyMatch(move -> move.to().equals(target));
    }

    private boolean hasEnPassantTo(HexGameState state, String from, String to) {
        HexCoordinate target = coordinate(to);
        return state.legalMovesFrom(coordinate(from))
                .stream()
                .anyMatch(move -> move.to().equals(target) && move.enPassant());
    }

    private HexMove moveTo(HexGameState state, String from, String to) {
        HexCoordinate target = coordinate(to);
        return state.legalMovesFrom(coordinate(from))
                .stream()
                .filter(move -> move.to().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected legal move " + from + "-" + to));
    }

    private List<HexPieceType> promotionsForMove(HexGameState state, String from, String to) {
        HexCoordinate target = coordinate(to);
        return state.legalMovesFrom(coordinate(from))
                .stream()
                .filter(move -> move.to().equals(target))
                .map(HexMove::promotion)
                .toList();
    }

    private HexCoordinate coordinate(String notation) {
        return HexCoordinate.of(notation);
    }

    private HexPiece piece(HexPieceColor color, HexPieceType type) {
        return new HexPiece(color, type);
    }
}
