package seda_project.control_alt_defeat.gamebox.model.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class GameModel {

    private static final String[] SYMBOL_POOL = {
            "card_faces/icons8-ahri-100.svg",
            "card_faces/icons8-bad-piggies-100.svg",
            "card_faces/icons8-best-terraria-100.svg",
            "card_faces/icons8-call-of-duty-warzone-100.svg",
            "card_faces/icons8-clubs-100.svg",
            "card_faces/icons8-color-by-number-100.svg",
            "card_faces/icons8-counter-strike-100.svg",
            "card_faces/icons8-diamonds-100.svg",
            "card_faces/icons8-elden-ring-100.svg",
            "card_faces/icons8-favorite-100.svg",
            "card_faces/icons8-five-nights-at-freddys-100.svg",
            "card_faces/icons8-greek-helmet-100.svg",
            "card_faces/icons8-mage-staff-100.svg",
            "card_faces/icons8-minecraft-logo-100.svg",
            "card_faces/icons8-minecraft-pig-100.svg",
            "card_faces/icons8-monster-face-100.svg",
            "card_faces/icons8-pixel-cat-100.svg",
            "card_faces/icons8-pixel-star-100.svg",
            "card_faces/icons8-scary-tree-100.svg",
            "card_faces/icons8-spades-100.svg",
            "card_faces/icons8-spartan-helmet-100.svg",
            "card_faces/icons8-super-mario-100.svg",
            "card_faces/icons8-ultra-ball-100.svg"
    };

    private final int k;
    private final int n;
    private final int rows;
    private final int cols;
    private final List<Card> cards;

    private int currentPlayer;
    private final int[] scores;
    private final List<Integer> openedThisTurn;
    private boolean gameOver;
    private int remainingCards;

    public GameModel(BoardVariant variant) {
        this(variant.k, variant.n, variant.rows, variant.cols);
    }

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
        List<String> symbols = IntStream.range(0, total)
                .mapToObj(i -> SYMBOL_POOL[(i / k) % SYMBOL_POOL.length])
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(symbols);

        this.cards = cardsFromSymbols(symbols);
    }

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
        this.cards = cardsFromSymbols(orderedSymbols);
    }

    public GameModel(
            int k,
            int n,
            int rows,
            int cols,
            List<String> orderedSymbols,
            int currentPlayer,
            int score0,
            int score1,
            boolean gameOver,
            List<Boolean> faceUp,
            List<Boolean> matched) {
        this(k, n, rows, cols, orderedSymbols, currentPlayer, score0, score1, gameOver, faceUp, matched,
                Collections.nCopies(orderedSymbols.size(), -1));
    }

    public GameModel(
            int k,
            int n,
            int rows,
            int cols,
            List<String> orderedSymbols,
            int currentPlayer,
            int score0,
            int score1,
            boolean gameOver,
            List<Boolean> faceUp,
            List<Boolean> matched,
            List<Integer> matchedBy) {
        if (orderedSymbols.size() != faceUp.size()
                || orderedSymbols.size() != matched.size()
                || orderedSymbols.size() != matchedBy.size()) {
            throw new IllegalArgumentException("Card state size must match symbol count.");
        }

        this.k = k;
        this.n = n;
        this.rows = rows;
        this.cols = cols;
        this.scores = new int[] { score0, score1 };
        this.currentPlayer = currentPlayer;
        this.openedThisTurn = new ArrayList<>();
        this.gameOver = gameOver;
        this.cards = cardsFromSymbols(orderedSymbols);

        IntStream.range(0, cards.size()).forEach(i -> {
            Card card = cards.get(i);
            if (matched.get(i)) {
                card.setMatchedBy(matchedBy.get(i));
            }
            card.setFaceUp(faceUp.get(i) || matched.get(i));
            if (card.isFaceUp() && !card.isMatched()) {
                openedThisTurn.add(i);
            }
        });
        this.remainingCards = (int) cards.stream()
                .filter(card -> !card.isMatched())
                .count();
    }

    public int getK() {
        return k;
    }

    public int getN() {
        return n;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public Card getCard(int idx) {
        return cards.get(idx);
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int getScore(int player) {
        return scores[player];
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public SelectResult selectCard(int cardIdx) {
        if (gameOver)
            return SelectResult.IGNORED;
        if (cardIdx < 0 || cardIdx >= cards.size())
            return SelectResult.IGNORED;

        Card card = cards.get(cardIdx);

        if (!card.isSelectable() || openedThisTurn.contains(cardIdx)) {
            return SelectResult.IGNORED;
        }

        card.setFaceUp(true);
        openedThisTurn.add(cardIdx);

        if (openedThisTurn.size() < k) {
            return SelectResult.OPENED;
        }

        return resolveAttempt();
    }

    private SelectResult resolveAttempt() {
        String sym = cards.get(openedThisTurn.get(0)).getSymbol();
        boolean match = openedThisTurn.stream()
                .map(cards::get)
                .map(Card::getSymbol)
                .allMatch(sym::equals);

        if (match) {
            openedThisTurn.forEach(id -> cards.get(id).setMatchedBy(currentPlayer));
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

    public void closeOpenCards() {
        openedThisTurn.stream()
                .map(cards::get)
                .filter(card -> !card.isMatched())
                .forEach(card -> card.setFaceUp(false));
        openedThisTurn.clear();
        currentPlayer = 1 - currentPlayer;
    }

    public int getWinner() {
        if (scores[0] > scores[1])
            return 0;
        if (scores[1] > scores[0])
            return 1;
        return -1;
    }

    private static List<Card> cardsFromSymbols(List<String> symbols) {
        return IntStream.range(0, symbols.size())
                .mapToObj(i -> new Card(i, symbols.get(i)))
                .toList();
    }

    public enum SelectResult {
        IGNORED,
        OPENED,
        RESOLVED_MATCH,
        RESOLVED_MISMATCH
    }
}
