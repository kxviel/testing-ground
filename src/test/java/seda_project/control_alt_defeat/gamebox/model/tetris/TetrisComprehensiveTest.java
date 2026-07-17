package seda_project.control_alt_defeat.gamebox.model.tetris;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the Tetris game model.
 *
 * <p>
 * Covers: TetrisBoard (construction, clamping, lock, line-clear, column-clear,
 * bomb effects, resize), PieceShape (standard shapes, rotation, custom
 * validation),
 * TetrisPiece (board-cell projection), TetrisPlayerState (spawn, gravity, move,
 * rotate, item effects), TetrisGameState (lifecycle, mode changes, gravity,
 * effect ticking), TetrisGameConfig (shape resolution).
 */
class TetrisComprehensiveTest {

    // ─────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────

    private static TetrisBoard emptyBoard() {
        return new TetrisBoard();
    }

    private static TetrisBoard boardWithFilledCells(BoardPosition... positions) {
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.DEFAULT_ROWS][TetrisBoard.DEFAULT_COLUMNS];
        for (TetrisCell[] row : cells)
            Arrays.fill(row, TetrisCell.EMPTY);
        for (BoardPosition p : positions)
            cells[p.row()][p.column()] = TetrisCell.FILLED;
        return new TetrisBoard(cells);
    }

    private static TetrisBoard fullyFilledRow(int row) {
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.DEFAULT_ROWS][TetrisBoard.DEFAULT_COLUMNS];
        for (TetrisCell[] r : cells)
            Arrays.fill(r, TetrisCell.EMPTY);
        Arrays.fill(cells[row], TetrisCell.FILLED);
        return new TetrisBoard(cells);
    }

    private static TetrisPlayerState idlePlayer(PlayerSide side) {
        return new TetrisPlayerState(
                side == PlayerSide.BOTTOM ? "Bottom" : "Top",
                side,
                emptyBoard(),
                null,
                0,
                PlayerStatus.PLAYING,
                null);
    }

    private static TetrisPlayerState playerWithObject(
            PlayerSide side, TetrisBoard board, PieceType pieceType, int score, TetrisItemType objectType) {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(pieceType), new BoardPosition(4, 4), Rotation.SPAWN);
        TetrisBoardObject object = objectType == null
                ? null
                : new TetrisBoardObject(objectType, new BoardPosition(4, 4));
        return new TetrisPlayerState(
                side == PlayerSide.BOTTOM ? "Bottom" : "Top",
                side,
                board,
                piece,
                score,
                PlayerStatus.PLAYING,
                null,
                object,
                null,
                List.of());
    }

    private static TetrisGameState runningState(TetrisPlayerState bottom, TetrisPlayerState top) {
        return new TetrisGameState(bottom, top, TetrisGameConfig.defaultConfig(), TetrisGameStatus.RUNNING);
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – construction and dimension clamping
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_default_hasTwentyRowsAndTenColumns() {
        TetrisBoard board = new TetrisBoard();
        assertEquals(20, board.rows());
        assertEquals(10, board.columns());
    }

    @Test
    void board_customDimensions_areClampedToValidRange() {
        TetrisBoard tooSmall = new TetrisBoard(1, 1);
        assertEquals(TetrisBoard.MIN_ROWS, tooSmall.rows());
        assertEquals(TetrisBoard.MIN_COLUMNS, tooSmall.columns());

        TetrisBoard tooLarge = new TetrisBoard(100, 100);
        assertEquals(TetrisBoard.MAX_ROWS, tooLarge.rows());
        assertEquals(TetrisBoard.MAX_COLUMNS, tooLarge.columns());
    }

    @Test
    void board_horizontalMode_swapsDimensions() {
        TetrisBoard horizontal = TetrisBoard.createDefault(true);
        assertEquals(TetrisBoard.HORIZONTAL_ROWS, horizontal.rows());
        assertEquals(TetrisBoard.HORIZONTAL_COLUMNS, horizontal.columns());
    }

    @Test
    void board_normalMode_usesDefaultDimensions() {
        TetrisBoard normal = TetrisBoard.createDefault(false);
        assertEquals(TetrisBoard.DEFAULT_ROWS, normal.rows());
        assertEquals(TetrisBoard.DEFAULT_COLUMNS, normal.columns());
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – isEmpty / isInside / cellAt / colorAt
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_isEmpty_trueForEmptyCell() {
        TetrisBoard board = emptyBoard();
        assertTrue(board.isEmpty(new BoardPosition(0, 0)));
    }

    @Test
    void board_isEmpty_falseForFilledCell() {
        TetrisBoard board = boardWithFilledCells(new BoardPosition(5, 5));
        assertFalse(board.isEmpty(new BoardPosition(5, 5)));
    }

    @Test
    void board_isEmpty_falseForOutOfBoundsPosition() {
        TetrisBoard board = emptyBoard();
        assertFalse(board.isEmpty(new BoardPosition(-1, 0)));
        assertFalse(board.isEmpty(new BoardPosition(0, 100)));
    }

    @Test
    void board_isInside_trueForValidPosition() {
        TetrisBoard board = emptyBoard();
        assertTrue(board.isInside(new BoardPosition(0, 0)));
        assertTrue(board.isInside(new BoardPosition(19, 9)));
    }

    @Test
    void board_isInside_falseForOutOfBoundsPosition() {
        TetrisBoard board = emptyBoard();
        assertFalse(board.isInside(new BoardPosition(-1, 0)));
        assertFalse(board.isInside(new BoardPosition(20, 0)));
        assertFalse(board.isInside(null));
    }

    @Test
    void board_cellAt_throwsForOutOfBoundsPosition() {
        TetrisBoard board = emptyBoard();
        assertThrows(IllegalArgumentException.class,
                () -> board.cellAt(new BoardPosition(-1, 0)));
    }

    @Test
    void board_colorAt_throwsForOutOfBoundsPosition() {
        TetrisBoard board = emptyBoard();
        assertThrows(IllegalArgumentException.class,
                () -> board.colorAt(new BoardPosition(99, 99)));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – canPlace / lockPiece
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_canPlace_trueForEmptySpot() {
        TetrisBoard board = emptyBoard();
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN);
        assertTrue(board.canPlace(piece));
    }

    @Test
    void board_canPlace_falseForOverlap() {
        TetrisBoard board = boardWithFilledCells(new BoardPosition(0, 0));
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN);
        assertFalse(board.canPlace(piece));
    }

    @Test
    void board_canPlace_falseForNull() {
        assertFalse(emptyBoard().canPlace(null));
    }

    @Test
    void board_lockPiece_fillsCells() {
        TetrisBoard board = emptyBoard();
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN);
        TetrisBoard locked = board.lockPiece(piece);
        assertEquals(TetrisCell.FILLED, locked.cellAt(new BoardPosition(0, 0)));
        assertEquals(TetrisCell.FILLED, locked.cellAt(new BoardPosition(0, 1)));
        assertEquals(TetrisCell.FILLED, locked.cellAt(new BoardPosition(1, 0)));
        assertEquals(TetrisCell.FILLED, locked.cellAt(new BoardPosition(1, 1)));
    }

    @Test
    void board_lockPiece_returnsSelfIfCannotPlace() {
        TetrisBoard board = boardWithFilledCells(new BoardPosition(0, 0));
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN);
        assertSame(board, board.lockPiece(piece));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – fullRows / clearRows
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_fullRows_detectsCompleteRow() {
        TetrisBoard board = fullyFilledRow(19);
        assertEquals(List.of(19), board.fullRows());
    }

    @Test
    void board_fullRows_emptyBoardHasNone() {
        assertTrue(emptyBoard().fullRows().isEmpty());
    }

    @Test
    void board_clearRows_removesCompleteRow() {
        TetrisBoard board = fullyFilledRow(19);
        TetrisBoard cleared = board.clearRows(List.of(19));
        assertEquals(TetrisCell.EMPTY, cleared.cellAt(new BoardPosition(19, 0)));
        assertTrue(cleared.fullRows().isEmpty());
    }

    @Test
    void board_clearRows_emptyListReturnsSelf() {
        TetrisBoard board = emptyBoard();
        assertSame(board, board.clearRows(List.of()));
    }

    @Test
    void board_clearRows_nullListReturnsSelf() {
        TetrisBoard board = emptyBoard();
        assertSame(board, board.clearRows(null));
    }

    @Test
    void board_clearRows_shiftsCellsDown() {
        // Fill row 18 with a pattern, then clear row 19 (full)
        TetrisCell[][] cells = new TetrisCell[20][10];
        for (TetrisCell[] r : cells)
            Arrays.fill(r, TetrisCell.EMPTY);
        Arrays.fill(cells[19], TetrisCell.FILLED); // full row to clear
        cells[18][0] = TetrisCell.FILLED; // marker cell
        TetrisBoard board = new TetrisBoard(cells);

        TetrisBoard cleared = board.clearRows(List.of(19));
        // After clearing row 19, row 18's content shifts to row 19
        assertEquals(TetrisCell.FILLED, cleared.cellAt(new BoardPosition(19, 0)));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – fullColumns / clearColumns
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_fullColumns_detectsCompleteColumn() {
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.DEFAULT_ROWS][TetrisBoard.DEFAULT_COLUMNS];
        for (TetrisCell[] row : cells)
            Arrays.fill(row, TetrisCell.EMPTY);
        for (int r = 0; r < TetrisBoard.DEFAULT_ROWS; r++)
            cells[r][0] = TetrisCell.FILLED;
        TetrisBoard board = new TetrisBoard(cells);
        assertEquals(List.of(0), board.fullColumns());
    }

    @Test
    void board_clearColumns_removesCompleteColumn() {
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.DEFAULT_ROWS][TetrisBoard.DEFAULT_COLUMNS];
        for (TetrisCell[] row : cells)
            Arrays.fill(row, TetrisCell.EMPTY);
        for (int r = 0; r < TetrisBoard.DEFAULT_ROWS; r++)
            cells[r][0] = TetrisCell.FILLED;
        TetrisBoard board = new TetrisBoard(cells);
        TetrisBoard cleared = board.clearColumns(List.of(0));
        for (int r = 0; r < cleared.rows(); r++) {
            assertEquals(TetrisCell.EMPTY, cleared.cellAt(new BoardPosition(r, cleared.columns() - 1)));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – destroyRadius
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_destroyRadius_clearsNearbyFilledCells() {
        BoardPosition center = new BoardPosition(10, 5);
        TetrisBoard board = boardWithFilledCells(
                new BoardPosition(10, 5), // at center
                new BoardPosition(10, 6), // 1 step away
                new BoardPosition(15, 9) // far away
        );
        TetrisBoard exploded = board.destroyRadius(center, 2);
        assertEquals(TetrisCell.EMPTY, exploded.cellAt(new BoardPosition(10, 5)));
        assertEquals(TetrisCell.EMPTY, exploded.cellAt(new BoardPosition(10, 6)));
        assertEquals(TetrisCell.FILLED, exploded.cellAt(new BoardPosition(15, 9)));
    }

    @Test
    void board_destroyRadius_nullCenter_returnsSelf() {
        TetrisBoard board = emptyBoard();
        assertSame(board, board.destroyRadius(null, 3));
    }

    @Test
    void board_destroyRadius_negativeRadius_returnsSelf() {
        TetrisBoard board = emptyBoard();
        assertSame(board, board.destroyRadius(new BoardPosition(5, 5), -1));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – destroyAlongGravity
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_destroyAlongGravity_down_clearsColumnBelow() {
        TetrisBoard board = boardWithFilledCells(
                new BoardPosition(10, 5),
                new BoardPosition(15, 5),
                new BoardPosition(10, 3));
        TetrisBoard destroyed = board.destroyAlongGravity(new BoardPosition(10, 5), GravityDirection.DOWN);
        assertEquals(TetrisCell.EMPTY, destroyed.cellAt(new BoardPosition(10, 5)));
        assertEquals(TetrisCell.EMPTY, destroyed.cellAt(new BoardPosition(15, 5)));
        assertEquals(TetrisCell.FILLED, destroyed.cellAt(new BoardPosition(10, 3)));
    }

    @Test
    void board_destroyAlongGravity_nullImpact_returnsSelf() {
        TetrisBoard board = emptyBoard();
        assertSame(board, board.destroyAlongGravity(null, GravityDirection.DOWN));
    }

    @Test
    void board_destroyAlongGravity_up_clearsColumnAbove() {
        TetrisBoard board = boardWithFilledCells(new BoardPosition(5, 5), new BoardPosition(0, 5));
        TetrisBoard destroyed = board.destroyAlongGravity(new BoardPosition(5, 5), GravityDirection.UP);
        assertEquals(TetrisCell.EMPTY, destroyed.cellAt(new BoardPosition(5, 5)));
        assertEquals(TetrisCell.EMPTY, destroyed.cellAt(new BoardPosition(0, 5)));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisBoard – resize operations
    // ─────────────────────────────────────────────────────────────

    @Test
    void board_addRowsAtTop_increasesRowCount() {
        TetrisBoard board = emptyBoard(); // 20 rows
        TetrisBoard grown = board.addRowsAtTop(2);
        assertEquals(22, grown.rows());
    }

    @Test
    void board_addRowsAtTop_cannotExceedMax() {
        TetrisBoard board = new TetrisBoard(TetrisBoard.MAX_ROWS, 10);
        TetrisBoard same = board.addRowsAtTop(5);
        assertEquals(TetrisBoard.MAX_ROWS, same.rows());
    }

    @Test
    void board_addRowsAtBottom_increasesRowCount() {
        TetrisBoard board = emptyBoard();
        TetrisBoard grown = board.addRowsAtBottom(3);
        assertEquals(23, grown.rows());
    }

    @Test
    void board_removeRowsFromTop_decreasesRowCount() {
        TetrisBoard board = emptyBoard(); // 20 rows
        TetrisBoard shrunk = board.removeRowsFromTop(2);
        assertEquals(18, shrunk.rows());
    }

    @Test
    void board_removeRowsFromTop_cannotGoBelowMin() {
        TetrisBoard board = new TetrisBoard(TetrisBoard.MIN_ROWS, 10);
        assertSame(board, board.removeRowsFromTop(5));
    }

    @Test
    void board_addColumnsAtLeft_increasesColumnCount() {
        TetrisBoard board = emptyBoard(); // 10 cols
        TetrisBoard grown = board.addColumnsAtLeft(2);
        assertEquals(12, grown.columns());
    }

    @Test
    void board_removeColumnsFromRight_decreasesColumnCount() {
        TetrisBoard board = emptyBoard();
        TetrisBoard shrunk = board.removeColumnsFromRight(2);
        assertEquals(8, shrunk.columns());
    }

    // ─────────────────────────────────────────────────────────────
    // PieceShape – standard shapes
    // ─────────────────────────────────────────────────────────────

    @Test
    void pieceShape_standardShapes_hasSevenPieces() {
        assertEquals(7, PieceShape.standardShapes().size());
    }

    @Test
    void pieceShape_standardShape_I_isFourCellsWide() {
        PieceShape i = PieceShape.standardShape(PieceType.I);
        assertEquals(4, i.width());
        assertEquals(1, i.height());
    }

    @Test
    void pieceShape_standardShape_O_is2by2() {
        PieceShape o = PieceShape.standardShape(PieceType.O);
        assertEquals(2, o.width());
        assertEquals(2, o.height());
    }

    @Test
    void pieceShape_standardShape_T_has4cells() {
        PieceShape t = PieceShape.standardShape(PieceType.T);
        assertEquals(4, t.cells().size());
    }

    @Test
    void pieceShape_rotateClockwise90_changesOrientation() {
        PieceShape i = PieceShape.standardShape(PieceType.I);
        PieceShape rotated = i.rotateClockwise90();
        // I-piece horizontal → vertical after rotation
        assertEquals(1, rotated.width());
        assertEquals(4, rotated.height());
    }

    @Test
    void pieceShape_custom_invalidNoCells_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PieceShape(PieceType.I, "Test", List.of()));
    }

    @Test
    void pieceShape_custom_negativeCellRow_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PieceShape(PieceType.I, "Test",
                        List.of(new BoardPosition(-1, 0))));
    }

    @Test
    void pieceShape_custom_tooManyCells_throws() {
        List<BoardPosition> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= PieceShape.MAX_CELLS; i++) {
            tooMany.add(new BoardPosition(i % 8, i / 8));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PieceShape(PieceType.I, "Huge", tooMany));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPiece – boardCells projection
    // ─────────────────────────────────────────────────────────────

    @Test
    void piece_boardCells_spawn_projectedCorrectly() {
        PieceShape o = PieceShape.standardShape(PieceType.O);
        TetrisPiece piece = new TetrisPiece(o, new BoardPosition(5, 3), Rotation.SPAWN);
        List<BoardPosition> cells = piece.boardCells();
        assertTrue(cells.contains(new BoardPosition(5, 3)));
        assertTrue(cells.contains(new BoardPosition(5, 4)));
        assertTrue(cells.contains(new BoardPosition(6, 3)));
        assertTrue(cells.contains(new BoardPosition(6, 4)));
    }

    @Test
    void piece_boardCells_rotated90_projectedCorrectly() {
        PieceShape i = PieceShape.standardShape(PieceType.I); // 1×4 horizontal
        TetrisPiece piece = new TetrisPiece(i, new BoardPosition(0, 0), Rotation.RIGHT);
        List<BoardPosition> cells = piece.boardCells();
        assertEquals(4, cells.size());
    }

    @Test
    void piece_withPosition_updatesPositionOnly() {
        PieceShape o = PieceShape.standardShape(PieceType.O);
        TetrisPiece orig = new TetrisPiece(o, new BoardPosition(0, 0), Rotation.SPAWN);
        TetrisPiece moved = orig.withPosition(new BoardPosition(5, 5));
        assertEquals(new BoardPosition(5, 5), moved.position());
        assertEquals(orig.rotation(), moved.rotation());
        assertEquals(orig.shape(), moved.shape());
    }

    @Test
    void piece_withRotation_updatesRotationOnly() {
        PieceShape o = PieceShape.standardShape(PieceType.O);
        TetrisPiece orig = new TetrisPiece(o, new BoardPosition(0, 0), Rotation.SPAWN);
        TetrisPiece rotated = orig.withRotation(Rotation.RIGHT);
        assertEquals(Rotation.RIGHT, rotated.rotation());
        assertEquals(orig.position(), rotated.position());
    }

    @Test
    void piece_nullShape_throws() {
        assertThrows(NullPointerException.class,
                () -> new TetrisPiece(null, new BoardPosition(0, 0), Rotation.SPAWN));
    }

    @Test
    void piece_nullPosition_throws() {
        assertThrows(NullPointerException.class,
                () -> new TetrisPiece(PieceShape.standardShape(PieceType.O), null, Rotation.SPAWN));
    }

    @Test
    void piece_nullRotation_defaultsToSpawn() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), null);
        assertEquals(Rotation.SPAWN, piece.rotation());
    }

    // ─────────────────────────────────────────────────────────────
    // CustomPieceBuilder
    // ─────────────────────────────────────────────────────────────

    @Test
    void customPieceBuilder_validCells_buildsShape() {
        PieceShape shape = CustomPieceBuilder.build("L-custom", List.of(
                new BoardPosition(0, 0),
                new BoardPosition(1, 0),
                new BoardPosition(2, 0),
                new BoardPosition(2, 1)));
        assertEquals(4, shape.cells().size());
        assertEquals(PieceType.CUSTOM, shape.type()); // CustomPieceBuilder always produces CUSTOM type
    }

    @Test
    void customPieceBuilder_duplicateCells_deduplicates() {
        PieceShape shape = CustomPieceBuilder.build("dup", List.of(
                new BoardPosition(0, 0),
                new BoardPosition(0, 0)));
        assertEquals(1, shape.cells().size());
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPlayerState – creation and defaults
    // ─────────────────────────────────────────────────────────────

    @Test
    void playerState_create_isPlayingWithNoActivePiece() {
        TetrisPlayerState player = TetrisPlayerState.create(SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM);
        assertTrue(player.isPlaying());
        assertNull(player.activePiece());
        assertEquals(0, player.score());
    }

    @Test
    void playerState_negativeSorce_clampedToZero() {
        TetrisPlayerState player = new TetrisPlayerState(
                "P", PlayerSide.BOTTOM, emptyBoard(), null, -10, PlayerStatus.PLAYING, null);
        assertEquals(0, player.score());
    }

    @Test
    void playerState_nullSide_throws() {
        assertThrows(NullPointerException.class,
                () -> new TetrisPlayerState("P", null, emptyBoard(), null, 0, PlayerStatus.PLAYING, null));
    }

    @Test
    void playerState_nullBoard_defaultsToNewBoard() {
        TetrisPlayerState player = new TetrisPlayerState(
                "P", PlayerSide.BOTTOM, null, null, 0, PlayerStatus.PLAYING, null);
        assertNotNull(player.board());
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPlayerState – gravity / lock
    // ─────────────────────────────────────────────────────────────

    @Test
    void gravity_locksOPieceAtBottomRow() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O), new BoardPosition(18, 0), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, emptyBoard(), piece, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState moved = player.applyGravity();

        assertNull(moved.activePiece());
        assertEquals(TetrisCell.FILLED, moved.board().cellAt(new BoardPosition(19, 0)));
        assertEquals(TetrisCell.FILLED, moved.board().cellAt(new BoardPosition(19, 1)));
    }

    @Test
    void gravity_fallsOneTileWhenClear() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I), new BoardPosition(0, 0), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, emptyBoard(), piece, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState moved = player.applyGravity();

        assertNotNull(moved.activePiece());
        assertEquals(1, moved.activePiece().position().row());
    }

    @Test
    void gravity_noActivePiece_returnsSelf() {
        TetrisPlayerState player = idlePlayer(PlayerSide.BOTTOM);
        assertSame(player, player.applyGravity());
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPlayerState – line clearing after lock
    // ─────────────────────────────────────────────────────────────

    @Test
    void gravity_lockClearsFullRow_incrementsScore() {
        // Fill columns 0-5 of row 19, leave 6-9 empty for the I-piece to complete the
        // row
        TetrisCell[][] cells = new TetrisCell[20][10];
        for (TetrisCell[] r : cells)
            Arrays.fill(r, TetrisCell.EMPTY);
        for (int col = 0; col <= 5; col++)
            cells[19][col] = TetrisCell.FILLED;
        TetrisBoard board = new TetrisBoard(cells);

        // I-piece at row 18, columns 6-9
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I), new BoardPosition(18, 6), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, board, piece, 0,
                PlayerStatus.PLAYING, null);

        // First gravity: moves piece from row 18 → 19
        TetrisPlayerState step1 = player.applyGravity();
        assertNotNull(step1.activePiece(), "Piece should still be active after first gravity step");

        // Second gravity: piece at row 19 cannot fall further → locks & clears full row
        TetrisPlayerState after = step1.applyGravity();

        assertNull(after.activePiece(), "Piece should be locked after second gravity step");
        assertTrue(after.score() > 0, "Score should increase after clearing a full row");
        assertTrue(after.board().fullRows().isEmpty(), "No full rows should remain after clearing");
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPlayerState – move left / right
    // ─────────────────────────────────────────────────────────────

    @Test
    void moveLeft_shiftsActivePiece() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I), new BoardPosition(0, 5), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, emptyBoard(), piece, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState moved = player.moveLeft();

        assertNotNull(moved.activePiece());
        assertEquals(4, moved.activePiece().position().column());
    }

    @Test
    void moveRight_shiftsActivePiece() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I), new BoardPosition(0, 0), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, emptyBoard(), piece, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState moved = player.moveRight();

        assertNotNull(moved.activePiece());
        assertEquals(1, moved.activePiece().position().column());
    }

    @Test
    void moveLeft_blockedByWall_returnsSelf() {
        // I-piece at column 0 cannot move further left
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I), new BoardPosition(0, 0), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, emptyBoard(), piece, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState moved = player.moveLeft();

        assertSame(player, moved); // Cannot move left, wall blocked
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPlayerState – rotate
    // ─────────────────────────────────────────────────────────────

    @Test
    void rotateClockwise_changesRotationState() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I), new BoardPosition(5, 3), Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, emptyBoard(), piece, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState rotated = player.rotateClockwise();

        assertNotNull(rotated.activePiece());
        assertNotEquals(Rotation.SPAWN, rotated.activePiece().rotation());
    }

    @Test
    void rotateClockwise_noActivePiece_returnsSelf() {
        TetrisPlayerState player = idlePlayer(PlayerSide.BOTTOM);
        assertSame(player, player.rotateClockwise());
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisPlayerState – loss detection
    // ─────────────────────────────────────────────────────────────

    @Test
    void spawnPiece_onFullBoard_causesLoss() {
        // Fill the top rows so no piece can spawn
        TetrisCell[][] cells = new TetrisCell[20][10];
        for (TetrisCell[] row : cells)
            Arrays.fill(row, TetrisCell.FILLED);
        TetrisBoard fullBoard = new TetrisBoard(cells);

        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME, PlayerSide.BOTTOM, fullBoard, null, 0,
                PlayerStatus.PLAYING, null);

        TetrisPlayerState after = player.spawnPiece(PieceShape.standardShape(PieceType.O));
        assertEquals(PlayerStatus.LOST, after.status());
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisGameState – lifecycle
    // ─────────────────────────────────────────────────────────────

    @Test
    void gameState_create_defaultSetup_isReady() {
        TetrisGameState state = TetrisGameState.create(null);
        assertEquals(TetrisGameStatus.READY, state.status());
    }

    @Test
    void gameState_running_setsRunningStatus() {
        TetrisGameState state = TetrisGameState.create(null).running();
        assertEquals(TetrisGameStatus.RUNNING, state.status());
    }

    @Test
    void gameState_finished_setsFinishedStatus() {
        TetrisGameState state = TetrisGameState.create(null).finished();
        assertEquals(TetrisGameStatus.FINISHED, state.status());
        assertTrue(state.isFinished());
    }

    @Test
    void gameState_applyGravity_whenNotRunning_returnsSelf() {
        TetrisGameState state = TetrisGameState.create(null); // READY
        assertSame(state, state.applyGravity());
    }

    @Test
    void gameState_tickEffects_whenNotRunning_returnsSelf() {
        TetrisGameState state = TetrisGameState.create(null); // READY
        assertSame(state, state.tickEffects());
    }

    @Test
    void gameState_applyGravity_whenRunning_updatesPlayers() {
        TetrisGameState state = TetrisGameState.create(null).running();
        // Just confirm it doesn't throw and returns a new state
        TetrisGameState next = state.applyGravity();
        assertNotNull(next);
    }

    @Test
    void gameState_moveLeft_whenFinished_returnsSelf() {
        TetrisGameState finished = TetrisGameState.create(null).finished();
        assertSame(finished, finished.moveLeft(PlayerSide.BOTTOM));
    }

    @Test
    void gameState_moveRight_whenFinished_returnsSelf() {
        TetrisGameState finished = TetrisGameState.create(null).finished();
        assertSame(finished, finished.moveRight(PlayerSide.BOTTOM));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisGameState – item effects (from existing test patterns)
    // ─────────────────────────────────────────────────────────────

    @Test
    void playerSwap_swapsBoardsAndFallingPiecesButNotScores() {
        TetrisBoard bottomBoard = boardWithFilledCells(new BoardPosition(19, 0));
        TetrisBoard topBoard = boardWithFilledCells(new BoardPosition(19, 9));
        TetrisPlayerState bottom = playerWithObject(
                PlayerSide.BOTTOM, bottomBoard, PieceType.O, 7, TetrisItemType.BOARD_SWAP);
        TetrisPlayerState top = playerWithObject(
                PlayerSide.TOP, topBoard, PieceType.T, 3, null);

        TetrisGameState swapped = runningState(bottom, top).rotateClockwise(PlayerSide.BOTTOM);

        assertEquals(7, swapped.bottomPlayer().score());
        assertEquals(3, swapped.topPlayer().score());
        assertEquals(TetrisCell.FILLED, swapped.bottomPlayer().board().cellAt(new BoardPosition(19, 9)));
        assertEquals(TetrisCell.FILLED, swapped.topPlayer().board().cellAt(new BoardPosition(19, 0)));
        assertEquals(PieceType.T, swapped.bottomPlayer().activePiece().shape().type());
        assertEquals(PieceType.O, swapped.topPlayer().activePiece().shape().type());
    }

    @Test
    void teleport_sendsActivePieceToOpponent() {
        TetrisPlayerState bottom = playerWithObject(
                PlayerSide.BOTTOM, emptyBoard(), PieceType.I, 4, TetrisItemType.TELEPORT);
        TetrisPlayerState top = playerWithObject(
                PlayerSide.TOP, emptyBoard(), PieceType.O, 2, null);

        TetrisGameState teleported = runningState(bottom, top).rotateClockwise(PlayerSide.BOTTOM);

        assertNull(teleported.bottomPlayer().activePiece());
        assertTrue(teleported.topPlayer().hasQueuedShape());
        assertEquals(PieceType.I, teleported.topPlayer().queuedShapes().getFirst().type());
    }

    @Test
    void radiusBomb_clearsNearbyBlocks() {
        TetrisBoard board = boardWithFilledCells(
                new BoardPosition(7, 4),
                new BoardPosition(10, 4));
        TetrisPlayerState player = playerWithObject(
                PlayerSide.BOTTOM, board, PieceType.O, 0, TetrisItemType.RADIUS_BOMB);
        TetrisGameState result = runningState(
                player,
                playerWithObject(PlayerSide.TOP, emptyBoard(), PieceType.O, 0, null))
                .rotateClockwise(PlayerSide.BOTTOM);

        assertEquals(TetrisCell.EMPTY, result.bottomPlayer().board().cellAt(new BoardPosition(7, 4)));
        assertEquals(TetrisCell.FILLED, result.bottomPlayer().board().cellAt(new BoardPosition(10, 4)));
    }

    @Test
    void columnBomb_clearsOnlyTargetColumn() {
        TetrisBoard board = boardWithFilledCells(
                new BoardPosition(7, 4),
                new BoardPosition(7, 5));
        TetrisPlayerState player = playerWithObject(
                PlayerSide.BOTTOM, board, PieceType.O, 0, TetrisItemType.COLUMN_BOMB);
        TetrisGameState result = runningState(
                player,
                playerWithObject(PlayerSide.TOP, emptyBoard(), PieceType.O, 0, null))
                .rotateClockwise(PlayerSide.BOTTOM);

        assertEquals(TetrisCell.EMPTY, result.bottomPlayer().board().cellAt(new BoardPosition(7, 4)));
        assertEquals(TetrisCell.FILLED, result.bottomPlayer().board().cellAt(new BoardPosition(7, 5)));
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisGameConfig
    // ─────────────────────────────────────────────────────────────

    @Test
    void gameConfig_default_hasStandardShapes() {
        TetrisGameConfig config = TetrisGameConfig.defaultConfig();
        assertFalse(config.availableShapes().isEmpty());
        assertTrue(config.hasPieces());
    }

    @Test
    void gameConfig_standardPieces_containsSevenShapes() {
        TetrisGameConfig config = TetrisGameConfig.defaultConfig();
        assertEquals(7, config.availableShapes().size());
    }

    @Test
    void gameConfig_customOnly_returnsCustomShapes() {
        PieceShape custom = CustomPieceBuilder.build("Tri", List.of(
                new BoardPosition(0, 0), new BoardPosition(0, 1), new BoardPosition(1, 0)));
        TetrisGameConfig config = new TetrisGameConfig(List.of("Custom"), List.of(custom));
        assertEquals(List.of(custom), config.availableShapes());
    }

    @Test
    void gameConfig_gravityMillis_isClamped() {
        TetrisGameConfig tooFast = new TetrisGameConfig(List.of("Standard"), List.of(), 0, false, false);
        assertTrue(tooFast.gravityMillis() >= 180, "Gravity millis should be at least 180");

        TetrisGameConfig tooSlow = new TetrisGameConfig(List.of("Standard"), List.of(), 99999, false, false);
        assertTrue(tooSlow.gravityMillis() <= 1100, "Gravity millis should be at most 1100");
    }

    @Test
    void gameConfig_horizontalMode_rotatesShapes() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(),
                TetrisGameConfig.DEFAULT_GRAVITY_MILLIS, false, true);
        List<?> shapes = config.availableShapes();
        assertFalse(shapes.isEmpty()); // shapes still available in horizontal mode
    }

    @Test
    void gameConfig_emptyCustomPieces_fallsBackToStandard() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Custom"), List.of());
        // When no custom pieces exist and custom is selected, falls back to standard
        assertFalse(config.availableShapes().isEmpty());
    }

    @Test
    void gameConfig_tooManyCustomPieces_areTrimmed() {
        List<PieceShape> shapes = new java.util.ArrayList<>();
        for (int i = 0; i <= TetrisGameConfig.MAX_CUSTOM_PIECES + 5; i++) {
            shapes.add(CustomPieceBuilder.build("P" + i, List.of(new BoardPosition(0, 0))));
        }
        TetrisGameConfig config = new TetrisGameConfig(List.of("Custom"), shapes);
        assertTrue(config.customPieces().size() <= TetrisGameConfig.MAX_CUSTOM_PIECES);
    }

    // ─────────────────────────────────────────────────────────────
    // TetrisItemType
    // ─────────────────────────────────────────────────────────────

    @Test
    void itemType_requiresActiveOpponent_forOpponentTargeting() {
        assertTrue(TetrisItemType.SPEED_UP_OPPONENT.requiresActiveOpponent());
        assertTrue(TetrisItemType.BOARD_SWAP.requiresActiveOpponent());
        assertTrue(TetrisItemType.TELEPORT.requiresActiveOpponent());
    }

    @Test
    void itemType_doesNotRequireActiveOpponent_forSelfEffects() {
        assertFalse(TetrisItemType.RADIUS_BOMB.requiresActiveOpponent());
        assertFalse(TetrisItemType.COLUMN_BOMB.requiresActiveOpponent());
        assertFalse(TetrisItemType.SLOW_SELF.requiresActiveOpponent());
    }

    @Test
    void itemType_eligibleForOpponentState_excludesOpponentEffectsWhenNotPlaying() {
        var eligible = TetrisItemType.eligibleForOpponentState(false);
        assertFalse(eligible.contains(TetrisItemType.SPEED_UP_OPPONENT));
        assertTrue(eligible.contains(TetrisItemType.RADIUS_BOMB));
    }

    @Test
    void itemType_eligibleForOpponentState_includesAllWhenPlaying() {
        var eligible = TetrisItemType.eligibleForOpponentState(true);
        assertTrue(eligible.contains(TetrisItemType.SPEED_UP_OPPONENT));
        assertTrue(eligible.contains(TetrisItemType.BOARD_SWAP));
    }

    // ─────────────────────────────────────────────────────────────
    // GravityDirection
    // ─────────────────────────────────────────────────────────────

    @Test
    void gravityDirection_down_isVertical() {
        assertFalse(GravityDirection.DOWN.isHorizontal());
        assertEquals(1, GravityDirection.DOWN.rowStep());
        assertEquals(0, GravityDirection.DOWN.columnStep());
    }

    @Test
    void gravityDirection_right_isHorizontal() {
        assertTrue(GravityDirection.RIGHT.isHorizontal());
        assertEquals(0, GravityDirection.RIGHT.rowStep());
        assertEquals(1, GravityDirection.RIGHT.columnStep());
    }

    @Test
    void gravityDirection_up_isVertical() {
        assertFalse(GravityDirection.UP.isHorizontal());
        assertEquals(-1, GravityDirection.UP.rowStep());
    }
}
