package seda_project.control_alt_defeat.gamebox.model.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the Memory game model.
 *
 * <p>Covers: Card state machine, GameModel lifecycle (select / resolve / close / switch),
 * winner logic, snapshot constructors, BoardVariant geometry helpers, and all
 * validation guards in {@link GameModel} and {@link BoardVariant}.
 */
class MemoryGameModelTest {

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /** Symbols long enough to fill a k=2, n=4 board (8 cards). */
    private static final String SYM = "card_faces/angry.png";

    private static List<String> symbols(int count) {
        return Collections.nCopies(count, SYM);
    }

    /** Returns the canonical 2-match / 4-pair board used in most tests. */
    private static GameModel twoByFourBoard() {
        // k=2 copies, n=4 pairs → 8 cards, 2×4 grid
        return new GameModel(2, 4, 2, 4, symbols(8), 0);
    }

    // ─────────────────────────────────────────────────────────────
    // Card – unit tests
    // ─────────────────────────────────────────────────────────────

    @Test
    void card_initialState_isHiddenAndUnmatched() {
        Card card = new Card(0, SYM);
        assertFalse(card.isFaceUp());
        assertFalse(card.isMatched());
        assertEquals(-1, card.getMatchedBy());
        assertTrue(card.isSelectable());
    }

    @Test
    void card_setFaceUp_doesNotMatchCard() {
        Card card = new Card(1, SYM);
        card.setFaceUp(true);
        assertTrue(card.isFaceUp());
        assertFalse(card.isMatched());
        assertFalse(card.isSelectable()); // face-up but unmatched → not selectable
    }

    @Test
    void card_setMatchedBy_setsMatchedAndFaceUp() {
        Card card = new Card(2, SYM);
        card.setMatchedBy(1);
        assertTrue(card.isMatched());
        assertTrue(card.isFaceUp());
        assertEquals(1, card.getMatchedBy());
        assertFalse(card.isSelectable());
    }

    @Test
    void card_setMatched_false_clearsOwner() {
        Card card = new Card(3, SYM);
        card.setMatchedBy(0);
        card.setMatched(false);
        assertFalse(card.isMatched());
        assertEquals(-1, card.getMatchedBy());
    }

    @Test
    void card_setFaceUpFalse_isSelectableAgain() {
        Card card = new Card(4, SYM);
        card.setFaceUp(true);
        card.setFaceUp(false);
        assertFalse(card.isFaceUp());
        assertTrue(card.isSelectable());
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – construction guards
    // ─────────────────────────────────────────────────────────────

    @Test
    void constructor_invalidK_zero_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(0, 4, 2, 4, symbols(8), 0));
    }

    @Test
    void constructor_invalidN_zero_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 0, 1, 1, symbols(0), 0));
    }

    @Test
    void constructor_gridMismatch_throws() {
        // k*n = 8 but rows*cols = 6
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 4, 2, 3, symbols(8), 0));
    }

    @Test
    void constructor_symbolCountMismatch_throws() {
        // k*n = 8 but only 6 symbols provided
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 4, 2, 4, symbols(6), 0));
    }

    @Test
    void constructor_nullSymbols_throws() {
        assertThrows(NullPointerException.class,
                () -> new GameModel(2, 4, 2, 4, null, 0));
    }

    @Test
    void constructor_blankSymbol_throws() {
        List<String> bad = new ArrayList<>(symbols(8));
        bad.set(3, "");
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 4, 2, 4, bad, 0));
    }

    @Test
    void constructor_tooLongSymbol_throws() {
        List<String> bad = new ArrayList<>(symbols(8));
        bad.set(0, "x".repeat(129));
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 4, 2, 4, bad, 0));
    }

    @Test
    void constructor_invalidCurrentPlayer_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 4, 2, 4, symbols(8), 2));
    }

    @Test
    void constructor_negativeCurrentPlayer_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(2, 4, 2, 4, symbols(8), -1));
    }

    @Test
    void constructor_validState_restoresOpenedCards() {
        List<Boolean> faceUp = new ArrayList<>(Collections.nCopies(8, false));
        faceUp.set(0, true); // one card open
        List<Boolean> matched = Collections.nCopies(8, false);
        List<Integer> matchedBy = Collections.nCopies(8, -1);

        GameModel model = new GameModel(
                2, 4, 2, 4, symbols(8), 0, 0, 0, false, faceUp, matched, matchedBy);

        assertTrue(model.getCard(0).isFaceUp());
        assertFalse(model.getCard(1).isFaceUp());
        assertFalse(model.isGameOver());
    }

    @Test
    void constructor_inconsistentScoreVsMatchedCards_throws() {
        List<Boolean> faceUp = Collections.nCopies(8, true);
        List<Boolean> matched = Collections.nCopies(8, true);
        // score0 = 1 but 8 matched cards belong to owner 0 → expects score0 = 4
        List<Integer> matchedBy = Collections.nCopies(8, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(
                        2, 4, 2, 4, symbols(8), 0, 1, 0, true, faceUp, matched, matchedBy));
    }

    @Test
    void constructor_inconsistentGameOverFlagWhenNotAllMatched_throws() {
        List<Boolean> faceUp = Collections.nCopies(8, false);
        List<Boolean> matched = Collections.nCopies(8, false);
        List<Integer> matchedBy = Collections.nCopies(8, -1);
        assertThrows(IllegalArgumentException.class,
                () -> new GameModel(
                        2, 4, 2, 4, symbols(8), 0, 0, 0, true /*gameOver but cards remain*/,
                        faceUp, matched, matchedBy));
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – accessors
    // ─────────────────────────────────────────────────────────────

    @Test
    void accessors_returnsCorrectDimensions() {
        GameModel model = twoByFourBoard();
        assertEquals(2, model.getK());
        assertEquals(4, model.getN());
        assertEquals(2, model.getRows());
        assertEquals(4, model.getCols());
    }

    @Test
    void accessors_initialScoresAreZero() {
        GameModel model = twoByFourBoard();
        assertEquals(0, model.getScore(0));
        assertEquals(0, model.getScore(1));
    }

    @Test
    void accessors_initialPlayerIsZero() {
        GameModel model = twoByFourBoard();
        assertEquals(0, model.getCurrentPlayer());
    }

    @Test
    void accessors_cardListSizeEqualsKTimesN() {
        GameModel model = twoByFourBoard();
        assertEquals(8, model.getCards().size());
    }

    @Test
    void accessors_getCardsIsUnmodifiable() {
        GameModel model = twoByFourBoard();
        assertThrows(UnsupportedOperationException.class,
                () -> model.getCards().add(null));
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – selectCard basics
    // ─────────────────────────────────────────────────────────────

    @Test
    void selectCard_firstCard_returnsOpened() {
        GameModel model = twoByFourBoard();
        assertEquals(GameModel.SelectResult.OPENED, model.selectCard(0));
        assertTrue(model.getCard(0).isFaceUp());
    }

    @Test
    void selectCard_outOfBounds_returnsIgnored() {
        GameModel model = twoByFourBoard();
        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(-1));
        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(8));
    }

    @Test
    void selectCard_sameCardTwice_returnsIgnored() {
        GameModel model = twoByFourBoard();
        model.selectCard(0);
        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(0));
    }

    @Test
    void selectCard_matchedCard_returnsIgnored() {
        GameModel model = twoByFourBoard();
        // Select two cards → they all share the same symbol → RESOLVED_MATCH
        model.selectCard(0);
        model.selectCard(1);
        // Now card 0 is matched; re-selecting it should be IGNORED
        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(0));
    }

    @Test
    void selectCard_whenGameOver_returnsIgnored() {
        // Complete a full game first
        GameModel model = twoByFourBoard(); // 4 pairs, k=2
        for (int i = 0; i < 8; i += 2) {
            model.selectCard(i);
            model.selectCard(i + 1);
        }
        assertTrue(model.isGameOver());
        assertEquals(GameModel.SelectResult.IGNORED, model.selectCard(0));
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – matching & mismatch flow (all symbols identical)
    // ─────────────────────────────────────────────────────────────

    @Test
    void selectCard_kThCard_matchingSymbols_returnsResolvedMatch() {
        GameModel model = twoByFourBoard();
        model.selectCard(0);
        GameModel.SelectResult result = model.selectCard(1);
        assertEquals(GameModel.SelectResult.RESOLVED_MATCH, result);
        assertTrue(model.getCard(0).isMatched());
        assertTrue(model.getCard(1).isMatched());
        assertEquals(1, model.getScore(0));
    }

    @Test
    void selectCard_matchDoesNotSwitchPlayer() {
        GameModel model = twoByFourBoard();
        model.selectCard(0);
        model.selectCard(1);
        // After a match the player keeps the turn (they get to go again)
        assertEquals(0, model.getCurrentPlayer());
    }

    @Test
    void selectCard_mismatch_setsResultButKeepsCardsOpen() {
        // Use different symbols so cards do NOT match
        List<String> syms = new ArrayList<>();
        syms.add("card_faces/angry.png");
        syms.add("card_faces/troll.png");
        // pad to k*n=4, rows*cols=2*2
        syms.add("card_faces/angry.png");
        syms.add("card_faces/troll.png");
        GameModel model = new GameModel(2, 2, 2, 2, syms, 0);

        model.selectCard(0); // angry
        GameModel.SelectResult result = model.selectCard(1); // troll → mismatch
        assertEquals(GameModel.SelectResult.RESOLVED_MISMATCH, result);
        // Cards should still be face-up until closeOpenCards() is called
        assertTrue(model.getCard(0).isFaceUp());
        assertTrue(model.getCard(1).isFaceUp());
        assertFalse(model.getCard(0).isMatched());
    }

    @Test
    void closeOpenCards_afterMismatch_flipsFaceDownAndSwitchesPlayer() {
        List<String> syms = List.of(
                "card_faces/angry.png", "card_faces/troll.png",
                "card_faces/angry.png", "card_faces/troll.png");
        GameModel model = new GameModel(2, 2, 2, 2, syms, 0);

        model.selectCard(0);
        model.selectCard(1);
        model.closeOpenCards();

        assertFalse(model.getCard(0).isFaceUp());
        assertFalse(model.getCard(1).isFaceUp());
        assertEquals(1, model.getCurrentPlayer());
    }

    @Test
    void closeOpenCards_afterMatch_doesNotFlipMatched() {
        GameModel model = twoByFourBoard(); // all same symbol
        model.selectCard(0);
        model.selectCard(1); // match
        model.closeOpenCards();

        // Matched cards stay face-up
        assertTrue(model.getCard(0).isFaceUp());
        assertTrue(model.getCard(1).isFaceUp());
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – game-over detection
    // ─────────────────────────────────────────────────────────────

    @Test
    void gameOver_setAfterAllPairsMatched() {
        GameModel model = twoByFourBoard(); // 4 pairs
        assertFalse(model.isGameOver());
        for (int i = 0; i < 8; i += 2) {
            model.selectCard(i);
            model.selectCard(i + 1);
        }
        assertTrue(model.isGameOver());
    }

    @Test
    void gameOver_notSetAfterPartialMatch() {
        GameModel model = twoByFourBoard();
        model.selectCard(0);
        model.selectCard(1); // 1 of 4 matched
        assertFalse(model.isGameOver());
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – winner logic
    // ─────────────────────────────────────────────────────────────

    @Test
    void getWinner_player0WinsWhenHigherScore() {
        // Make player 0 score 3, player 1 score 1 via snapshot constructor
        // k=2, n=4, rows=2, cols=4 → 8 cards
        // All matched: 6 by player 0 (→ score 3), 2 by player 1 (→ score 1)
        List<String> syms = symbols(8);
        List<Boolean> faceUp = Collections.nCopies(8, true);
        List<Boolean> matched = Collections.nCopies(8, true);
        List<Integer> owners = new ArrayList<>(Collections.nCopies(6, 0));
        owners.add(1);
        owners.add(1);

        GameModel model = new GameModel(
                2, 4, 2, 4, syms, 0, 3, 1, true, faceUp, matched, owners);

        assertEquals(0, model.getWinner());
    }

    @Test
    void getWinner_player1WinsWhenHigherScore() {
        List<String> syms = symbols(8);
        List<Boolean> faceUp = Collections.nCopies(8, true);
        List<Boolean> matched = Collections.nCopies(8, true);
        List<Integer> owners = new ArrayList<>(Collections.nCopies(2, 0));
        owners.addAll(Collections.nCopies(6, 1));

        GameModel model = new GameModel(
                2, 4, 2, 4, syms, 0, 1, 3, true, faceUp, matched, owners);

        assertEquals(1, model.getWinner());
    }

    @Test
    void getWinner_tieReturnsMinus1() {
        List<String> syms = symbols(8);
        List<Boolean> faceUp = Collections.nCopies(8, true);
        List<Boolean> matched = Collections.nCopies(8, true);
        List<Integer> owners = new ArrayList<>(Collections.nCopies(4, 0));
        owners.addAll(Collections.nCopies(4, 1));

        GameModel model = new GameModel(
                2, 4, 2, 4, syms, 0, 2, 2, true, faceUp, matched, owners);

        assertEquals(-1, model.getWinner());
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – k=3 triplet matching
    // ─────────────────────────────────────────────────────────────

    @Test
    void tripletMatch_k3_requiresThreeCardsToResolve() {
        // k=3 copies, n=3 triplets → 9 cards, 3×3 grid; all same symbol
        GameModel model = new GameModel(3, 3, 3, 3, symbols(9), 0);

        assertEquals(GameModel.SelectResult.OPENED, model.selectCard(0));
        assertEquals(GameModel.SelectResult.OPENED, model.selectCard(1));
        assertEquals(GameModel.SelectResult.RESOLVED_MATCH, model.selectCard(2));
        assertEquals(1, model.getScore(0));
    }

    @Test
    void tripletMatch_k3_partialSelection_doesNotResolve() {
        GameModel model = new GameModel(3, 3, 3, 3, symbols(9), 0);

        assertEquals(GameModel.SelectResult.OPENED, model.selectCard(0));
        assertEquals(GameModel.SelectResult.OPENED, model.selectCard(1));
        // Two cards opened but k=3 → no resolution yet
        assertFalse(model.getCard(0).isMatched());
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel – isSupportedSymbol
    // ─────────────────────────────────────────────────────────────

    @Test
    void isSupportedSymbol_recognisesKnownSymbols() {
        assertTrue(GameModel.isSupportedSymbol("card_faces/angry.png"));
        assertTrue(GameModel.isSupportedSymbol("card_faces/super-mario.png"));
    }

    @Test
    void isSupportedSymbol_rejectsUnknownAndNull() {
        assertFalse(GameModel.isSupportedSymbol("card_faces/unknown.png"));
        assertFalse(GameModel.isSupportedSymbol(null));
        assertFalse(GameModel.isSupportedSymbol(""));
    }

    // ─────────────────────────────────────────────────────────────
    // BoardVariant – construction
    // ─────────────────────────────────────────────────────────────

    @Test
    void boardVariant_validKAndN_createsCorrectly() {
        BoardVariant v = new BoardVariant(2, 8, "Easy");
        assertEquals(2, v.k);
        assertEquals(8, v.n);
        assertEquals(16, v.totalCards);
        assertEquals("Easy", v.difficulty);
    }

    @Test
    void boardVariant_invalidK_zero_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BoardVariant(0, 10, "x"));
    }

    @Test
    void boardVariant_totalExceedsMax_throws() {
        // k=3, n=16 → 48 > MAX_CARDS (45)
        assertThrows(IllegalArgumentException.class, () -> new BoardVariant(3, 16, "x"));
    }

    @Test
    void boardVariant_nullDifficulty_usesDefaultLabel() {
        BoardVariant v = new BoardVariant(2, 4, null);
        assertEquals("Custom Board", v.difficulty);
    }

    @Test
    void boardVariant_blankDifficulty_usesDefaultLabel() {
        BoardVariant v = new BoardVariant(2, 4, "   ");
        assertEquals("Custom Board", v.difficulty);
    }

    // ─────────────────────────────────────────────────────────────
    // BoardVariant – bestDimensions
    // ─────────────────────────────────────────────────────────────

    @Test
    void bestDimensions_16cards_returnsBalancedGrid() {
        int[] dims = BoardVariant.bestDimensions(16);
        assertEquals(16, dims[0] * dims[1]);
        // 4×4 is perfectly square and within 2.5 aspect ratio
        assertEquals(4, dims[0]);
        assertEquals(4, dims[1]);
    }

    @Test
    void bestDimensions_invalidCount_throws() {
        assertThrows(IllegalArgumentException.class, () -> BoardVariant.bestDimensions(0));
        assertThrows(IllegalArgumentException.class,
                () -> BoardVariant.bestDimensions(BoardVariant.MAX_CARDS + 1));
    }

    @Test
    void bestDimensions_primeCount_stillReturnsValidGrid() {
        // 7 cards → only factor pair is 1×7; aspect = 7.0 > 2.5 so fallback used
        int[] dims = BoardVariant.bestDimensions(7);
        assertEquals(7, dims[0] * dims[1]);
    }

    // ─────────────────────────────────────────────────────────────
    // BoardVariant – computeVariants
    // ─────────────────────────────────────────────────────────────

    @Test
    void computeVariants_standardK2_returnsThreeVariants() {
        List<BoardVariant> variants = BoardVariant.computeVariants(2);
        // Should have Large, Medium, Small
        assertFalse(variants.isEmpty());
        assertTrue(variants.size() <= 3);
        variants.forEach(v -> assertEquals(2, v.k));
    }

    @Test
    void computeVariants_kTooLarge_returnsEmpty() {
        // k=46 → maxN = 45/46 = 0 → empty
        List<BoardVariant> variants = BoardVariant.computeVariants(46);
        assertTrue(variants.isEmpty());
    }

    @Test
    void computeVariants_kZero_returnsEmpty() {
        assertTrue(BoardVariant.computeVariants(0).isEmpty());
    }

    @Test
    void computeVariants_allVariantsRespectMaxCards() {
        for (int k = 1; k <= 10; k++) {
            for (BoardVariant v : BoardVariant.computeVariants(k)) {
                assertTrue(v.totalCards <= BoardVariant.MAX_CARDS,
                        "Variant exceeds MAX_CARDS: " + v);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GameModel via BoardVariant constructor
    // ─────────────────────────────────────────────────────────────

    @Test
    void gameModel_fromBoardVariant_initialises() {
        BoardVariant variant = new BoardVariant(2, 6, "Medium");
        GameModel model = new GameModel(variant);
        assertEquals(12, model.getCards().size());
        assertFalse(model.isGameOver());
    }

    @Test
    void gameModel_fromNullVariant_throws() {
        assertThrows(NullPointerException.class, () -> new GameModel((BoardVariant) null));
    }

    // ─────────────────────────────────────────────────────────────
    // Score accumulation across multiple turns
    // ─────────────────────────────────────────────────────────────

    @Test
    void scoreAccumulates_acrossConsecutiveMatches() {
        GameModel model = twoByFourBoard(); // 4 pairs, all same symbol
        model.selectCard(0);
        model.selectCard(1); // match 1 for player 0
        model.selectCard(2);
        model.selectCard(3); // match 2 for player 0
        assertEquals(2, model.getScore(0));
        assertEquals(0, model.getScore(1));
    }

    @Test
    void playerSwitchesAfterMismatch() {
        List<String> mixed = List.of(
                "card_faces/angry.png", "card_faces/troll.png",
                "card_faces/angry.png", "card_faces/troll.png");
        GameModel model = new GameModel(2, 2, 2, 2, mixed, 0);

        assertEquals(0, model.getCurrentPlayer());
        model.selectCard(0); // angry
        model.selectCard(1); // troll → mismatch
        model.closeOpenCards();
        assertEquals(1, model.getCurrentPlayer());
    }
}
