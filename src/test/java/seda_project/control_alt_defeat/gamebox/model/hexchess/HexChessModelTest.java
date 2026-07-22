package seda_project.control_alt_defeat.gamebox.model.hexchess;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.network.hexchess.HexChessStateSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the HexChess game model.
 *
 * <p>
 * Covers: board geometry, coordinate parsing, piece movement generation,
 * legal-move filtering, check/checkmate/stalemate detection, draw rules,
 * game-state transitions (resign, draw offer/accept/decline), and
 * position validation.
 */
class HexChessModelTest {

    // ─────────────────────────────────────────────────────────────
    // HexCoordinate
    // ─────────────────────────────────────────────────────────────

    @Test
    void coordinate_parsing_lowercaseAndUppercase_areEquivalent() {
        HexCoordinate lower = HexCoordinate.of("f6");
        HexCoordinate upper = HexCoordinate.of("F6");
        assertEquals(lower, upper);
    }

    @Test
    void coordinate_notation_roundTrip() {
        HexCoordinate coord = HexCoordinate.of("e4");
        assertEquals("e4", coord.notation());
    }

    @Test
    void coordinate_invalidFile_throws() {
        assertThrows(IllegalArgumentException.class, () -> HexCoordinate.of("z5"));
    }

    @Test
    void coordinate_rankTooLow_throws() {
        // rank < 1 → constructor throws
        assertThrows(IllegalArgumentException.class, () -> new HexCoordinate('f', 0));
    }

    @Test
    void coordinate_outsideBoard_throws() {
        // 'a' file has length 6, so rank 7 is off the board
        assertThrows(IllegalArgumentException.class, () -> HexCoordinate.of("a7"));
    }

    @Test
    void coordinate_compareTo_sortsByFileFirst() {
        HexCoordinate a1 = HexCoordinate.of("a1");
        HexCoordinate b1 = HexCoordinate.of("b1");
        assertTrue(a1.compareTo(b1) < 0);
    }

    @Test
    void coordinate_compareTo_sortsByRankWithinFile() {
        HexCoordinate f3 = HexCoordinate.of("f3");
        HexCoordinate f7 = HexCoordinate.of("f7");
        assertTrue(f3.compareTo(f7) < 0);
    }

    // ─────────────────────────────────────────────────────────────
    // HexBoardGeometry
    // ─────────────────────────────────────────────────────────────

    @Test
    void geometry_totalSquares_is91() {
        assertEquals(91, HexBoardGeometry.coordinates().size());
    }

    @Test
    void geometry_isValid_centre_isTrue() {
        assertTrue(HexBoardGeometry.isValid(HexCoordinate.of("f6")));
    }

    @Test
    void geometry_isValid_null_isFalse() {
        assertFalse(HexBoardGeometry.isValid(null));
    }

    @Test
    void geometry_isValid_unknownFile_isFalse() {
        // Manually construct a coordinate with an invalid file character
        // (bypasses the of() validation)
        HexCoordinate bad = new HexCoordinate('z', 1);
        assertFalse(HexBoardGeometry.isValid(bad));
    }

    @Test
    void geometry_fileLength_centreFileF_is11() {
        // File 'f' is the centre column of radius-5 board
        assertEquals(11, HexBoardGeometry.fileLength('f'));
    }

    @Test
    void geometry_fileLength_cornerFileA_is6() {
        assertEquals(6, HexBoardGeometry.fileLength('a'));
    }

    @Test
    void geometry_promotionSquare_whiteTopOfFile() {
        // White promotes at rank == fileLength
        HexCoordinate topF = HexCoordinate.of("f11");
        assertTrue(HexBoardGeometry.isPromotionSquare(topF, HexPieceColor.WHITE));
    }

    @Test
    void geometry_promotionSquare_blackRankOne() {
        HexCoordinate rank1 = HexCoordinate.of("f1");
        assertTrue(HexBoardGeometry.isPromotionSquare(rank1, HexPieceColor.BLACK));
    }

    @Test
    void geometry_promotionSquare_middleSquare_false() {
        HexCoordinate mid = HexCoordinate.of("f6");
        assertFalse(HexBoardGeometry.isPromotionSquare(mid, HexPieceColor.WHITE));
        assertFalse(HexBoardGeometry.isPromotionSquare(mid, HexPieceColor.BLACK));
    }

    @Test
    void geometry_ray_returnsConnectedPath() {
        // Ray from f1 going up along the f-file (via the direction that moves through
        // f)
        HexCoordinate start = HexCoordinate.of("f1");
        List<HexCoordinate> ray = HexBoardGeometry.ray(start, HexDirection.rookDirections().get(0));
        assertFalse(ray.isEmpty());
        // First square in the ray must be adjacent
        HexCoordinate first = ray.get(0);
        assertNotNull(first);
    }

    @Test
    void geometry_shift_withinBounds_returnsPresent() {
        HexCoordinate f6 = HexCoordinate.of("f6");
        Optional<HexCoordinate> shifted = HexBoardGeometry.shift(f6, 0, 1);
        assertTrue(shifted.isPresent());
    }

    @Test
    void geometry_shift_offBoard_returnsEmpty() {
        // Shift 'a1' far off the board
        HexCoordinate corner = HexCoordinate.of("a1");
        Optional<HexCoordinate> shifted = HexBoardGeometry.shift(corner, -10, -10);
        assertTrue(shifted.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // HexBoard
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_empty_hasNoPieces() {
        HexBoard empty = HexBoard.empty();
        assertEquals(0, empty.pieces().size());
    }

    @Test
    void board_standard_has36Pieces() {
        HexBoard board = HexBoard.standard();
        assertEquals(36, board.pieces().size());
    }

    @Test
    void board_standard_white_has18Pieces() {
        HexBoard board = HexBoard.standard();
        assertEquals(18, board.piecesOf(HexPieceColor.WHITE).count());
    }

    @Test
    void board_standard_black_has18Pieces() {
        HexBoard board = HexBoard.standard();
        assertEquals(18, board.piecesOf(HexPieceColor.BLACK).count());
    }

    @Test
    void board_standard_white_has9Pawns() {
        HexBoard board = HexBoard.standard();
        long pawns = board.piecesOf(HexPieceColor.WHITE)
                .filter(e -> e.getValue().type() == HexPieceType.PAWN)
                .count();
        assertEquals(9, pawns);
    }

    @Test
    void board_standard_hasWhiteKingAtG1() {
        HexBoard board = HexBoard.standard();
        Optional<HexPiece> piece = board.pieceAt(HexCoordinate.of("g1"));
        assertTrue(piece.isPresent());
        assertEquals(HexPieceType.KING, piece.get().type());
        assertEquals(HexPieceColor.WHITE, piece.get().color());
    }

    @Test
    void board_standard_hasBlackKingAtG10() {
        HexBoard board = HexBoard.standard();
        Optional<HexPiece> piece = board.pieceAt(HexCoordinate.of("g10"));
        assertTrue(piece.isPresent());
        assertEquals(HexPieceType.KING, piece.get().type());
        assertEquals(HexPieceColor.BLACK, piece.get().color());
    }

    @Test
    void board_withPiece_addsPiece() {
        HexBoard board = HexBoard.empty()
                .withPiece(HexCoordinate.of("f6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        assertTrue(board.pieceAt(HexCoordinate.of("f6")).isPresent());
    }

    @Test
    void board_withoutPiece_removesPiece() {
        HexBoard board = HexBoard.standard().withoutPiece(HexCoordinate.of("g1"));
        assertTrue(board.pieceAt(HexCoordinate.of("g1")).isEmpty());
    }

    @Test
    void board_isEmpty_trueForEmptySquare() {
        HexBoard board = HexBoard.empty();
        assertTrue(board.isEmpty(HexCoordinate.of("f6")));
    }

    @Test
    void board_isEmpty_falseForOccupiedSquare() {
        HexBoard board = HexBoard.standard();
        assertFalse(board.isEmpty(HexCoordinate.of("g1")));
    }

    @Test
    void board_kingPosition_findsWhiteKing() {
        HexBoard board = HexBoard.standard();
        Optional<HexCoordinate> pos = board.kingPosition(HexPieceColor.WHITE);
        assertTrue(pos.isPresent());
        assertEquals("g1", pos.get().notation());
    }

    @Test
    void board_withInvalidCoordinate_ignored() {
        HexBoard board = HexBoard.empty();
        HexCoordinate bad = new HexCoordinate('a', 99);
        HexBoard same = board.withPiece(bad, new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        // Invalid coord → board unchanged
        assertEquals(board.pieces().size(), same.pieces().size());
    }

    @Test
    void board_nullConstructor_createsEmpty() {
        HexBoard board = new HexBoard(null);
        assertTrue(board.pieces().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // HexPiece
    // ─────────────────────────────────────────────────────────────

    @Test
    void piece_displayText_allTypes() {
        assertEquals("♚", new HexPiece(HexPieceColor.WHITE, HexPieceType.KING).displayText());
        assertEquals("♛", new HexPiece(HexPieceColor.WHITE, HexPieceType.QUEEN).displayText());
        assertEquals("♜", new HexPiece(HexPieceColor.WHITE, HexPieceType.ROOK).displayText());
        assertEquals("♝", new HexPiece(HexPieceColor.WHITE, HexPieceType.BISHOP).displayText());
        assertEquals("♞", new HexPiece(HexPieceColor.WHITE, HexPieceType.KNIGHT).displayText());
        assertEquals("♟", new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN).displayText());
        assertEquals("◆", new HexPiece(HexPieceColor.WHITE, HexPieceType.CUSTOM).displayText());
    }

    @Test
    void piece_nullColor_throws() {
        assertThrows(NullPointerException.class,
                () -> new HexPiece(null, HexPieceType.KING));
    }

    // ─────────────────────────────────────────────────────────────
    // HexPieceColor
    // ─────────────────────────────────────────────────────────────

    @Test
    void color_opponent_returnsOpposite() {
        assertEquals(HexPieceColor.BLACK, HexPieceColor.WHITE.opponent());
        assertEquals(HexPieceColor.WHITE, HexPieceColor.BLACK.opponent());
    }

    @Test
    void color_displayName() {
        assertEquals("White", HexPieceColor.WHITE.displayName());
        assertEquals("Black", HexPieceColor.BLACK.displayName());
    }

    // ─────────────────────────────────────────────────────────────
    // HexMove
    // ─────────────────────────────────────────────────────────────

    @Test
    void move_notation_noPromotion() {
        HexMove move = new HexMove(HexCoordinate.of("e4"), HexCoordinate.of("f5"));
        assertEquals("e4-f5", move.notation());
    }

    @Test
    void move_notation_withPromotion() {
        HexMove move = new HexMove(
                HexCoordinate.of("f10"), HexCoordinate.of("f11"),
                HexPieceType.QUEEN, false);
        assertEquals("f10-f11=Q", move.notation());
    }

    @Test
    void move_nullFrom_throws() {
        assertThrows(NullPointerException.class,
                () -> new HexMove(null, HexCoordinate.of("f6")));
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – creation and initial state
    // ─────────────────────────────────────────────────────────────

    @Test
    void gameState_standard_isRunning() {
        HexGameState state = HexGameState.standard();
        assertEquals(HexGameStatus.RUNNING, state.status());
        assertEquals(HexPieceColor.WHITE, state.turn());
        assertTrue(state.isActive());
    }

    @Test
    void gameState_standard_whiteMustHaveLegalMoves() {
        HexGameState state = HexGameState.standard();
        assertFalse(state.legalMovesForTurn().isEmpty());
    }

    @Test
    void gameState_standard_scoresAreZero() {
        HexGameState state = HexGameState.standard();
        assertEquals(0.0, state.whiteScore());
        assertEquals(0.0, state.blackScore());
    }

    @Test
    void gameState_standard_noEnPassantTarget() {
        assertNull(HexGameState.standard().enPassantTarget());
    }

    @Test
    void gameState_standard_noDrawOffer() {
        assertNull(HexGameState.standard().drawOfferBy());
    }

    @Test
    void gameState_standard_halfMoveClockIsZero() {
        assertEquals(0, HexGameState.standard().halfMoveClock());
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – play() basics
    // ─────────────────────────────────────────────────────────────

    @Test
    void play_illegalMove_returnsMessageWithSameTurn() {
        HexGameState state = HexGameState.standard();
        // Try to move black's king when it's white's turn
        HexMove illegalMove = new HexMove(
                HexCoordinate.of("g10"), HexCoordinate.of("g9"));
        HexGameState next = state.play(illegalMove);
        assertEquals(HexPieceColor.WHITE, next.turn()); // still White's turn
        assertTrue(next.statusMessage().contains("Illegal move"));
    }

    @Test
    void play_nullMove_returnsSameState() {
        HexGameState state = HexGameState.standard();
        HexGameState next = state.play(null);
        assertSame(state, next);
    }

    @Test
    void play_validPawnMove_switchesTurn() {
        HexGameState state = HexGameState.standard();
        // White pawn at b1 can move to b2
        HexMove pawnAdvance = new HexMove(
                HexCoordinate.of("b1"), HexCoordinate.of("b2"));
        HexGameState next = state.play(pawnAdvance);
        assertEquals(HexPieceColor.BLACK, next.turn());
    }

    @Test
    void play_validPawnMove_pieceIsPlaced() {
        HexGameState state = HexGameState.standard();
        HexMove move = new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b2"));
        HexGameState next = state.play(move);
        assertTrue(next.board().pieceAt(HexCoordinate.of("b2")).isPresent());
        assertTrue(next.board().pieceAt(HexCoordinate.of("b1")).isEmpty());
    }

    @Test
    void play_whenGameNotActive_returnsSameState() {
        HexGameState resigned = HexGameState.standard().resign();
        HexGameState after = resigned.play(
                new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b2")));
        assertSame(resigned, after);
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – legalMovesFrom
    // ─────────────────────────────────────────────────────────────

    @Test
    void legalMovesFrom_emptySquare_returnsEmpty() {
        HexGameState state = HexGameState.standard();
        List<HexMove> moves = state.legalMovesFrom(HexCoordinate.of("f6"));
        assertTrue(moves.isEmpty());
    }

    @Test
    void legalMovesFrom_opponentPiece_returnsEmpty() {
        HexGameState state = HexGameState.standard(); // White to move
        List<HexMove> moves = state.legalMovesFrom(HexCoordinate.of("g10")); // Black king
        assertTrue(moves.isEmpty());
    }

    @Test
    void legalMovesFrom_whitePawnAtB1_hasMoves() {
        HexGameState state = HexGameState.standard();
        List<HexMove> moves = state.legalMovesFrom(HexCoordinate.of("b1"));
        assertFalse(moves.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – resign
    // ─────────────────────────────────────────────────────────────

    @Test
    void resign_currentPlayer_setsResignedStatus() {
        HexGameState state = HexGameState.standard();
        HexGameState resigned = state.resign();
        assertEquals(HexGameStatus.RESIGNED, resigned.status());
        assertFalse(resigned.isActive());
    }

    @Test
    void resign_white_blackGetsFullScore() {
        HexGameState state = HexGameState.standard();
        HexGameState resigned = state.resign(HexPieceColor.WHITE);
        assertEquals(0.0, resigned.whiteScore());
        assertEquals(1.0, resigned.blackScore());
    }

    @Test
    void resign_black_whiteGetsFullScore() {
        HexGameState state = HexGameState.standard();
        HexGameState resigned = state.resign(HexPieceColor.BLACK);
        assertEquals(1.0, resigned.whiteScore());
        assertEquals(0.0, resigned.blackScore());
    }

    @Test
    void resign_whenGameAlreadyOver_returnsSameState() {
        HexGameState resigned = HexGameState.standard().resign();
        assertSame(resigned, resigned.resign());
    }

    @Test
    void resign_statusMessage_containsBothPlayerNames() {
        HexGameState resigned = HexGameState.standard().resign(HexPieceColor.WHITE);
        assertTrue(resigned.statusMessage().contains("White"));
        assertTrue(resigned.statusMessage().contains("Black"));
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – draw offer / accept / decline
    // ─────────────────────────────────────────────────────────────

    @Test
    void offerDraw_byCurrentPlayer_setsPending() {
        HexGameState state = HexGameState.standard();
        HexGameState offered = state.offerDraw();
        assertEquals(HexPieceColor.WHITE, offered.drawOfferBy());
    }

    @Test
    void offerDraw_whenAlreadyPending_returnsMessageState() {
        HexGameState state = HexGameState.standard().offerDraw();
        HexGameState doubleOffer = state.offerDraw(HexPieceColor.BLACK);
        // Still has original offer, message added
        assertNotNull(doubleOffer.drawOfferBy());
    }

    @Test
    void acceptDraw_byOpponent_createsDraw() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        HexGameState accepted = offered.acceptDraw(HexPieceColor.BLACK);
        assertEquals(HexGameStatus.DRAW, accepted.status());
        assertEquals(0.5, accepted.whiteScore());
        assertEquals(0.5, accepted.blackScore());
    }

    @Test
    void acceptDraw_bySamePlayer_returnsError() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        HexGameState same = offered.acceptDraw(HexPieceColor.WHITE);
        // Game should still be active (can't accept your own offer)
        assertTrue(same.isActive());
    }

    @Test
    void acceptDraw_whenNoOffer_returnsSameState() {
        HexGameState state = HexGameState.standard();
        assertSame(state, state.acceptDraw(HexPieceColor.BLACK));
    }

    @Test
    void declineDraw_byOpponent_clearsPending() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        HexGameState declined = offered.declineDraw(HexPieceColor.BLACK);
        assertNull(declined.drawOfferBy());
        assertTrue(declined.isActive());
    }

    @Test
    void declineDraw_bySamePlayer_returnsError() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        HexGameState same = offered.declineDraw(HexPieceColor.WHITE);
        // Draw offer persists
        assertTrue(same.isActive());
    }

    @Test
    void declineDraw_whenNoOffer_returnsSameState() {
        HexGameState state = HexGameState.standard();
        assertSame(state, state.declineDraw(HexPieceColor.BLACK));
    }

    @Test
    void hasDrawOfferFor_returnsTrueIfOpponentOffered() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        assertTrue(offered.hasDrawOfferFor(HexPieceColor.BLACK));
        assertFalse(offered.hasDrawOfferFor(HexPieceColor.WHITE));
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – failed / disconnected terminal states
    // ─────────────────────────────────────────────────────────────

    @Test
    void failed_setsErrorStatus() {
        HexGameState state = HexGameState.standard().failed("Network error");
        assertEquals(HexGameStatus.ERROR, state.status());
        assertFalse(state.isActive());
    }

    @Test
    void disconnected_setsDisconnectedStatus() {
        HexGameState state = HexGameState.standard().disconnected("Player left");
        assertEquals(HexGameStatus.DISCONNECTED, state.status());
        assertFalse(state.isActive());
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – check detection
    // ─────────────────────────────────────────────────────────────

    @Test
    void isInCheck_standardStarting_isFalse() {
        HexGameState state = HexGameState.standard();
        assertFalse(state.isInCheck(HexPieceColor.WHITE));
        assertFalse(state.isInCheck(HexPieceColor.BLACK));
    }

    @Test
    void isInCheck_queenAttackingKing_isTrue() {
        // Place white king at f6, black queen next to it
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        pieces.put(HexCoordinate.of("f6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        pieces.put(HexCoordinate.of("g6"), new HexPiece(HexPieceColor.BLACK, HexPieceType.QUEEN));
        pieces.put(HexCoordinate.of("g10"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        HexBoard board = new HexBoard(pieces);
        assertTrue(HexLegalMoveValidator.isInCheck(board, HexPieceColor.WHITE));
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameEndDetector – insufficient material
    // ─────────────────────────────────────────────────────────────

    @Test
    void insufficientMaterial_kingOnly_isTrue() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        pieces.put(HexCoordinate.of("f6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        pieces.put(HexCoordinate.of("f8"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        HexBoard board = new HexBoard(pieces);
        assertTrue(HexGameEndDetector.hasInsufficientMaterial(board));
    }

    @Test
    void insufficientMaterial_kingVsKingBishop_isTrue() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        pieces.put(HexCoordinate.of("f6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        pieces.put(HexCoordinate.of("f8"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        pieces.put(HexCoordinate.of("e7"), new HexPiece(HexPieceColor.WHITE, HexPieceType.BISHOP));
        HexBoard board = new HexBoard(pieces);
        assertTrue(HexGameEndDetector.hasInsufficientMaterial(board));
    }

    @Test
    void insufficientMaterial_withPawn_isFalse() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        pieces.put(HexCoordinate.of("f6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        pieces.put(HexCoordinate.of("f8"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        pieces.put(HexCoordinate.of("e5"), new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN));
        HexBoard board = new HexBoard(pieces);
        assertFalse(HexGameEndDetector.hasInsufficientMaterial(board));
    }

    @Test
    void insufficientMaterial_withRook_isFalse() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        pieces.put(HexCoordinate.of("f6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        pieces.put(HexCoordinate.of("f8"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        pieces.put(HexCoordinate.of("d4"), new HexPiece(HexPieceColor.WHITE, HexPieceType.ROOK));
        HexBoard board = new HexBoard(pieces);
        assertFalse(HexGameEndDetector.hasInsufficientMaterial(board));
    }

    // ─────────────────────────────────────────────────────────────
    // HexMoveRules
    // ─────────────────────────────────────────────────────────────

    @Test
    void moveRules_isPawnStart_whitePawnSquares() {
        // White pawn starting squares are defined in HexStartingPosition
        HexCoordinate b1 = HexCoordinate.of("b1");
        assertTrue(HexMoveRules.isPawnStart(b1, HexPieceColor.WHITE));
    }

    @Test
    void moveRules_isPawnStart_blackPawnSquares() {
        HexCoordinate b7 = HexCoordinate.of("b7");
        assertTrue(HexMoveRules.isPawnStart(b7, HexPieceColor.BLACK));
    }

    @Test
    void moveRules_isPawnStart_centerSquare_false() {
        HexCoordinate f6 = HexCoordinate.of("f6");
        assertFalse(HexMoveRules.isPawnStart(f6, HexPieceColor.WHITE));
    }

    @Test
    void moveRules_promotionOptionsAt_promotionSquare_hasOptions() {
        HexCoordinate topF = HexCoordinate.of("f11");
        List<HexPieceType> options = HexMoveRules.promotionOptionsAt(topF, HexPieceColor.WHITE);
        assertFalse(options.isEmpty());
        assertTrue(options.contains(HexPieceType.QUEEN));
    }

    @Test
    void moveRules_promotionOptionsAt_nonPromotionSquare_isEmpty() {
        HexCoordinate mid = HexCoordinate.of("f6");
        assertTrue(HexMoveRules.promotionOptionsAt(mid, HexPieceColor.WHITE).isEmpty());
    }

    @Test
    void moveRules_sameMoveIntent_matchesFromAndTo() {
        HexCoordinate from = HexCoordinate.of("b1");
        HexCoordinate to = HexCoordinate.of("b2");
        HexMove m1 = new HexMove(from, to);
        HexMove m2 = new HexMove(from, to);
        assertTrue(HexMoveRules.sameMoveIntent(m1, m2));
    }

    @Test
    void moveRules_sameMoveIntent_differentPromotion_false() {
        HexCoordinate from = HexCoordinate.of("f10");
        HexCoordinate to = HexCoordinate.of("f11");
        HexMove m1 = new HexMove(from, to, HexPieceType.QUEEN, false);
        HexMove m2 = new HexMove(from, to, HexPieceType.ROOK, false);
        assertFalse(HexMoveRules.sameMoveIntent(m1, m2));
    }

    // ─────────────────────────────────────────────────────────────
    // HexPositionValidator – material validation
    // ─────────────────────────────────────────────────────────────

    @Test
    void positionValidator_standardBoard_isValid() {
        HexPositionValidation result = HexPositionValidator.validate(
                HexBoard.standard(), HexPieceColor.WHITE);
        assertTrue(result.isValid(), result.message());
    }

    @Test
    void positionValidator_tooManyQueens_isInvalid() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        var it = HexBoardGeometry.coordinates().iterator();
        pieces.put(it.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING));
        pieces.put(it.next(), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        for (int i = 0; i < 11; i++) {
            pieces.put(it.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.QUEEN));
        }
        HexPositionValidation result = HexPositionValidator.validateMaterial(new HexBoard(pieces));
        assertFalse(result.isValid());
        assertTrue(result.message().contains("maximum is 10"));
    }

    @Test
    void positionValidator_tooManyPieces_isInvalid() {
        Map<HexCoordinate, HexPiece> pieces = new LinkedHashMap<>();
        var it = HexBoardGeometry.coordinates().iterator();
        for (int i = 0; i < 19; i++) {
            pieces.put(it.next(), new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN));
        }
        HexPositionValidation result = HexPositionValidator.validateMaterial(new HexBoard(pieces));
        assertFalse(result.isValid());
        assertTrue(result.message().contains("maximum is 18"));
    }

    @Test
    void positionValidator_emptyBoard_isInvalid() {
        HexPositionValidation result = HexPositionValidator.validate(
                HexBoard.empty(), HexPieceColor.WHITE);
        assertFalse(result.isValid());
    }

    @Test
    void positionValidator_nullBoard_treatedAsEmpty() {
        HexPositionValidation result = HexPositionValidator.validate(null, HexPieceColor.WHITE);
        assertFalse(result.isValid());
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – half-move clock / 50-move rule
    // ─────────────────────────────────────────────────────────────

    @Test
    void halfMoveClock_incrementsOnNonPawnNonCapture() {
        HexGameState state = HexGameState.standard();
        // Make a non-pawn move that can increment the clock
        // White knight at d1 → any legal knight jump
        List<HexMove> knightMoves = state.legalMovesFrom(HexCoordinate.of("d1"));
        assertFalse(knightMoves.isEmpty(), "Knight at d1 should have moves");
        HexGameState next = state.play(knightMoves.get(0));
        assertEquals(1, next.halfMoveClock());
    }

    @Test
    void halfMoveClock_resetOnPawnMove() {
        HexGameState state = HexGameState.standard();
        HexMove pawnMove = new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b2"));
        HexGameState next = state.play(pawnMove);
        assertEquals(0, next.halfMoveClock());
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – repetition counts
    // ─────────────────────────────────────────────────────────────

    @Test
    void repetitionCounts_initiallyOneForStartingPosition() {
        HexGameState state = HexGameState.standard();
        assertFalse(state.repetitionCounts().isEmpty());
        assertTrue(state.repetitionCounts().values().stream().allMatch(c -> c >= 1));
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – legalMoves for inactive game
    // ─────────────────────────────────────────────────────────────

    @Test
    void legalMoves_whenResigned_returnsEmpty() {
        HexGameState resigned = HexGameState.standard().resign();
        assertTrue(resigned.legalMovesForTurn().isEmpty());
    }

    @Test
    void legalMoves_whenDraw_returnsEmpty() {
        HexGameState offered = HexGameState.standard().offerDraw(HexPieceColor.WHITE);
        HexGameState draw = offered.acceptDraw(HexPieceColor.BLACK);
        assertTrue(draw.legalMovesForTurn().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // HexGameState – withStatusMessage
    // ─────────────────────────────────────────────────────────────

    @Test
    void withStatusMessage_updatesMessageOnly() {
        HexGameState state = HexGameState.standard();
        HexGameState updated = state.withStatusMessage("Test message");
        assertEquals("Test message", updated.statusMessage());
        assertEquals(state.status(), updated.status());
        assertEquals(state.turn(), updated.turn());
    }

    // ─────────────────────────────────────────────────────────────
    // HexCellTone
    // ─────────────────────────────────────────────────────────────

    @Test
    void cellTone_f6_isKnown() {
        HexCellTone tone = HexBoardGeometry.tone(HexCoordinate.of("f6"));
        assertNotNull(tone);
    }

    // ─────────────────────────────────────────────────────────────
    // HexDirection
    // ─────────────────────────────────────────────────────────────

    @Test
    void rookDirections_hasSixDirections() {
        assertEquals(6, HexDirection.rookDirections().size());
    }

    @Test
    void bishopDirections_hasSixDirections() {
        assertEquals(6, HexDirection.bishopDirections().size());
    }

    @Test
    void customStandardPosition_retainsInitialPawnDoubleMoves() {
        HexGameState state = HexGameState.create(HexBoard.standard(), HexPieceColor.WHITE, true);

        List<HexCoordinate> destinations = state.legalMovesFrom(HexCoordinate.of("b1"))
                .stream()
                .map(HexMove::to)
                .toList();

        assertTrue(destinations.contains(HexCoordinate.of("b2")));
        assertTrue(destinations.contains(HexCoordinate.of("b3")));
    }

    @Test
    void movedPawn_cannotInheritDoubleMoveRightFromAnotherStartingSquare() {
        HexBoard board = HexBoard.empty()
                .withPiece(HexCoordinate.of("a1"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(HexCoordinate.of("l6"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING))
                .withPiece(HexCoordinate.of("d3"), new HexPiece(HexPieceColor.WHITE, HexPieceType.PAWN))
                .withPiece(HexCoordinate.of("e4"), new HexPiece(HexPieceColor.BLACK, HexPieceType.ROOK));
        HexGameState state = HexGameState.create(board, HexPieceColor.WHITE, true)
                .play(new HexMove(HexCoordinate.of("d3"), HexCoordinate.of("e4")))
                .play(new HexMove(HexCoordinate.of("l6"), HexCoordinate.of("i7")));

        List<HexCoordinate> destinations = state.legalMovesFrom(HexCoordinate.of("e4"))
                .stream()
                .map(HexMove::to)
                .toList();

        assertTrue(destinations.contains(HexCoordinate.of("e5")));
        assertFalse(destinations.contains(HexCoordinate.of("e6")));
    }

    @Test
    void positionKey_distinguishesPawnDoubleMoveRights() {
        HexBoard board = HexBoard.standard();
        String withRight = HexPositionKey.from(
                board,
                HexPieceColor.WHITE,
                null,
                Set.of(HexCoordinate.of("b1")));
        String withoutRight = HexPositionKey.from(board, HexPieceColor.WHITE, null, Set.of());

        assertNotEquals(withRight, withoutRight);
    }

    @Test
    void networkSnapshot_roundTripsPlayerNamesWithGameState() {
        HexGameState state = HexGameState.standard()
                .play(new HexMove(HexCoordinate.of("b1"), HexCoordinate.of("b3")));

        String serialized = HexChessStateSnapshot.serialize(state, "Alice", "Bob");
        HexChessStateSnapshot.MatchSnapshot restored = HexChessStateSnapshot.deserializeMatch(serialized);

        assertEquals(state, restored.gameState());
        assertEquals("Alice", restored.whiteName());
        assertEquals("Bob", restored.blackName());
    }

    @Test
    void create_terminalPositions_preservesResolutionScores() {
        HexBoard checkmate = HexBoard.empty()
                .withPiece(HexCoordinate.of("a1"), new HexPiece(HexPieceColor.WHITE, HexPieceType.KING))
                .withPiece(HexCoordinate.of("b3"), new HexPiece(HexPieceColor.WHITE, HexPieceType.QUEEN))
                .withPiece(HexCoordinate.of("a3"), new HexPiece(HexPieceColor.BLACK, HexPieceType.KING));
        HexGameState checkmateState = HexGameState.create(checkmate, HexPieceColor.BLACK, false);

        assertEquals(HexGameStatus.CHECKMATE, checkmateState.status());
        assertEquals(1.0, checkmateState.whiteScore());
        assertEquals(0.0, checkmateState.blackScore());

        HexBoard stalemate = checkmate
                .withoutPiece(HexCoordinate.of("b3"))
                .withPiece(HexCoordinate.of("c6"), new HexPiece(HexPieceColor.WHITE, HexPieceType.QUEEN));
        HexGameState stalemateState = HexGameState.create(stalemate, HexPieceColor.BLACK, false);

        assertEquals(HexGameStatus.STALEMATE, stalemateState.status());
        assertEquals(0.75, stalemateState.whiteScore());
        assertEquals(0.25, stalemateState.blackScore());
    }
}
