package seda_project.control_alt_defeat.gamebox.network.memory;

import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.decode;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.encode;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.parseBoolean;
import static seda_project.control_alt_defeat.gamebox.network.SnapshotCodec.parseInt;

public final class MemoryStateSnapshot {

    private static final int FIELD_COUNT = 9;
    private static final int CARD_FIELD_COUNT = 5;

    private MemoryStateSnapshot() {
    }

    public static String serialize(GameModel model) {
        return String.join("|",
                String.valueOf(model.getK()),
                String.valueOf(model.getN()),
                String.valueOf(model.getRows()),
                String.valueOf(model.getCols()),
                String.valueOf(model.getCurrentPlayer()),
                String.valueOf(model.getScore(0)),
                String.valueOf(model.getScore(1)),
                String.valueOf(model.isGameOver()),
                serializeCards(model));
    }

    public static GameModel deserialize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Snapshot is empty.");
        }

        String[] fields = value.split("\\|", FIELD_COUNT + 1);
        if (fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException("Snapshot has wrong field count.");
        }

        int k = parseInt(fields[0]);
        int n = parseInt(fields[1]);
        int rows = parseInt(fields[2]);
        int cols = parseInt(fields[3]);
        int currentPlayer = parsePlayer(fields[4]);
        int score0 = parseInt(fields[5]);
        int score1 = parseInt(fields[6]);
        boolean gameOver = parseBoolean(fields[7]);
        if (k < 1 || n < 1 || rows < 1 || cols < 1) {
            throw new IllegalArgumentException("Snapshot dimensions must be positive.");
        }
        long configuredCards = (long) k * n;
        long gridCards = (long) rows * cols;
        if (configuredCards > BoardVariant.MAX_CARDS
                || gridCards < configuredCards
                || gridCards > BoardVariant.MAX_GRID_CELLS) {
            throw new IllegalArgumentException("Snapshot dimensions are outside the supported range.");
        }
        if (score0 < 0 || score1 < 0) {
            throw new IllegalArgumentException("Snapshot scores must be non-negative.");
        }
        List<CardState> cards = deserializeCards(fields[8], (int) configuredCards);
        if (cards.size() != configuredCards) {
            throw new IllegalArgumentException("Snapshot dimensions do not match cards.");
        }

        List<String> symbols = cards.stream().map(CardState::symbol).toList();
        List<Boolean> faceUp = cards.stream().map(CardState::faceUp).toList();
        List<Boolean> matched = cards.stream().map(CardState::matched).toList();
        List<Integer> matchedBy = cards.stream().map(CardState::matchedBy).toList();

        return new GameModel(k, n, rows, cols, symbols, currentPlayer, score0, score1, gameOver, faceUp, matched,
                matchedBy);
    }

    private static String serializeCards(GameModel model) {
        return model.getCards().stream()
                .map(card -> card.getId()
                        + "," + encode(card.getSymbol())
                        + "," + card.isFaceUp()
                        + "," + card.isMatched()
                        + "," + card.getMatchedBy())
                .collect(Collectors.joining(";"));
    }

    private static List<CardState> deserializeCards(String value, int expectedCount) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        String[] entries = value.split(";", expectedCount + 1);
        if (entries.length != expectedCount) {
            throw new IllegalArgumentException("Snapshot has the wrong number of cards.");
        }
        List<CardState> cards = Arrays.stream(entries)
                .map(MemoryStateSnapshot::deserializeCard)
                .toList();

        IntStream.range(0, cards.size())
                .filter(i -> cards.get(i).id() != i)
                .findFirst()
                .ifPresent(i -> {
                    throw new IllegalArgumentException("Card id does not match position: " + i);
                });

        return cards;
    }

    private static CardState deserializeCard(String value) {
        String[] fields = value.split(",", CARD_FIELD_COUNT + 1);
        if (fields.length != CARD_FIELD_COUNT) {
            throw new IllegalArgumentException("Invalid card entry: " + value);
        }

        int id = parseInt(fields[0]);
        if (id < 0) {
            throw new IllegalArgumentException("Invalid card id: " + id);
        }

        String symbol = decode(fields[1]);
        if (!GameModel.isSupportedSymbol(symbol)) {
            throw new IllegalArgumentException("Unsupported memory card symbol.");
        }
        int matchedBy = parseInt(fields[4]);
        if (matchedBy < -1 || matchedBy > 1) {
            throw new IllegalArgumentException("Invalid matched-card owner.");
        }
        return new CardState(
                id,
                symbol,
                parseBoolean(fields[2]),
                parseBoolean(fields[3]),
                matchedBy);
    }

    private static int parsePlayer(String value) {
        int player = parseInt(value);
        if (player != 0 && player != 1) {
            throw new IllegalArgumentException("Invalid player: " + value);
        }
        return player;
    }

    private record CardState(int id, String symbol, boolean faceUp, boolean matched, int matchedBy) {
    }
}
