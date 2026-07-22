package seda_project.control_alt_defeat.gamebox.model.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class GameModel {

    private static final String[] SYMBOL_POOL = {
            "card_faces/icons8-aang-50.png",
            "card_faces/icons8-amethyst-universe-50.png",
            "card_faces/icons8-batman-50.png",
            "card_faces/icons8-bendy-50.png",
            "card_faces/icons8-bt21-chimmy-50.png",
            "card_faces/icons8-bt21-mang-50.png",
            "card_faces/icons8-c-3po-50.png",
            "card_faces/icons8-cylon-head-new-50.png",
            "card_faces/icons8-eric-cartman-50.png",
            "card_faces/icons8-fortnite-llama-50.png",
            "card_faces/icons8-fursona-50.png",
            "card_faces/icons8-futurama-nibbler-50.png",
            "card_faces/icons8-grey-50.png",
            "card_faces/icons8-house-stark-50.png",
            "card_faces/icons8-hulk-50.png",
            "card_faces/icons8-iron-man-50.png",
            "card_faces/icons8-jeffy-50.png",
            "card_faces/icons8-jerry-50.png",
            "card_faces/icons8-jetpack-joyride-50.png",
            "card_faces/icons8-lightsaber-50.png",
            "card_faces/icons8-logan-x-men-50.png",
            "card_faces/icons8-magic-lamp-50.png",
            "card_faces/icons8-millennium-rod-50.png",
            "card_faces/icons8-money-heist-dali-50.png",
            "card_faces/icons8-mongrol-50.png",
            "card_faces/icons8-neo-50.png",
            "card_faces/icons8-popeye-50.png",
            "card_faces/icons8-pumbaa-50.png",
            "card_faces/icons8-rick-sanchez-50.png",
            "card_faces/icons8-s.h.i.e.l.d-50.png",
            "card_faces/icons8-scooby-doo-50.png",
            "card_faces/icons8-scooby-doo-daphne-blake-50.png",
            "card_faces/icons8-scooby-doo-velma-dinkley-50.png",
            "card_faces/icons8-spider-man-head-50.png",
            "card_faces/icons8-spider-man-old-50.png",
            "card_faces/icons8-star-track-lokirrim-partol-ship-50.png",
            "card_faces/icons8-stormtrooper-50.png",
            "card_faces/icons8-superman-50.png",
            "card_faces/icons8-superman-dc-50.png",
            "card_faces/icons8-the-flash-sign-50.png",
            "card_faces/icons8-timon-50.png",
            "card_faces/icons8-tom-50.png",
            "card_faces/icons8-tricorder-50.png",
            "card_faces/icons8-walter-white-50.png",
            "card_faces/icons8-woody-woodpecker-50.png"
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
        this(requireVariant(variant).k, variant.n, variant.rows, variant.cols);
    }

    public GameModel(int k, int n, int rows, int cols) {
        validateDimensions(k, n, rows, cols, (long) k * n);
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
                .mapToObj(i -> SYMBOL_POOL[i / k])
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(symbols);

        this.cards = cardsFromSymbols(symbols);
    }

    public GameModel(int k, int n, int rows, int cols, List<String> orderedSymbols, int currentPlayer) {
        validateSnapshotBasics(k, n, rows, cols, orderedSymbols, currentPlayer, 0, 0);
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
        validateSnapshotBasics(k, n, rows, cols, orderedSymbols, currentPlayer, score0, score1);
        Objects.requireNonNull(faceUp, "Face-up state is required.");
        Objects.requireNonNull(matched, "Matched state is required.");
        Objects.requireNonNull(matchedBy, "Matched-owner state is required.");
        if (orderedSymbols.size() != faceUp.size()
                || orderedSymbols.size() != matched.size()
                || orderedSymbols.size() != matchedBy.size()) {
            throw new IllegalArgumentException("Card state size must match symbol count.");
        }
        int[] matchedCounts = new int[2];
        int openCount = 0;
        for (int i = 0; i < orderedSymbols.size(); i++) {
            Boolean isFaceUp = faceUp.get(i);
            Boolean isMatched = matched.get(i);
            Integer owner = matchedBy.get(i);
            if (isFaceUp == null || isMatched == null || owner == null) {
                throw new IllegalArgumentException("Card state must not contain null values.");
            }
            if (isMatched) {
                if (owner != 0 && owner != 1) {
                    throw new IllegalArgumentException("Matched cards require a valid owner.");
                }
                matchedCounts[owner]++;
            } else {
                if (owner != -1) {
                    throw new IllegalArgumentException("Unmatched cards cannot have an owner.");
                }
                if (isFaceUp) {
                    openCount++;
                }
            }
        }
        if (openCount > k || matchedCounts[0] != score0 * k || matchedCounts[1] != score1 * k) {
            throw new IllegalArgumentException("Card state is inconsistent with the scores.");
        }
        boolean allMatched = matchedCounts[0] + matchedCounts[1] == orderedSymbols.size();
        if (gameOver != allMatched) {
            throw new IllegalArgumentException("Game-over state is inconsistent with the cards.");
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
        Objects.requireNonNull(symbols, "Card symbols are required.");
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one card symbol is required.");
        }
        symbols.forEach(symbol -> {
            if (symbol == null || symbol.isBlank() || symbol.length() > 128) {
                throw new IllegalArgumentException("Invalid memory card symbol.");
            }
        });
        return IntStream.range(0, symbols.size())
                .mapToObj(i -> new Card(i, symbols.get(i)))
                .toList();
    }

    public static boolean isSupportedSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        for (String supported : SYMBOL_POOL) {
            if (supported.equals(symbol)) {
                return true;
            }
        }
        return false;
    }

    private static BoardVariant requireVariant(BoardVariant variant) {
        return Objects.requireNonNull(variant, "Board variant is required.");
    }

    private static void validateSnapshotBasics(
            int k,
            int n,
            int rows,
            int cols,
            List<String> symbols,
            int currentPlayer,
            int score0,
            int score1) {
        Objects.requireNonNull(symbols, "Card symbols are required.");
        validateDimensions(k, n, rows, cols, symbols.size());
        if (currentPlayer != 0 && currentPlayer != 1) {
            throw new IllegalArgumentException("Current player must be 0 or 1.");
        }
        if (score0 < 0 || score1 < 0 || (long) score0 + score1 > n) {
            throw new IllegalArgumentException("Memory scores are outside the valid range.");
        }
        symbols.forEach(symbol -> {
            if (symbol == null || symbol.isBlank() || symbol.length() > 128) {
                throw new IllegalArgumentException("Invalid memory card symbol.");
            }
        });
    }

    private static void validateDimensions(int k, int n, int rows, int cols, long cardCount) {
        long configuredCards = (long) k * n;
        long gridCards = (long) rows * cols;
        if (k < 1 || n < 1 || rows < 1 || cols < 1
                || configuredCards > BoardVariant.MAX_CARDS
                || gridCards < configuredCards
                || gridCards > BoardVariant.MAX_GRID_CELLS
                || configuredCards != cardCount) {
            throw new IllegalArgumentException("Invalid memory board dimensions.");
        }
    }

    public enum SelectResult {
        IGNORED,
        OPENED,
        RESOLVED_MATCH,
        RESOLVED_MISMATCH
    }
}
