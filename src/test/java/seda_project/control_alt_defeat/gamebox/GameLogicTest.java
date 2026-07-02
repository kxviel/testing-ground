package seda_project.control_alt_defeat.gamebox;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.Card;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GameLogicTest {

    @Test
    void testFR_INPUT01_kBelowOneGivesNoVariants() {
        assertTrue(BoardVariant.computeVariants(0).isEmpty());
    }

    @Test
    void testFR_INPUT01_kAbove45GivesNoVariants() {
        assertTrue(BoardVariant.computeVariants(46).isEmpty());
    }

    @Test
    void testFR_INPUT01_k1HasThreeVariants() {
        assertEquals(3, BoardVariant.computeVariants(1).size());
    }

    @Test
    void testFR_INPUT01_k2HasThreeVariants() {
        assertEquals(3, BoardVariant.computeVariants(2).size());
    }

    @Test
    void testFR_INPUT01_k45HasOneVariant() {
        List<BoardVariant> v = BoardVariant.computeVariants(45);
        assertEquals(1, v.size());
        assertEquals(45, v.get(0).totalCards);
    }

    @Test
    void testFR_INPUT02_variantTotalCardsDivisibleByK() {
        for (int k = 1; k <= 45; k++) {
            for (BoardVariant v : BoardVariant.computeVariants(k)) {
                assertEquals(0, v.totalCards % k);
            }
        }
    }

    @Test
    void testFR_INPUT02_variantTotalCardsNotExceed45() {
        for (int k = 1; k <= 45; k++) {
            for (BoardVariant v : BoardVariant.computeVariants(k)) {
                assertTrue(v.totalCards <= 45);
            }
        }
    }

    @Test
    void testBoardDimensionsProductEqualsTotal() {
        for (int k = 1; k <= 45; k++) {
            for (BoardVariant v : BoardVariant.computeVariants(k)) {
                assertEquals(v.totalCards, v.rows * v.cols);
            }
        }
    }

    @Test
    void testVariantDifficultiesOrder() {
        List<BoardVariant> v = BoardVariant.computeVariants(2);
        assertEquals("Large Board", v.get(0).difficulty);
        assertEquals("Medium Board", v.get(1).difficulty);
        assertEquals("Small Board", v.get(2).difficulty);
    }

    @Test
    void testFR_GAME02_initialState() {
        GameModel m = new GameModel(2, 3, 2, 3);
        assertEquals(0, m.getScore(0));
        assertEquals(0, m.getScore(1));
        assertEquals(0, m.getCurrentPlayer());
        assertFalse(m.isGameOver());
        for (Card c : m.getCards()) {
            assertFalse(c.isFaceUp());
            assertFalse(c.isMatched());
        }
    }

    @Test
    void testFR_GAME03_deckHasKCopiesOfEachSymbol() {
        int k = 3, n = 4;
        GameModel m = new GameModel(k, n, 3, 4);
        Map<String, Integer> counts = new HashMap<>();
        for (Card c : m.getCards()) {
            counts.merge(c.getSymbol(), 1, Integer::sum);
        }
        assertEquals(n, counts.size());
        for (int cnt : counts.values()) {
            assertEquals(k, cnt);
        }
    }

    @Test
    void testFR_GAME04_cannotOpenMoreThanKCards() {
        List<String> syms = List.of("A", "B", "A", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        GameModel.SelectResult r1 = m.selectCard(0);
        assertEquals(GameModel.SelectResult.OPENED, r1);
        GameModel.SelectResult r2 = m.selectCard(1);
        assertTrue(r2 == GameModel.SelectResult.RESOLVED_MATCH
                || r2 == GameModel.SelectResult.RESOLVED_MISMATCH);
    }

    @Test
    void testFR_GAME05_attemptResolvedAfterNthCard() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        GameModel.SelectResult r = m.selectCard(1);
        assertNotEquals(GameModel.SelectResult.OPENED, r);
        assertNotEquals(GameModel.SelectResult.IGNORED, r);
    }

    @Test
    void testFR_GAME06_matchScoresOnePoint() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        assertEquals(1, m.getScore(0));
        assertEquals(0, m.getScore(1));
    }

    @Test
    void testFR_GAME07_turnStaysOnMatch() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        assertEquals(0, m.getCurrentPlayer());
    }

    @Test
    void testFR_GAME07_turnSwitchesOnMismatch() {
        List<String> syms = List.of("A", "B", "A", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        m.closeOpenCards();
        assertEquals(1, m.getCurrentPlayer());
    }

    @Test
    void testFR_GAME08_mismatchedCardsClosedAfterDelay() {
        List<String> syms = List.of("A", "B", "A", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        m.closeOpenCards();
        assertFalse(m.getCard(0).isFaceUp());
        assertFalse(m.getCard(1).isFaceUp());
    }

    @Test
    void testFR_GAME09_ignoreAlreadyOpenCard() {
        GameModel m = new GameModel(2, 3, 2, 3);
        m.selectCard(0);
        assertEquals(GameModel.SelectResult.IGNORED, m.selectCard(0));
    }

    @Test
    void testFR_GAME09_ignoreMatchedCard() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        assertEquals(GameModel.SelectResult.IGNORED, m.selectCard(0));
    }

    @Test
    void testFR_GAME10_gameOverWhenAllMatched() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        m.selectCard(2);
        m.selectCard(3);
        assertTrue(m.isGameOver());
    }

    @Test
    void testFR_GAME11_winnerHigherScore() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        m.selectCard(2);
        m.selectCard(3);
        assertEquals(2, m.getScore(0));
        assertEquals(0, m.getScore(1));
        assertEquals(0, m.getWinner());
    }

    @Test
    void testFR_GAME11_drawOnEqualScores() {
        List<String> syms = List.of("A", "A", "B", "B", "C", "C", "D", "D");
        GameModel m = new GameModel(2, 4, 2, 4, syms, 0);
        m.selectCard(0);
        m.selectCard(1);
        m.selectCard(2);
        m.selectCard(3);
        m.selectCard(4);
        m.selectCard(6);
        m.closeOpenCards();
        m.selectCard(4);
        m.selectCard(5);
        m.selectCard(6);
        m.selectCard(7);
        assertTrue(m.isGameOver());
        assertEquals(-1, m.getWinner());
    }

    @Test
    void testFR_GAME12_restartResetsState() {
        List<String> syms = List.of("A", "A", "B", "B");
        GameModel played = new GameModel(2, 2, 2, 2, syms, 0);
        played.selectCard(0);
        played.selectCard(1);

        GameModel restarted = new GameModel(played.getK(), played.getN(),
                played.getRows(), played.getCols());
        assertEquals(0, restarted.getScore(0));
        assertEquals(0, restarted.getScore(1));
        assertEquals(0, restarted.getCurrentPlayer());
        assertFalse(restarted.isGameOver());
        for (Card c : restarted.getCards()) {
            assertFalse(c.isFaceUp());
        }
    }

    @Test
    void testFR_NET03_turnTracking() {
        List<String> syms = List.of("A", "B", "A", "B");
        GameModel m = new GameModel(2, 2, 2, 2, syms, 0);
        assertEquals(0, m.getCurrentPlayer());
        m.selectCard(0);
        m.selectCard(1);
        m.closeOpenCards();
        assertEquals(1, m.getCurrentPlayer());
    }
}
