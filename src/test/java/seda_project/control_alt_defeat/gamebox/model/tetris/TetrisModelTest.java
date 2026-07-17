package seda_project.control_alt_defeat.gamebox.model.tetris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

class TetrisModelTest {

    @Test
    void gravityLocksPieceAtBottom() {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O),
                new BoardPosition(18, 0),
                Rotation.SPAWN);
        TetrisPlayerState player = new TetrisPlayerState(
                SafeText.PLAYER_ONE_NAME,
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
    void customPiecesAreIncludedInThePlayableShapeSet() {
        PieceShape custom = CustomPieceBuilder.build("Stair", List.of(
                new BoardPosition(0, 0),
                new BoardPosition(1, 0),
                new BoardPosition(1, 1)));
        TetrisGameConfig config = new TetrisGameConfig(List.of("Custom"), List.of(custom));

        assertEquals(List.of(custom), config.availableShapes());
    }

    @Test
    void customPieceCellsMustBeConnected() {
        assertThrows(IllegalArgumentException.class, () -> CustomPieceBuilder.build("Disconnected", List.of(
                new BoardPosition(0, 0),
                new BoardPosition(2, 2))));
    }

    @Test
    void playerSwapKeepsScoresAndIdentitiesButSwapsBoardsAndFallingPieces() {
        TetrisBoard bottomBoard = boardWithFilledCell(19, 0);
        TetrisBoard topBoard = boardWithFilledCell(19, 9);
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
    void timedSpeedAndRotationObjectsTargetTheRequiredPlayer() {
        TetrisGameState spedUpOpponent = triggerBottomObject(TetrisItemType.SPEED_UP_OPPONENT);
        assertEquals(50, spedUpOpponent.topPlayer().effects().gravityPercent());
        assertEquals(100, spedUpOpponent.topPlayer().effects().gravityTicks());
        assertEquals(275, spedUpOpponent.topPlayer().effects().gravityMillis(550));

        TetrisGameState slowedSelf = triggerBottomObject(TetrisItemType.SLOW_SELF);
        assertEquals(200, slowedSelf.bottomPlayer().effects().gravityPercent());
        assertEquals(100, slowedSelf.bottomPlayer().effects().gravityTicks());
        assertEquals(1_100, slowedSelf.bottomPlayer().effects().gravityMillis(550));

        TetrisGameState slowedOpponent = triggerBottomObject(TetrisItemType.SLOW_OPPONENT);
        assertEquals(200, slowedOpponent.topPlayer().effects().gravityPercent());
        assertEquals(100, slowedOpponent.topPlayer().effects().gravityTicks());
        assertEquals(1_100, slowedOpponent.topPlayer().effects().gravityMillis(550));

        TetrisGameState delayedOpponent = triggerBottomObject(TetrisItemType.ROTATION_DELAY_OPPONENT);
        assertEquals(100, delayedOpponent.topPlayer().effects().rotationEffectTicks());
        Rotation opponentRotation = delayedOpponent.topPlayer().activePiece().rotation();
        TetrisGameState queuedOpponentRotation = delayedOpponent.rotateClockwise(PlayerSide.TOP);
        assertEquals(opponentRotation, queuedOpponentRotation.topPlayer().activePiece().rotation());
        assertEquals(
                TetrisEffectState.ROTATION_LAG_TICKS,
                queuedOpponentRotation.topPlayer().effects().rotationLagTicks());

        TetrisGameState delayedSelf = triggerBottomObject(TetrisItemType.ROTATION_DELAY_SELF);
        assertEquals(100, delayedSelf.bottomPlayer().effects().rotationEffectTicks());

        TetrisGameState afterEffect = spedUpOpponent;
        for (int tick = 0; tick < 100; tick++) {
            afterEffect = afterEffect.tickEffects();
        }
        assertEquals(100, afterEffect.topPlayer().effects().gravityPercent());
        assertEquals(0, afterEffect.topPlayer().effects().gravityTicks());
    }

    @Test
    void rotationDelayAppliesQueuedRotationAfterTwoSeconds() {
        TetrisGameState delayed = triggerBottomObject(TetrisItemType.ROTATION_DELAY_SELF);
        Rotation beforeQueuedRotation = delayed.bottomPlayer().activePiece().rotation();

        TetrisGameState queued = delayed.rotateClockwise(PlayerSide.BOTTOM);
        assertEquals(beforeQueuedRotation, queued.bottomPlayer().activePiece().rotation());
        assertEquals(TetrisEffectState.ROTATION_LAG_TICKS, queued.bottomPlayer().effects().rotationLagTicks());

        TetrisGameState afterLag = queued;
        for (int tick = 0; tick < TetrisEffectState.ROTATION_LAG_TICKS; tick++) {
            afterLag = afterLag.tickEffects();
        }
        assertFalse(beforeQueuedRotation == afterLag.bottomPlayer().activePiece().rotation());
    }

    @Test
    void clearingOneLineGrowsThePlayerBoardAndShrinksTheOpponentBoard() {
        BoardPosition[] filledCells = new BoardPosition[TetrisBoard.DEFAULT_COLUMNS - 2];
        for (int column = 2; column < TetrisBoard.DEFAULT_COLUMNS; column++) {
            filledCells[column - 2] = new BoardPosition(19, column);
        }
        TetrisBoard bottomBoard = boardWithFilledCells(filledCells);

        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                bottomBoard,
                new TetrisPiece(
                        PieceShape.standardShape(PieceType.O),
                        new BoardPosition(18, 0),
                        Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState next = runningState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP))
                .applyGravity(PlayerSide.BOTTOM);

        assertEquals(1, next.bottomPlayer().score());
        assertEquals(TetrisBoard.DEFAULT_ROWS + 1, next.bottomPlayer().board().rows());
        assertEquals(TetrisBoard.DEFAULT_ROWS - 1, next.topPlayer().board().rows());
    }

    @Test
    void teleportSendsTheControlledFallingShapeToOpponentNext() {
        TetrisPlayerState bottom = playerWithObject(
                PlayerSide.BOTTOM, new TetrisBoard(), PieceType.I, 4, TetrisItemType.TELEPORT);
        TetrisPlayerState top = playerWithObject(
                PlayerSide.TOP, new TetrisBoard(), PieceType.O, 2, null);

        TetrisGameState teleported = runningState(bottom, top).rotateClockwise(PlayerSide.BOTTOM);

        assertNull(teleported.bottomPlayer().activePiece());
        assertTrue(teleported.topPlayer().hasQueuedShape());
        assertEquals(PieceType.I, teleported.topPlayer().queuedShapes().getFirst().type());
        assertEquals(4, teleported.bottomPlayer().score());
        assertEquals(2, teleported.topPlayer().score());
    }

    @Test
    void controlledPieceTriggersRadiusAndColumnBombBlocks() {
        TetrisBoard radiusBoard = boardWithFilledCells(
                new BoardPosition(7, 4),
                new BoardPosition(10, 4));
        TetrisPlayerState radiusPlayer = playerWithObject(
                PlayerSide.BOTTOM, radiusBoard, PieceType.O, 0, TetrisItemType.RADIUS_BOMB);
        TetrisGameState radiusResult = runningState(
                radiusPlayer,
                playerWithObject(PlayerSide.TOP, new TetrisBoard(), PieceType.O, 0, null))
                .rotateClockwise(PlayerSide.BOTTOM);
        assertEquals(TetrisCell.EMPTY, radiusResult.bottomPlayer().board().cellAt(new BoardPosition(7, 4)));
        assertEquals(TetrisCell.FILLED, radiusResult.bottomPlayer().board().cellAt(new BoardPosition(10, 4)));

        TetrisBoard columnBoard = boardWithFilledCells(
                new BoardPosition(7, 4),
                new BoardPosition(7, 5));
        TetrisPlayerState columnPlayer = playerWithObject(
                PlayerSide.BOTTOM, columnBoard, PieceType.O, 0, TetrisItemType.COLUMN_BOMB);
        TetrisGameState columnResult = runningState(
                columnPlayer,
                playerWithObject(PlayerSide.TOP, new TetrisBoard(), PieceType.O, 0, null))
                .rotateClockwise(PlayerSide.BOTTOM);
        assertEquals(TetrisCell.EMPTY, columnResult.bottomPlayer().board().cellAt(new BoardPosition(7, 4)));
        assertEquals(TetrisCell.FILLED, columnResult.bottomPlayer().board().cellAt(new BoardPosition(7, 5)));
    }

    @Test
    void fallingPieceSwapExchangesOnlyTheControlledShapes() {
        TetrisBoard bottomBoard = boardWithFilledCell(19, 0);
        TetrisBoard topBoard = boardWithFilledCell(19, 9);
        TetrisPlayerState bottom = playerWithObject(
                PlayerSide.BOTTOM,
                bottomBoard,
                PieceType.O,
                7,
                TetrisItemType.FALLING_PIECE_SWAP);
        TetrisPlayerState top = playerWithObject(
                PlayerSide.TOP,
                topBoard,
                PieceType.I,
                3,
                null);

        TetrisGameState swapped = runningState(bottom, top).rotateClockwise(PlayerSide.BOTTOM);

        assertEquals(PieceType.I, swapped.bottomPlayer().activePiece().shape().type());
        assertEquals(PieceType.O, swapped.topPlayer().activePiece().shape().type());
        assertEquals(TetrisCell.FILLED, swapped.bottomPlayer().board().cellAt(new BoardPosition(19, 0)));
        assertEquals(TetrisCell.FILLED, swapped.topPlayer().board().cellAt(new BoardPosition(19, 9)));
        assertEquals(7, swapped.bottomPlayer().score());
        assertEquals(3, swapped.topPlayer().score());
    }

    @Test
    void speedDualPieceAndHorizontalOptionsSurviveConfigurationRoundTrip() {
        TetrisGameConfig config = new TetrisGameConfig(
                List.of("Standard"),
                List.of(),
                320,
                true,
                true);

        TetrisGameConfig restored = TetrisGameConfig.deserialize(config.serialize());

        assertEquals(320, restored.gravityMillis());
        assertTrue(restored.dualPieces());
        assertTrue(restored.horizontalMode());
        assertTrue(restored.availableShapes().stream()
                .allMatch(shape -> shape.name().startsWith("Dual ")));
        assertTrue(restored.availableShapes().stream()
                .allMatch(shape -> shape.cells().size() == 8));
        assertEquals(GravityDirection.RIGHT, restored.gravityDirection(PlayerSide.BOTTOM));
        assertEquals(GravityDirection.LEFT, restored.gravityDirection(PlayerSide.TOP));

        for (PlayerSide side : PlayerSide.values()) {
            TetrisPlayerState player = TetrisPlayerState.create("Player", side, true);
            TetrisPlayerState spawned = player.spawnPiece(
                    restored.availableShapes().getFirst(),
                    0,
                    restored.gravityDirection(side));
            assertNotNull(spawned.activePiece());
            assertEquals(PlayerStatus.PLAYING, spawned.status());

            BoardPosition beforeGravity = spawned.activePiece().position();
            TetrisPlayerState moved = spawned.applyGravity(restored.gravityDirection(side));
            assertEquals(
                    beforeGravity.column() + restored.gravityDirection(side).columnStep(),
                    moved.activePiece().position().column());
        }
    }

    @Test
    void everySpecialObjectTypeAppearsInAnObjectBag() {
        TetrisItemBag bag = new TetrisItemBag();
        Random random = new Random(42);
        Set<TetrisItemType> drawnTypes = EnumSet.noneOf(TetrisItemType.class);

        for (int draw = 0; draw < TetrisItemType.values().length; draw++) {
            drawnTypes.add(bag.next(random));
        }

        assertEquals(EnumSet.allOf(TetrisItemType.class), drawnTypes);
    }

    private static TetrisGameState triggerBottomObject(TetrisItemType objectType) {
        TetrisPlayerState bottom = playerWithObject(
                PlayerSide.BOTTOM,
                new TetrisBoard(),
                PieceType.O,
                0,
                objectType);
        TetrisPlayerState top = playerWithObject(
                PlayerSide.TOP,
                new TetrisBoard(),
                PieceType.T,
                0,
                null);
        return runningState(bottom, top).rotateClockwise(PlayerSide.BOTTOM);
    }

    private static TetrisGameState runningState(TetrisPlayerState bottom, TetrisPlayerState top) {
        return new TetrisGameState(bottom, top, TetrisGameConfig.defaultConfig(), TetrisGameStatus.RUNNING);
    }

    private static TetrisPlayerState playerWithObject(
            PlayerSide side,
            TetrisBoard board,
            PieceType pieceType,
            int score,
            TetrisItemType objectType) {
        TetrisPiece piece = new TetrisPiece(
                PieceShape.standardShape(pieceType),
                new BoardPosition(4, 4),
                Rotation.SPAWN);
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

    private static TetrisBoard boardWithFilledCell(int row, int column) {
        return boardWithFilledCells(new BoardPosition(row, column));
    }

    private static TetrisBoard boardWithFilledCells(BoardPosition... positions) {
        TetrisCell[][] cells = new TetrisCell[TetrisBoard.DEFAULT_ROWS][TetrisBoard.DEFAULT_COLUMNS];
        for (TetrisCell[] row : cells) {
            Arrays.fill(row, TetrisCell.EMPTY);
        }
        for (BoardPosition position : positions) {
            cells[position.row()][position.column()] = TetrisCell.FILLED;
        }
        return new TetrisBoard(cells);
    }
}
