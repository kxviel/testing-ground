package seda_project.control_alt_defeat.gamebox.controller.tetris;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameConfig;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameState;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisGameStatus;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetrisGameControllerTest {

    @Test
    void findSpawnObjectRejectsUnsupportedTileEvenWhenItIsTheOnlyFreeCell() {
        BoardPosition target = new BoardPosition(8, 5);
        TetrisBoard board = filledBoard().withCell(target, TetrisCell.EMPTY);
        TetrisPlayerState player = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM).withBoard(board);

        TetrisBoardObject object = TetrisGameController.findSpawnObject(
                player,
                TetrisItemType.SLOW_SELF,
                GravityDirection.DOWN,
                new FixedRandom(target.row(), target.column()),
                5);

        assertNull(object);
    }

    @Test
    void findSpawnObjectAcceptsSupportedTileWithClearApproach() {
        BoardPosition target = new BoardPosition(18, 5);
        TetrisBoard board = filledBoard();
        for (int row = 0; row <= target.row(); row++) {
            board = board.withCell(new BoardPosition(row, target.column()), TetrisCell.EMPTY);
        }
        TetrisPlayerState player = TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM).withBoard(board);

        TetrisBoardObject object = TetrisGameController.findSpawnObject(
                player,
                TetrisItemType.SLOW_SELF,
                GravityDirection.DOWN,
                new FixedRandom(target.row(), target.column()),
                1);

        assertEquals(new TetrisBoardObject(TetrisItemType.SLOW_SELF, target), object);
    }

    @Test
    void eligibleObjectTypesIncludesEveryObjectWhenOpponentIsPlaying() {
        TetrisGameState state = new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM),
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        assertEquals(EnumSet.allOf(TetrisItemType.class), TetrisGameController.eligibleObjectTypes(state, PlayerSide.BOTTOM));
        assertEquals(EnumSet.allOf(TetrisItemType.class), TetrisGameController.eligibleObjectTypes(state, PlayerSide.TOP));
    }

    @Test
    void eligibleObjectTypesExcludesOpponentObjectsAfterTopLoses() {
        TetrisGameState state = new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM),
                TetrisPlayerState.create("Top", PlayerSide.TOP).lost(),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        Set<TetrisItemType> eligibleTypes = TetrisGameController.eligibleObjectTypes(state, PlayerSide.BOTTOM);

        assertFalse(eligibleTypes.isEmpty());
        assertTrue(eligibleTypes.stream().noneMatch(TetrisItemType::requiresActiveOpponent));
        assertEquals(selfContainedObjectTypes(), eligibleTypes);
    }

    @Test
    void eligibleObjectTypesExcludesOpponentObjectsAfterBottomLoses() {
        TetrisGameState state = new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM).lost(),
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        Set<TetrisItemType> eligibleTypes = TetrisGameController.eligibleObjectTypes(state, PlayerSide.TOP);

        assertFalse(eligibleTypes.isEmpty());
        assertTrue(eligibleTypes.stream().noneMatch(TetrisItemType::requiresActiveOpponent));
        assertEquals(selfContainedObjectTypes(), eligibleTypes);
    }

    @Test
    void eligibleObjectTypesIsEmptyForLostSpawner() {
        TetrisGameState state = new TetrisGameState(
                TetrisPlayerState.create("Bottom", PlayerSide.BOTTOM).lost(),
                TetrisPlayerState.create("Top", PlayerSide.TOP),
                TetrisGameConfig.defaultConfig(),
                TetrisGameStatus.RUNNING);

        assertTrue(TetrisGameController.eligibleObjectTypes(state, PlayerSide.BOTTOM).isEmpty());
    }

    private static TetrisBoard filledBoard() {
        TetrisBoard board = new TetrisBoard();
        for (int row = 0; row < board.rows(); row++) {
            for (int column = 0; column < board.columns(); column++) {
                board = board.withCell(new BoardPosition(row, column), TetrisCell.FILLED);
            }
        }
        return board;
    }

    private static Set<TetrisItemType> selfContainedObjectTypes() {
        return EnumSet.of(
                TetrisItemType.SLOW_SELF,
                TetrisItemType.ROTATION_DELAY_SELF,
                TetrisItemType.EXPLODE_RADIUS,
                TetrisItemType.EXPLODE_BELOW);
    }

    private static final class FixedRandom extends Random {
        private final int[] values;
        private int index;

        private FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = values[index % values.length];
            index++;
            return Math.floorMod(value, bound);
        }
    }
}
