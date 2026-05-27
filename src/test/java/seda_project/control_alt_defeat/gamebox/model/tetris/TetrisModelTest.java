package seda_project.control_alt_defeat.gamebox.model.tetris;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.*;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.List;
import java.util.stream.IntStream;

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
    void teleporterObjectSwapsBoardsAndPiecesButKeepsScores() {
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
        assertEquals(topPiece, swapped.bottomPlayer().activePiece());
        assertEquals(bottomPiece.withPosition(new BoardPosition(1, 0)), swapped.topPlayer().activePiece());
        assertEquals(TetrisCell.FILLED, swapped.bottomPlayer().board().cellAt(new BoardPosition(18, 9)));
        assertNull(swapped.topPlayer().bugPosition());
    }

    @Test
    void clearingLineClearsPlayerRowAndAddsGarbageLineToOpponent() {
        TetrisBoard bottomBoard = new TetrisBoard();
        for (int column = 2; column < TetrisBoard.COLUMNS; column++) {
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

        long filledCells = IntStream.range(0, TetrisBoard.COLUMNS)
                .filter(column -> next.topPlayer().board().cellAt(new BoardPosition(19, column)) == TetrisCell.FILLED)
                .count();
        assertEquals(1, next.bottomPlayer().score());
        assertEquals(TetrisCell.EMPTY, next.bottomPlayer().board().cellAt(new BoardPosition(18, 0)));
        assertEquals(TetrisBoard.COLUMNS - 1, filledCells);
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
    void rotationDelayObjectBlocksRotationUntilItTicksDown() {
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

        TetrisGameState delayed = state.moveLeft(PlayerSide.BOTTOM).rotateClockwise(PlayerSide.BOTTOM);
        TetrisGameState ready = delayed;
        for (int tick = 0; tick < 99; tick++) {
            ready = ready.tickEffects();
        }

        assertEquals(Rotation.SPAWN, delayed.bottomPlayer().activePiece().rotation());
        assertEquals(Rotation.SPAWN, ready.rotateClockwise(PlayerSide.BOTTOM).bottomPlayer().activePiece().rotation());
        ready = ready.tickEffects();
        assertEquals(Rotation.RIGHT, ready.rotateClockwise(PlayerSide.BOTTOM).bottomPlayer().activePiece().rotation());
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
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM),
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                config,
                TetrisGameStatus.RUNNING).spawnPiece(PlayerSide.BOTTOM, PieceShape.standardShape(PieceType.O));

        TetrisGameState next = state.applyGravity(PlayerSide.BOTTOM);

        assertEquals(0, state.bottomPlayer().activePiece().position().column());
        assertEquals(1, next.bottomPlayer().activePiece().position().column());
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
    void itemBagIncludesTeleporterObject() {
        Set<TetrisItemType> drawnTypes = drawFullBag(new TetrisItemBag(), new Random(7));

        assertTrue(drawnTypes.contains(TetrisItemType.TELEPORT_SWAP));
    }

    private static Set<TetrisItemType> drawFullBag(TetrisItemBag bag, Random random) {
        Set<TetrisItemType> drawnTypes = EnumSet.noneOf(TetrisItemType.class);
        for (int draw = 0; draw < TetrisItemType.values().length; draw++) {
            drawnTypes.add(bag.next(random));
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
}
