package seda_project.control_alt_defeat.gamebox.model.tetris;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TetrisModelTest {

    @Test
    void movementStopsAtLeftWall() {
        TetrisPlayerState player = TetrisPlayerState.create("Player 1", PlayerSide.BOTTOM)
                .spawnPiece(PieceShape.standardShape(PieceType.T));

        player = player.moveLeft().moveLeft().moveLeft().moveLeft();

        assertEquals(0, player.activePiece().position().column());
    }

    @Test
    void rotationIntoSettledCellIsRejected() {
        TetrisBoard board = new TetrisBoard()
                .withCell(new BoardPosition(2, 4), TetrisCell.FILLED);
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.T),
                new BoardPosition(0, 3),
                Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                "Player 1",
                PlayerSide.BOTTOM,
                board,
                piece,
                0,
                PlayerStatus.PLAYING,
                null);

        TetrisPlayerState rotated = player.rotateClockwise();

        assertEquals(Rotation.SPAWN, rotated.activePiece().rotation());
    }

    @Test
    void gravityLocksPieceWhenItCannotMoveForward() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O),
                new BoardPosition(18, 0),
                Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                "Player 1",
                PlayerSide.BOTTOM,
                new TetrisBoard(),
                piece,
                0,
                PlayerStatus.PLAYING,
                null);

        TetrisPlayerState moved = player.applyGravity();

        assertNull(moved.activePiece());
        assertEquals(TetrisCell.FILLED, moved.board().cellAt(new BoardPosition(19, 0)));
    }

    @Test
    void lockClearsCompletedLineAndScoresOnePoint() {
        TetrisBoard board = new TetrisBoard();
        for (int column = 2; column < TetrisBoard.COLUMNS; column++) {
            board = board.withCell(new BoardPosition(19, column), TetrisCell.FILLED);
        }

        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O),
                new BoardPosition(18, 0),
                Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                "Player 1",
                PlayerSide.BOTTOM,
                board,
                piece,
                0,
                PlayerStatus.PLAYING,
                null);

        TetrisPlayerState locked = player.lockActivePiece();

        assertEquals(1, locked.score());
        assertEquals(TetrisCell.FILLED, locked.board().cellAt(new BoardPosition(19, 0)));
        assertEquals(TetrisCell.FILLED, locked.board().cellAt(new BoardPosition(19, 1)));
        assertEquals(TetrisCell.EMPTY, locked.board().cellAt(new BoardPosition(18, 0)));
    }

    @Test
    void spawnCollisionMarksPlayerLostAndStoresFinalScore() {
        TetrisBoard board = new TetrisBoard()
                .withCell(new BoardPosition(0, 3), TetrisCell.FILLED);
        TetrisPlayerState player = new TetrisPlayerState(
                "Player 1",
                PlayerSide.BOTTOM,
                board,
                null,
                4,
                PlayerStatus.PLAYING,
                null);

        TetrisPlayerState lost = player.spawnPiece(PieceShape.standardShape(PieceType.I));

        assertEquals(PlayerStatus.LOST, lost.status());
        assertEquals(4, lost.finalScore());
    }

    @Test
    void gameFinishesAfterBothPlayersLose() {
        TetrisPlayerState bottom = TetrisPlayerState.create("Player 1", PlayerSide.BOTTOM).lost();
        TetrisPlayerState top = TetrisPlayerState.create("Player 2", PlayerSide.TOP).lost();
        TetrisGameState state = new TetrisGameState(
                bottom,
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState finished = state.applyGravity(PlayerSide.BOTTOM);

        assertTrue(finished.isFinished());
    }

    @Test
    void disconnectedCustomPieceIsRejected() {
        List<BoardPosition> cells = List.of(
                new BoardPosition(0, 0),
                new BoardPosition(1, 1));

        assertThrows(IllegalArgumentException.class, () -> CustomPieceBuilder.build("Bad", cells));
    }

    @Test
    void connectedCustomPieceIsNormalized() {
        PieceShape shape = CustomPieceBuilder.build("Good", List.of(
                new BoardPosition(2, 2),
                new BoardPosition(2, 3),
                new BoardPosition(3, 3)));

        assertEquals(List.of(
                new BoardPosition(0, 0),
                new BoardPosition(0, 1),
                new BoardPosition(1, 1)), shape.cells());
    }
}
