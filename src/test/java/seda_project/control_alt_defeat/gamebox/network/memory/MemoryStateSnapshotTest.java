package seda_project.control_alt_defeat.gamebox.network.memory;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryStateSnapshotTest {

    @Test
    void snapshotRoundTripsBoardScoresTurnAndGameOverState() {
        GameModel state = new GameModel(2, 2, 2, 2, List.of("A:B", "A:B", "C", "C"), 0);

        state.selectCard(0);
        state.selectCard(1);
        state.selectCard(2);
        state.selectCard(3);

        GameModel copy = MemoryStateSnapshot.deserialize(MemoryStateSnapshot.serialize(state));

        assertEquals(state.getK(), copy.getK());
        assertEquals(state.getN(), copy.getN());
        assertEquals(state.getRows(), copy.getRows());
        assertEquals(state.getCols(), copy.getCols());
        assertEquals(state.getCurrentPlayer(), copy.getCurrentPlayer());
        assertEquals(state.getScore(0), copy.getScore(0));
        assertEquals(state.getScore(1), copy.getScore(1));
        assertEquals(state.isGameOver(), copy.isGameOver());
        assertEquals(state.getSymbolOrder(), copy.getSymbolOrder());
        assertTrue(copy.getCard(0).isMatched());
        assertTrue(copy.getCard(3).isFaceUp());
    }

    @Test
    void snapshotRoundTripsOpenMismatchBeforeHostClosesIt() {
        GameModel state = new GameModel(2, 2, 2, 2, List.of("A", "B", "A", "B"), 1);

        state.selectCard(0);
        state.selectCard(1);

        GameModel copy = MemoryStateSnapshot.deserialize(MemoryStateSnapshot.serialize(state));

        assertEquals(1, copy.getCurrentPlayer());
        assertTrue(copy.getCard(0).isFaceUp());
        assertTrue(copy.getCard(1).isFaceUp());
        assertFalse(copy.getCard(0).isMatched());
        assertFalse(copy.getCard(1).isMatched());
    }

    @Test
    void malformedSnapshotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MemoryStateSnapshot.deserialize("bad"));
    }
}
