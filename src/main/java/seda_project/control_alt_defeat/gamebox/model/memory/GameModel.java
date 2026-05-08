package seda_project.control_alt_defeat.gamebox.model.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Contains the complete rules and mutable state for one memory game.
public class GameModel {

    private static final String[] SYMBOL_POOL = {
            "🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🍑", "🍍", "🥝", "🍉",
            "🌸", "🌻", "🌺", "🌹", "🌷", "🍄", "🌈", "⭐", "🌟", "💫",
            "🔥", "❄️", "🎯", "🎲", "🎸", "🎺", "🎻", "🎹", "🥁", "🎮",
            "🚀", "🌙", "☀️", "🌊", "⚡", "🦋", "🐝", "🦜", "🦊", "🐬",
            "🍕", "🍦", "🎂", "🧁", "🍫"
    };

    private final int k;
    private final int n;
    private final int rows;
    private final int cols;
    private final List<Card> cards;

    private int currentPlayer;
    private int[] scores;
    private List<Integer> openedThisTurn;
    private boolean gameOver;
    private int remainingCards;

    /**
     * Creates a shuffled game from a precomputed board variant.
     *
     * @param variant selected board size and k-tuple configuration
     */
    public GameModel(BoardVariant variant) {
        this(variant.k, variant.n, variant.rows, variant.cols);
    }

    /**
     * Creates a new shuffled game.
     *
     * @param k    number of identical cards required for a match
     * @param n    number of distinct symbol groups
     * @param rows number of board rows
     * @param cols number of board columns
     */
    public GameModel(int k, int n, int rows, int cols) {
        this.k = k;
        this.n = n;
        this.rows = rows;
        this.cols = cols;
        this.scores = new int[] { 0, 0 };
        this.currentPlayer = 0;
        this.openedThisTurn = new ArrayList<>();
        this.gameOver = false;

        int total = n * k;
        this.remainingCards = total;
        List<String> symbols = new ArrayList<>(total);
        for (int i = 0; i < n; i++) {
            String sym = SYMBOL_POOL[i % SYMBOL_POOL.length];
            for (int j = 0; j < k; j++) {
                symbols.add(sym);
            }
        }
        Collections.shuffle(symbols);

        // Store shuffled symbols as stable card objects for the rest of the game.
        this.cards = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            cards.add(new Card(i, symbols.get(i)));
        }
    }

    /**
     * Creates a game with an already known card order.
     * <p>
     * Network clients use this constructor so their board exactly matches the
     * host's shuffled board.
     *
     * @param k              number of identical cards required for a match
     * @param n              number of distinct symbol groups
     * @param rows           number of board rows
     * @param cols           number of board columns
     * @param orderedSymbols symbols in board-index order
     * @param currentPlayer  player index whose turn starts the game
     */
    public GameModel(int k, int n, int rows, int cols, List<String> orderedSymbols, int currentPlayer) {
        this.k = k;
        this.n = n;
        this.rows = rows;
        this.cols = cols;
        this.scores = new int[] { 0, 0 };
        this.currentPlayer = currentPlayer;
        this.openedThisTurn = new ArrayList<>();
        this.gameOver = false;
        int total = orderedSymbols.size();
        this.remainingCards = total;
        this.cards = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            cards.add(new Card(i, orderedSymbols.get(i)));
        }
    }

    /**
     * @return number of identical cards required for one match
     */
    public int getK() {
        return k;
    }

    /**
     * @return number of distinct symbol groups
     */
    public int getN() {
        return n;
    }

    /**
     * @return board row count
     */
    public int getRows() {
        return rows;
    }

    /**
     * @return board column count
     */
    public int getCols() {
        return cols;
    }

    /**
     * @return read-only view of all cards in board order
     */
    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    /**
     * Returns the card at one board position.
     *
     * @param idx zero-based board position
     * @return card at that position
     */
    public Card getCard(int idx) {
        return cards.get(idx);
    }

    /**
     * @return zero-based index of the player whose turn it is
     */
    public int getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * Returns a player's score.
     *
     * @param player zero-based player index
     * @return number of completed matches owned by the player
     */
    public int getScore(int player) {
        return scores[player];
    }

    /**
     * @return true once all cards have been matched
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * Attempts to select one card for the current turn.
     *
     * @param cardIdx zero-based board position
     * @return result describing whether the selection was ignored, opened a
     *         card, completed a match, or completed a mismatch
     */
    public SelectResult selectCard(int cardIdx) {
        if (gameOver)
            return SelectResult.IGNORED;
        Card card = cards.get(cardIdx);

        if (!card.isSelectable() || openedThisTurn.contains(cardIdx)) {
            return SelectResult.IGNORED;
        }

        card.setFaceUp(true);
        openedThisTurn.add(cardIdx);

        // Only resolve an attempt after the player has opened k cards.
        if (openedThisTurn.size() < k) {
            return SelectResult.OPENED;
        }

        return resolveAttempt();
    }

    private SelectResult resolveAttempt() {
        String sym = cards.get(openedThisTurn.get(0)).getSymbol();
        boolean match = true;
        for (int id : openedThisTurn) {
            if (!cards.get(id).getSymbol().equals(sym)) {
                match = false;
                break;
            }
        }

        if (match) {
            // A successful k-tuple stays face up and awards one point.
            for (int id : openedThisTurn) {
                cards.get(id).setMatched(true);
            }
            scores[currentPlayer]++;
            remainingCards -= k;
            openedThisTurn.clear();
            if (remainingCards == 0) {
                gameOver = true;
            }

            return SelectResult.RESOLVED_MATCH;
        } else {
            return SelectResult.RESOLVED_MISMATCH;
        }
    }

    /**
     * Closes the currently open unmatched cards and advances to the other
     * player.
     */
    public void closeOpenCards() {
        for (int id : openedThisTurn) {
            Card c = cards.get(id);
            if (!c.isMatched())
                c.setFaceUp(false);
        }
        openedThisTurn.clear();
        currentPlayer = 1 - currentPlayer;
    }

    /**
     * @return winning player index, or {@code -1} for a draw
     */
    public int getWinner() {
        if (scores[0] > scores[1])
            return 0;
        if (scores[1] > scores[0])
            return 1;
        return -1;
    }

    /**
     * @return current card symbols in board order for network initialization
     */
    public List<String> getSymbolOrder() {
        List<String> syms = new ArrayList<>();
        for (Card c : cards)
            syms.add(c.getSymbol());
        return syms;
    }

    public enum SelectResult {
        IGNORED,
        OPENED,
        RESOLVED_MATCH,
        RESOLVED_MISMATCH
    }
}