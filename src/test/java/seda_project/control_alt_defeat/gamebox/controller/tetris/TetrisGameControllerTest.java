package seda_project.control_alt_defeat.gamebox.controller.tetris;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.tetris.BoardPosition;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoard;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisBoardObject;
import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisPlayerState;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.GravityDirection;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.PlayerSide;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisCell;
import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static TetrisBoard filledBoard() {
        TetrisBoard board = new TetrisBoard();
        for (int row = 0; row < board.rows(); row++) {
            for (int column = 0; column < board.columns(); column++) {
                board = board.withCell(new BoardPosition(row, column), TetrisCell.FILLED);
            }
        }
        return board;
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
