package seda_project.control_alt_defeat.gamebox.model.tetris;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PieceType;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.Rotation;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        for (int column = 2; column < TetrisBoard.DEFAULT_COLUMNS; column++) {
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

        TetrisPlayerState locked = player.lockActivePiece(GravityDirection.DOWN);

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
    @DisplayName("FR-OUTPUT-23: board-swap object swaps player boards without changing scores or falling pieces")
    void frOutput23BoardSwapObjectSwapsBoardsOnlyAndKeepsScores() {
        TetrisPiece bottomPiece = new TetrisPiece(
                PieceShape.standardShape(PieceType.O),
                new BoardPosition(0, 0),
                Rotation.SPAWN);
        TetrisPiece topPiece = new TetrisPiece(
                PieceShape.standardShape(PieceType.I),
                new BoardPosition(4, 4),
                Rotation.SPAWN);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard().withCell(new BoardPosition(19, 0), TetrisCell.FILLED),
                bottomPiece,
                3,
                PlayerStatus.PLAYING,
                null,
                new BoardPosition(2, 0));
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                new TetrisBoard().withCell(new BoardPosition(18, 9), TetrisCell.FILLED),
                topPiece,
                7,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(
                bottom,
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState swapped = state.applyGravity(PlayerSide.BOTTOM);

        assertEquals("Bottom", swapped.bottomPlayer().playerName());
        assertEquals("Top", swapped.topPlayer().playerName());
        assertEquals(3, swapped.bottomPlayer().score());
        assertEquals(7, swapped.topPlayer().score());
        assertEquals(bottomPiece.withPosition(new BoardPosition(1, 0)), swapped.bottomPlayer().activePiece());
        assertEquals(topPiece, swapped.topPlayer().activePiece());
        assertEquals(TetrisCell.FILLED, swapped.bottomPlayer().board().cellAt(new BoardPosition(18, 9)));
        assertEquals(TetrisCell.FILLED, swapped.topPlayer().board().cellAt(new BoardPosition(19, 0)));
        assertNull(swapped.bottomPlayer().bugPosition());
        assertNull(swapped.topPlayer().bugPosition());
    }

    @Test
    @DisplayName("FR-OUTPUT-19: clearing a line grows clearer's board and shrinks opponent's board")
    void frOutput19ClearingLineGrowsActorBoardAndShrinksOpponentBoard() {
        TetrisBoard bottomBoard = new TetrisBoard();
        for (int column = 2; column < TetrisBoard.DEFAULT_COLUMNS; column++) {
            bottomBoard = bottomBoard.withCell(new BoardPosition(19, column), TetrisCell.FILLED);
        }

        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                bottomBoard,
                new TetrisPiece(PieceShape.standardShape(PieceType.O), new BoardPosition(18, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState next = state.applyGravity(PlayerSide.BOTTOM);

        // Clearing player (bottom, DOWN gravity) gains 1 extra row at the top
        assertEquals(1, next.bottomPlayer().score());
        assertEquals(TetrisBoard.DEFAULT_ROWS + 1, next.bottomPlayer().board().rows());

        // Opponent (top, UP gravity) loses 1 row from the bottom (spawn side)
        assertEquals(TetrisBoard.DEFAULT_ROWS - 1, next.topPlayer().board().rows());
    }

    @Test
    void explosionObjectDestroysBlocksInRadius() {
        TetrisBoard board = new TetrisBoard()
                .withCell(new BoardPosition(0, 3), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 4), TetrisCell.FILLED);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                board,
                new TetrisPiece(PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.EXPLODE_RADIUS, new BoardPosition(0, 0)),
                null,
                List.of());
        TetrisGameState state = new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState next = state.moveLeft(PlayerSide.BOTTOM);

        assertEquals(TetrisCell.EMPTY, next.bottomPlayer().board().cellAt(new BoardPosition(0, 3)));
        assertEquals(TetrisCell.FILLED, next.bottomPlayer().board().cellAt(new BoardPosition(4, 4)));
        assertNull(next.bottomPlayer().boardObject());
    }

    @Test
    void portalQueuesCurrentPieceForOpponent() {
        PieceShape portalShape = PieceShape.standardShape(PieceType.O);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard(),
                new TetrisPiece(portalShape, new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.PORTAL, new BoardPosition(0, 0)),
                null,
                List.of());
        TetrisGameState state = new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState next = state.moveLeft(PlayerSide.BOTTOM)
                .spawnPiece(PlayerSide.TOP, PieceShape.standardShape(PieceType.I));

        assertEquals(portalShape, next.topPlayer().activePiece().shape());
    }

    @Test
    void portalQueuesTransferredPieceBeforeExistingQueuedShapes() {
        PieceShape portalShape = PieceShape.standardShape(PieceType.O);
        PieceShape existingQueuedShape = PieceShape.standardShape(PieceType.I);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard(),
                new TetrisPiece(portalShape, new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.PORTAL, new BoardPosition(0, 0)),
                null,
                List.of());
        TetrisPlayerState top = TetrisPlayerState.create("Top", PlayerSide.TOP)
                .queueShape(existingQueuedShape);
        TetrisGameState state = new TetrisGameState(
                bottom,
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState next = state.moveLeft(PlayerSide.BOTTOM)
                .spawnPiece(PlayerSide.TOP, PieceShape.standardShape(PieceType.T));

        assertEquals(portalShape, next.topPlayer().activePiece().shape());
        assertEquals(existingQueuedShape, next.topPlayer().queuedShapes().getFirst());
    }

    @Test
    void rotationDelayObjectAppliesRotationAfterTwoSecondLag() {
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard(),
                new TetrisPiece(PieceShape.standardShape(PieceType.T), new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.ROTATION_DELAY_SELF, new BoardPosition(0, 0)),
                null,
                List.of());
        TetrisGameState state = new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState withEffect = state.moveLeft(PlayerSide.BOTTOM);
        TetrisGameState lagScheduled = withEffect.rotateClockwise(PlayerSide.BOTTOM);

        assertTrue(withEffect.bottomPlayer().effects().hasRotationDelayEffect());
        assertEquals(100, withEffect.bottomPlayer().effects().rotationEffectTicks());
        assertEquals(Rotation.SPAWN, lagScheduled.bottomPlayer().activePiece().rotation());
        assertTrue(lagScheduled.bottomPlayer().effects().rotationLagTicks() > 0);
        assertEquals(
                lagScheduled,
                lagScheduled.rotateClockwise(PlayerSide.BOTTOM));

        TetrisGameState afterLag = lagScheduled;
        for (int tick = 0; tick < 19; tick++) {
            afterLag = afterLag.tickEffects();
        }

        assertEquals(Rotation.SPAWN, afterLag.bottomPlayer().activePiece().rotation());
        afterLag = afterLag.tickEffects();
        assertEquals(Rotation.RIGHT, afterLag.bottomPlayer().activePiece().rotation());
    }

    @Test
    void speedObjectsUseDoubleSpeedForTenSeconds() {
        TetrisGameState spedUp = stateWithBottomObject(TetrisItemType.SPEED_UP_OPPONENT)
                .moveLeft(PlayerSide.BOTTOM);
        TetrisGameState slowSelf = stateWithBottomObject(TetrisItemType.SLOW_SELF)
                .moveLeft(PlayerSide.BOTTOM);
        TetrisGameState slowOpponent = stateWithBottomObject(TetrisItemType.SLOW_OPPONENT)
                .moveLeft(PlayerSide.BOTTOM);

        assertEquals(50, spedUp.topPlayer().effects().gravityPercent());
        assertEquals(100, spedUp.topPlayer().effects().gravityTicks());
        assertEquals(200, slowSelf.bottomPlayer().effects().gravityPercent());
        assertEquals(100, slowSelf.bottomPlayer().effects().gravityTicks());
        assertEquals(200, slowOpponent.topPlayer().effects().gravityPercent());
        assertEquals(100, slowOpponent.topPlayer().effects().gravityTicks());

        TetrisGameState ready = spedUp;
        for (int tick = 0; tick < 100; tick++) {
            ready = ready.tickEffects();
        }

        assertEquals(100, ready.topPlayer().effects().gravityPercent());
        assertEquals(0, ready.topPlayer().effects().gravityTicks());
    }

    @Test
    void boardObjectsExpireAfterTenSeconds() {
        TetrisGameState state = stateWithBottomObject(TetrisItemType.EXPLODE_BELOW);

        for (int tick = 0; tick < 99; tick++) {
            state = state.tickEffects();
        }

        assertNotNull(state.bottomPlayer().boardObject());
        state = state.tickEffects();
        assertNull(state.bottomPlayer().boardObject());
    }

    @Test
    void objectPlacementRejectsLockedBlocks() {
        BoardPosition lockedPosition = new BoardPosition(5, 5);
        TetrisPlayerState player = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard().withCell(lockedPosition, TetrisCell.FILLED),
                null,
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisBoardObject object = new TetrisBoardObject(TetrisItemType.SLOW_SELF, lockedPosition);

        assertFalse(player.canPlaceObject(object));
        assertNull(player.withBoardObject(object).boardObject());
    }

    @Test
    void objectSpawnRequiresSupportedFreeTile() {
        TetrisPlayerState player = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM);
        TetrisBoardObject middleObject = new TetrisBoardObject(TetrisItemType.SLOW_SELF, new BoardPosition(8, 5));
        TetrisBoardObject floorObject = new TetrisBoardObject(TetrisItemType.SLOW_SELF, new BoardPosition(19, 5));
        TetrisPlayerState withLockedBlock = player.withBoard(new TetrisBoard()
                .withCell(new BoardPosition(9, 5), TetrisCell.FILLED));

        assertFalse(player.canSpawnObject(middleObject, GravityDirection.DOWN));
        assertTrue(player.canSpawnObject(floorObject, GravityDirection.DOWN));
        assertTrue(withLockedBlock.canSpawnObject(middleObject, GravityDirection.DOWN));
    }

    @Test
    void objectSpawnRejectsCavitiesBlockedFromSpawnSide() {
        TetrisBoardObject object = new TetrisBoardObject(TetrisItemType.SLOW_SELF, new BoardPosition(8, 5));
        TetrisPlayerState player = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM)
                .withBoard(new TetrisBoard()
                        .withCell(new BoardPosition(7, 5), TetrisCell.FILLED)
                        .withCell(new BoardPosition(9, 5), TetrisCell.FILLED));

        assertFalse(player.canSpawnObject(object, GravityDirection.DOWN));
    }

    @Test
    void objectSpawnUsesHorizontalGravitySupport() {
        TetrisPlayerState player = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM);
        TetrisBoardObject middleObject = new TetrisBoardObject(TetrisItemType.SLOW_SELF, new BoardPosition(8, 5));
        TetrisBoardObject wallObject = new TetrisBoardObject(TetrisItemType.SLOW_SELF, new BoardPosition(8, 9));

        assertFalse(player.canSpawnObject(middleObject, GravityDirection.RIGHT));
        assertTrue(player.canSpawnObject(wallObject, GravityDirection.RIGHT));
    }

    @Test
    void objectSpawnRejectsHorizontalCavitiesBlockedFromSpawnSide() {
        TetrisBoardObject object = new TetrisBoardObject(TetrisItemType.SLOW_SELF, new BoardPosition(8, 5));
        TetrisPlayerState player = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM)
                .withBoard(new TetrisBoard()
                        .withCell(new BoardPosition(8, 4), TetrisCell.FILLED)
                        .withCell(new BoardPosition(8, 6), TetrisCell.FILLED));

        assertFalse(player.canSpawnObject(object, GravityDirection.RIGHT));
    }

    @Test
    void horizontalModeSpawnsAtSideAndFallsSideways() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, false, true);
        TetrisGameState state = new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM, true),
                TetrisPlayerState.create("Top", PlayerSide.TOP, true),
                config,
                TetrisGameStatus.RUNNING).spawnPiece(
                        PlayerSide.BOTTOM,
                        config.availableShapes().getFirst());

        assertEquals(TetrisBoard.HORIZONTAL_ROWS, state.bottomPlayer().board().rows());
        assertEquals(TetrisBoard.HORIZONTAL_COLUMNS, state.bottomPlayer().board().columns());

        TetrisGameState next = state.applyGravity(PlayerSide.BOTTOM);

        assertEquals(0, state.bottomPlayer().activePiece().position().column());
        assertEquals(1, next.bottomPlayer().activePiece().position().column());
    }

    @Test
    void horizontalTopPlayerSpawnsAtRightAndFallsLeft() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, false, true);
        PieceShape shape = config.availableShapes().getFirst();
        TetrisGameState state = new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM, true),
                TetrisPlayerState.create("Top", PlayerSide.TOP, true),
                config,
                TetrisGameStatus.RUNNING).spawnPiece(PlayerSide.TOP, shape);

        TetrisGameState next = state.applyGravity(PlayerSide.TOP);

        assertEquals(TetrisBoard.HORIZONTAL_COLUMNS - shape.width(), state.topPlayer().activePiece().position().column());
        assertEquals(state.topPlayer().activePiece().position().column() - 1, next.topPlayer().activePiece().position().column());
    }

    @Test
    void horizontalModeClearsFullColumnsAndTransfersWidth() {
        TetrisBoard board = new TetrisBoard(TetrisBoard.HORIZONTAL_ROWS, TetrisBoard.HORIZONTAL_COLUMNS);
        for (int row = 0; row < TetrisBoard.HORIZONTAL_ROWS; row++) {
            if (row != 7 && row != 8) {
                board = board.withCell(new BoardPosition(row, 19), TetrisCell.FILLED);
            }
        }

        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, false, true);
        PieceShape oShape = PieceShape.standardShape(PieceType.O).rotateClockwise90();
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                board,
                new TetrisPiece(
                        oShape,
                        new BoardPosition(7, 18),
                        Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP, true),
                config,
                TetrisGameStatus.RUNNING);

        TetrisGameState next = state.applyGravity(PlayerSide.BOTTOM);

        assertEquals(1, next.bottomPlayer().score());
        assertEquals(TetrisBoard.HORIZONTAL_COLUMNS + 1, next.bottomPlayer().board().columns());
        assertEquals(TetrisBoard.HORIZONTAL_COLUMNS - 1, next.topPlayer().board().columns());
    }

    @Test
    void horizontalBoardSwapMirrorsBoardsAndKeepsFallingPiecesWithOwners() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, false, true);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard(TetrisBoard.HORIZONTAL_ROWS, TetrisBoard.HORIZONTAL_COLUMNS)
                        .withCell(new BoardPosition(5, 19), TetrisCell.FILLED),
                new TetrisPiece(PieceShape.standardShape(PieceType.J), new BoardPosition(4, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, new BoardPosition(5, 3)),
                null,
                List.of());
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                new TetrisBoard(TetrisBoard.HORIZONTAL_ROWS, TetrisBoard.HORIZONTAL_COLUMNS)
                        .withCell(new BoardPosition(2, 0), TetrisCell.FILLED),
                new TetrisPiece(PieceShape.standardShape(PieceType.L), new BoardPosition(2, 16), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(bottom, top, config, TetrisGameStatus.RUNNING);

        TetrisGameState swapped = state.applyGravity(PlayerSide.BOTTOM);

        assertFalse(swapped.isFinished());
        assertEquals(PlayerStatus.PLAYING, swapped.bottomPlayer().status());
        assertEquals(PlayerStatus.PLAYING, swapped.topPlayer().status());
        assertEquals(bottom.activePiece().shape(), swapped.bottomPlayer().activePiece().shape());
        assertEquals(top.activePiece().shape(), swapped.topPlayer().activePiece().shape());
        assertTrue(swapped.bottomPlayer().board().canPlace(swapped.bottomPlayer().activePiece()));
        assertTrue(swapped.topPlayer().board().canPlace(swapped.topPlayer().activePiece()));
        assertEquals(TetrisCell.FILLED, swapped.bottomPlayer().board().cellAt(new BoardPosition(2, 19)));
        assertEquals(TetrisCell.FILLED, swapped.topPlayer().board().cellAt(new BoardPosition(5, 0)));
    }

    @Test
    void horizontalTeleporterKeepsNextSpawnPlayable() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, false, true);
        TetrisGameState swapped = horizontalTeleporterState(
                config,
                PieceShape.standardShape(PieceType.J).rotateClockwise90(),
                PieceShape.standardShape(PieceType.L).rotateClockwise90())
                .applyGravity(PlayerSide.BOTTOM);

        TetrisGameState afterLock = dropUntilLocked(swapped, PlayerSide.BOTTOM);
        afterLock = dropUntilLocked(afterLock, PlayerSide.TOP);
        afterLock = afterLock.spawnPiece(PlayerSide.BOTTOM, config.availableShapes().getFirst());
        afterLock = afterLock.spawnPiece(PlayerSide.TOP, config.availableShapes().getFirst());

        assertEquals(PlayerStatus.PLAYING, afterLock.bottomPlayer().status());
        assertEquals(PlayerStatus.PLAYING, afterLock.topPlayer().status());
        assertNotNull(afterLock.bottomPlayer().activePiece());
        assertNotNull(afterLock.topPlayer().activePiece());
    }

    @Test
    void horizontalDualTeleporterKeepsNextSpawnPlayable() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, true, true);
        TetrisGameState swapped = horizontalTeleporterState(
                config,
                config.availableShapes().stream()
                        .filter(shape -> shape.name().equals("Dual J+L"))
                        .findFirst()
                        .orElse(config.availableShapes().getFirst()),
                config.availableShapes().stream()
                        .filter(shape -> shape.name().equals("Dual L+I"))
                        .findFirst()
                        .orElse(config.availableShapes().getLast()))
                .applyGravity(PlayerSide.BOTTOM);

        TetrisGameState afterLock = dropUntilLocked(swapped, PlayerSide.BOTTOM);
        afterLock = dropUntilLocked(afterLock, PlayerSide.TOP);
        afterLock = afterLock.spawnPiece(PlayerSide.BOTTOM, config.availableShapes().getFirst());
        afterLock = afterLock.spawnPiece(PlayerSide.TOP, config.availableShapes().getFirst());

        assertEquals(PlayerStatus.PLAYING, afterLock.bottomPlayer().status());
        assertEquals(PlayerStatus.PLAYING, afterLock.topPlayer().status());
        assertNotNull(afterLock.bottomPlayer().activePiece());
        assertNotNull(afterLock.topPlayer().activePiece());
    }

    @Test
    void dualPiecesSpawnCleanlyInVerticalAndHorizontalModes() {
        for (boolean horizontalMode : List.of(false, true)) {
            TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, true, horizontalMode);
            for (PlayerSide side : PlayerSide.values()) {
                TetrisPlayerState player = TetrisPlayerState.create("Player", side, horizontalMode);
                for (PieceShape shape : config.availableShapes()) {
                    TetrisPlayerState spawned = player.spawnPiece(shape, 0, config.gravityDirection(side));
                    assertEquals(PlayerStatus.PLAYING, spawned.status(), shape.name() + " should spawn for " + side);
                    assertNotNull(spawned.activePiece(), shape.name() + " should fit on spawn for " + side);
                }
            }
        }
    }

    @Test
    void configOptionsRoundTrip() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 320, true, true);

        TetrisGameConfig copy = TetrisGameConfig.deserialize(config.serialize());

        assertEquals(320, copy.gravityMillis());
        assertTrue(copy.dualPieces());
        assertTrue(copy.horizontalMode());
        assertTrue(copy.availableShapes().getFirst().name().startsWith("Dual "));
    }

    @Test
    void gravitySpeedRampsDownOverTime() {
        TetrisGameConfig config = TetrisGameConfig.defaultConfig();

        assertEquals(550, config.gravityMillisAtElapsed(0));
        assertEquals(550, config.gravityMillisAtElapsed(14_999));
        assertEquals(530, config.gravityMillisAtElapsed(15_000));
        assertEquals(510, config.gravityMillisAtElapsed(30_000));
    }

    @Test
    void otherPlayerContinuesAfterOnePlayerLoses() {
        TetrisPlayerState bottom = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM).lost();
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                new TetrisBoard(),
                new TetrisPiece(PieceShape.standardShape(PieceType.O), new BoardPosition(17, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(
                bottom,
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        TetrisGameState next = state.applyGravity(PlayerSide.TOP);

        assertFalse(next.isFinished());
        assertEquals(PlayerStatus.LOST, next.bottomPlayer().status());
        assertEquals(new BoardPosition(18, 0), next.topPlayer().activePiece().position());
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

    @Test
    void itemBagReturnsEveryObjectTypeBeforeRepeating() {
        TetrisItemBag bag = new TetrisItemBag();
        Random random = new Random(42);
        Set<TetrisItemType> expectedTypes = EnumSet.allOf(TetrisItemType.class);

        Set<TetrisItemType> firstBag = drawFullBag(bag, random);
        Set<TetrisItemType> secondBag = drawFullBag(bag, random);

        assertEquals(expectedTypes, firstBag);
        assertEquals(expectedTypes, secondBag);
    }

    @Test
    void opponentDependencyClassificationMatchesObjectEffects() {
        Set<TetrisItemType> opponentDependentTypes = EnumSet.noneOf(TetrisItemType.class);
        for (TetrisItemType type : TetrisItemType.values()) {
            if (type.requiresActiveOpponent()) {
                opponentDependentTypes.add(type);
            }
        }

        assertEquals(
                EnumSet.of(
                        TetrisItemType.SPEED_UP_OPPONENT,
                        TetrisItemType.ROTATION_DELAY_OPPONENT,
                        TetrisItemType.SLOW_OPPONENT,
                        TetrisItemType.PORTAL,
                        TetrisItemType.TELEPORT_SWAP,
                        TetrisItemType.PIECE_SWAP),
                opponentDependentTypes);
    }

    @Test
    void itemBagOnlyReturnsEligibleObjectsWhenOpponentHasLost() {
        TetrisItemBag bag = new TetrisItemBag();
        Random random = new Random(42);
        Set<TetrisItemType> eligibleTypes = EnumSet.of(
                TetrisItemType.SLOW_SELF,
                TetrisItemType.ROTATION_DELAY_SELF,
                TetrisItemType.EXPLODE_RADIUS,
                TetrisItemType.EXPLODE_BELOW);

        Set<TetrisItemType> firstBag = drawFullBag(bag, random, eligibleTypes);
        Set<TetrisItemType> secondBag = drawFullBag(bag, random, eligibleTypes);

        assertEquals(eligibleTypes, firstBag);
        assertEquals(eligibleTypes, secondBag);
    }

    @Test
    void itemBagDropsDisallowedRemainingObjectsWhenEligibilityChanges() {
        TetrisItemBag bag = new TetrisItemBag();
        Random random = new Random(42);
        Set<TetrisItemType> eligibleTypes = EnumSet.of(
                TetrisItemType.SLOW_SELF,
                TetrisItemType.ROTATION_DELAY_SELF,
                TetrisItemType.EXPLODE_RADIUS,
                TetrisItemType.EXPLODE_BELOW);

        assertNotNull(bag.next(random));

        for (int draw = 0; draw < 20; draw++) {
            TetrisItemType type = bag.next(random, eligibleTypes);

            assertNotNull(type);
            assertTrue(eligibleTypes.contains(type), type.name());
        }
    }

    @Test
    void opponentObjectsAreClearedWithoutEffectAfterOpponentLoses() {
        for (TetrisItemType type : EnumSet.allOf(TetrisItemType.class)) {
            if (!type.requiresActiveOpponent()) {
                continue;
            }

            TetrisGameState state = stateWithBottomObjectAndLostTop(type);
            TetrisGameState next = state.moveLeft(PlayerSide.BOTTOM);

            assertNull(next.bottomPlayer().boardObject(), type.name());
            assertEquals(state.bottomPlayer().activePiece(), next.bottomPlayer().activePiece(), type.name());
            assertEquals(state.topPlayer(), next.topPlayer(), type.name());
        }
    }

    @Test
    void opponentObjectsOnTopAreClearedWithoutEffectAfterBottomLoses() {
        for (TetrisItemType type : EnumSet.allOf(TetrisItemType.class)) {
            if (!type.requiresActiveOpponent()) {
                continue;
            }

            TetrisGameState state = stateWithTopObjectAndLostBottom(type);
            TetrisGameState next = state.moveLeft(PlayerSide.TOP);

            assertNull(next.topPlayer().boardObject(), type.name());
            assertEquals(state.topPlayer().activePiece(), next.topPlayer().activePiece(), type.name());
            assertEquals(state.bottomPlayer(), next.bottomPlayer(), type.name());
        }
    }

    @Test
    void selfContainedObjectsStillWorkAfterOpponentLoses() {
        TetrisGameState slowSelf = stateWithBottomObjectAndLostTop(TetrisItemType.SLOW_SELF)
                .moveLeft(PlayerSide.BOTTOM);
        TetrisGameState delaySelf = stateWithBottomObjectAndLostTop(TetrisItemType.ROTATION_DELAY_SELF)
                .moveLeft(PlayerSide.BOTTOM);
        TetrisGameState radiusExplosion = stateWithBottomObjectAndLostTop(
                TetrisItemType.EXPLODE_RADIUS,
                new TetrisBoard().withCell(new BoardPosition(1, 1), TetrisCell.FILLED))
                .moveLeft(PlayerSide.BOTTOM);
        TetrisGameState belowExplosion = stateWithBottomObjectAndLostTop(
                TetrisItemType.EXPLODE_BELOW,
                new TetrisBoard().withCell(new BoardPosition(5, 0), TetrisCell.FILLED))
                .moveLeft(PlayerSide.BOTTOM);

        assertEquals(200, slowSelf.bottomPlayer().effects().gravityPercent());
        assertEquals(100, slowSelf.bottomPlayer().effects().gravityTicks());
        assertEquals(100, delaySelf.bottomPlayer().effects().rotationEffectTicks());
        assertEquals(TetrisCell.EMPTY, radiusExplosion.bottomPlayer().board().cellAt(new BoardPosition(1, 1)));
        assertEquals(TetrisCell.EMPTY, belowExplosion.bottomPlayer().board().cellAt(new BoardPosition(5, 0)));
    }

    @Test
    @DisplayName("PIECE_SWAP (G): each player gets the opponent's piece shape at their own current position")
    void pieceSwapObjectSwapsShapesInPlace() {
        PieceShape oShape = PieceShape.standardShape(PieceType.O);  // 2×2
        PieceShape iShape = PieceShape.standardShape(PieceType.I);  // 1×4

        // Bottom: O-piece at (0,0), color 1.  Piece fits → it will receive the I at (0,0) in SPAWN rotation.
        // Top:    I-piece at (16,3), color 2.  Piece fits → it will receive the O at (16,3) in SPAWN rotation.
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard().withCell(new BoardPosition(19, 0), TetrisCell.FILLED),
                new TetrisPiece(oShape, new BoardPosition(0, 0), Rotation.SPAWN, 1),
                2,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.PIECE_SWAP, new BoardPosition(0, 0)),
                null,
                List.of());
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                new TetrisBoard().withCell(new BoardPosition(18, 9), TetrisCell.FILLED),
                new TetrisPiece(iShape, new BoardPosition(16, 3), Rotation.SPAWN, 2),
                5,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(
                bottom,
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        // Trigger the swap via movement into the object
        TetrisGameState next = state.moveLeft(PlayerSide.BOTTOM);

        // Scores and boards must be unchanged
        assertEquals(2, next.bottomPlayer().score());
        assertEquals(5, next.topPlayer().score());
        assertEquals(TetrisCell.FILLED, next.bottomPlayer().board().cellAt(new BoardPosition(19, 0)));
        assertEquals(TetrisCell.FILLED, next.topPlayer().board().cellAt(new BoardPosition(18, 9)));

        // Object must be cleared from bottom's board
        assertNull(next.bottomPlayer().boardObject());

        // Bottom player now has the I-piece (top's shape) at bottom's original position (0,0)
        // using top's color index (2)
        assertNotNull(next.bottomPlayer().activePiece());
        assertEquals(iShape, next.bottomPlayer().activePiece().shape());
        assertEquals(new BoardPosition(0, 0), next.bottomPlayer().activePiece().position());
        assertEquals(2, next.bottomPlayer().activePiece().colorIndex());

        // Top player now has the O-piece (bottom's shape) at top's original position (16,3)
        // using bottom's color index (1)
        assertNotNull(next.topPlayer().activePiece());
        assertEquals(oShape, next.topPlayer().activePiece().shape());
        assertEquals(new BoardPosition(16, 3), next.topPlayer().activePiece().position());
        assertEquals(1, next.topPlayer().activePiece().colorIndex());
    }

    @Test
    @DisplayName("PIECE_SWAP (G): nudges swapped pieces back inside the board in horizontal mode")
    void pieceSwapKeepsHorizontalOpponentPieceInsideBoard() {
        TetrisGameConfig config = new TetrisGameConfig(List.of("Standard"), List.of(), 550, false, true);
        PieceShape lShape = config.availableShapes().stream()
                .filter(shape -> shape.name().equals("L"))
                .findFirst()
                .orElseThrow();
        PieceShape iShape = config.availableShapes().stream()
                .filter(shape -> shape.name().equals("I"))
                .findFirst()
                .orElseThrow();

        TetrisPlayerState bottom = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM, true)
                .spawnPiece(lShape, -1, config.gravityDirection(PlayerSide.BOTTOM));
        TetrisBoardObject swapObject = new TetrisBoardObject(
                TetrisItemType.PIECE_SWAP,
                bottom.activePiece().withPosition(new BoardPosition(
                        bottom.activePiece().position().row(),
                        bottom.activePiece().position().column() + 1)).boardCells().getFirst());
        bottom = new TetrisPlayerState(
                bottom.playerName(),
                bottom.side(),
                bottom.board(),
                bottom.activePiece(),
                bottom.score(),
                bottom.status(),
                bottom.finalScore(),
                swapObject,
                bottom.effects(),
                bottom.queuedShapes());
        TetrisPlayerState top = TetrisPlayerState.create("Top", PlayerSide.TOP, true)
                .spawnPiece(iShape, -1, config.gravityDirection(PlayerSide.TOP));
        TetrisGameState state = new TetrisGameState(bottom, top, config, TetrisGameStatus.RUNNING);

        TetrisGameState next = state.applyGravity(PlayerSide.BOTTOM);

        assertNotNull(next.topPlayer().activePiece());
        assertEquals(lShape, next.topPlayer().activePiece().shape());
        assertTrue(next.topPlayer().board().canPlace(next.topPlayer().activePiece()));
    }

    @Test
    @DisplayName("PIECE_SWAP (G): falls back to re-queue when swapped shape still cannot fit after nudging")
    void pieceSwapFallsBackToRespawnWhenShapeDoesNotFit() {
        PieceShape oShape = PieceShape.standardShape(PieceType.O);  // 2×2
        PieceShape iShape = PieceShape.standardShape(PieceType.I);  // 1×4 wide

        // Block row 0 columns 0..7 so the incoming I-piece cannot fit anywhere after bounds correction.
        TetrisBoard cramped = new TetrisBoard();
        for (int c = 0; c < 8; c++) {
            cramped = cramped.withCell(new BoardPosition(0, c), TetrisCell.FILLED);
        }

        // Bottom: O-piece sits in the only open 2x2 space at the right edge, but the incoming I-piece cannot fit.
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                cramped,
                new TetrisPiece(oShape, new BoardPosition(0, 8), Rotation.SPAWN, 1),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(TetrisItemType.PIECE_SWAP, new BoardPosition(0, 8)),
                null,
                List.of());
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                new TetrisBoard(),
                new TetrisPiece(iShape, new BoardPosition(16, 3), Rotation.SPAWN, 2),
                0,
                PlayerStatus.PLAYING,
                null);
        TetrisGameState state = new TetrisGameState(
                bottom, top, TetrisGameConfig.defaultConfig(), TetrisGameStatus.RUNNING);

        TetrisGameState next = state.moveLeft(PlayerSide.BOTTOM);

        // Bottom could not fit the I-piece → active piece nulled, I-shape queued for respawn
        assertNull(next.bottomPlayer().activePiece());
        assertTrue(next.bottomPlayer().hasQueuedShape());
        assertEquals(iShape, next.bottomPlayer().queuedShapes().getFirst());
    }

    @Test
    @DisplayName("Item bag includes PIECE_SWAP (G) object")
    void itemBagIncludesPieceSwapObject() {
        Set<TetrisItemType> drawnTypes = drawFullBag(new TetrisItemBag(), new Random(7));

        assertTrue(drawnTypes.contains(TetrisItemType.PIECE_SWAP));
    }

    private static Set<TetrisItemType> drawFullBag(TetrisItemBag bag, Random random) {
        Set<TetrisItemType> drawnTypes = EnumSet.noneOf(TetrisItemType.class);
        for (int draw = 0; draw < TetrisItemType.values().length; draw++) {
            drawnTypes.add(bag.next(random));
        }

        return drawnTypes;
    }

    private static Set<TetrisItemType> drawFullBag(
            TetrisItemBag bag,
            Random random,
            Set<TetrisItemType> eligibleTypes) {
        Set<TetrisItemType> drawnTypes = EnumSet.noneOf(TetrisItemType.class);
        for (int draw = 0; draw < eligibleTypes.size(); draw++) {
            drawnTypes.add(bag.next(random, eligibleTypes));
        }

        return drawnTypes;
    }

    private static TetrisGameState stateWithBottomObject(TetrisItemType type) {
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                new TetrisBoard(),
                new TetrisPiece(PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(type, new BoardPosition(0, 0)),
                null,
                List.of());

        return new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);
    }

    private static TetrisGameState stateWithBottomObjectAndLostTop(TetrisItemType type) {
        return stateWithBottomObjectAndLostTop(type, new TetrisBoard());
    }

    private static TetrisGameState stateWithBottomObjectAndLostTop(TetrisItemType type, TetrisBoard board) {
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                board,
                new TetrisPiece(PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(type, new BoardPosition(0, 0)),
                null,
                List.of());

        return new TetrisGameState(
                bottom,
                TetrisPlayerState.create("Top", PlayerSide.TOP).lost(),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);
    }

    private static TetrisGameState stateWithTopObjectAndLostBottom(TetrisItemType type) {
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                new TetrisBoard(),
                new TetrisPiece(PieceShape.standardShape(PieceType.O), new BoardPosition(0, 0), Rotation.SPAWN),
                0,
                PlayerStatus.PLAYING,
                null,
                new TetrisBoardObject(type, new BoardPosition(0, 0)),
                null,
                List.of());

        return new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM).lost(),
                top,
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);
    }

    private static TetrisGameState dropUntilLocked(TetrisGameState state, PlayerSide side) {
        TetrisGameState next = state;
        for (int step = 0; step < 40; step++) {
            TetrisPlayerState player = side == PlayerSide.BOTTOM ? next.bottomPlayer() : next.topPlayer();
            if (player.activePiece() == null || !player.isPlaying()) {
                return next;
            }
            next = next.applyGravity(side);
        }
        fail("Piece did not lock in time for " + side);
        return next;
    }

    private static TetrisGameState horizontalTeleporterState(
            TetrisGameConfig config,
            PieceShape bottomShape,
            PieceShape topShape) {
        TetrisBoard bottomBoard = new TetrisBoard(TetrisBoard.HORIZONTAL_ROWS, TetrisBoard.HORIZONTAL_COLUMNS)
                .withCell(new BoardPosition(4, 16), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 17), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 18), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 19), TetrisCell.FILLED);
        TetrisBoard topBoard = new TetrisBoard(TetrisBoard.HORIZONTAL_ROWS, TetrisBoard.HORIZONTAL_COLUMNS)
                .withCell(new BoardPosition(4, 0), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 1), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 2), TetrisCell.FILLED)
                .withCell(new BoardPosition(4, 3), TetrisCell.FILLED);
        TetrisPlayerState bottom = new TetrisPlayerState(
                "Bottom",
                PlayerSide.BOTTOM,
                bottomBoard,
                null,
                0,
                PlayerStatus.PLAYING,
                null)
                .spawnPiece(bottomShape, -1, config.gravityDirection(PlayerSide.BOTTOM));
        TetrisPlayerState top = new TetrisPlayerState(
                "Top",
                PlayerSide.TOP,
                topBoard,
                null,
                0,
                PlayerStatus.PLAYING,
                null)
                .spawnPiece(topShape, -1, config.gravityDirection(PlayerSide.TOP));
        BoardPosition triggerPosition = bottom.activePiece()
                .withPosition(new BoardPosition(
                        bottom.activePiece().position().row(),
                        bottom.activePiece().position().column() + 1))
                .boardCells()
                .getFirst();
        bottom = new TetrisPlayerState(
                bottom.playerName(),
                bottom.side(),
                bottom.board(),
                bottom.activePiece(),
                bottom.score(),
                bottom.status(),
                bottom.finalScore(),
                new TetrisBoardObject(TetrisItemType.TELEPORT_SWAP, triggerPosition),
                bottom.effects(),
                bottom.queuedShapes());

        return new TetrisGameState(bottom, top, config, TetrisGameStatus.RUNNING);
    }
}
