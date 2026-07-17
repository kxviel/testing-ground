package seda_project.control_alt_defeat.gamebox.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.ListView;
import javafx.util.Duration;
import seda_project.control_alt_defeat.gamebox.network.LanDiscoveryService;

import java.util.stream.IntStream;

public final class DiscoveredGameListController {

    private static final int MAX_DISCOVERED_GAMES = 100;

    private final ListView<LanDiscoveryService.DiscoveredGame> listView;
    private Timeline staleGameTimer;

    public DiscoveredGameListController(ListView<LanDiscoveryService.DiscoveredGame> listView) {
        this.listView = listView;
    }

    public void upsert(LanDiscoveryService.DiscoveredGame game) {
        if (listView == null || game == null) {
            return;
        }

        String selectedSessionId = selectedSessionId();
        int existingIndex = IntStream.range(0, listView.getItems().size())
                .filter(index -> listView.getItems().get(index).sessionId().equals(game.sessionId()))
                .findFirst()
                .orElse(-1);

        if (existingIndex >= 0) {
            listView.getItems().set(existingIndex, game);
        } else {
            if (listView.getItems().size() >= MAX_DISCOVERED_GAMES) {
                int oldestIndex = IntStream.range(0, listView.getItems().size())
                        .reduce((first, second) -> listView.getItems().get(first).timestamp()
                                <= listView.getItems().get(second).timestamp() ? first : second)
                        .orElse(0);
                listView.getItems().remove(oldestIndex);
            }
            listView.getItems().add(game);
        }

        restoreSelection(selectedSessionId);
        if (listView.getSelectionModel().isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }
    }

    public void clear() {
        if (listView != null) {
            listView.getItems().clear();
        }
    }

    public LanDiscoveryService.DiscoveredGame selectedGame() {
        return listView == null ? null : listView.getSelectionModel().getSelectedItem();
    }

    public void removeStale(long ttlMillis) {
        if (listView == null) {
            return;
        }

        long cutoff = System.currentTimeMillis() - Math.max(0, ttlMillis);
        listView.getItems().removeIf(game -> game.timestamp() < cutoff);
    }

    public void startStaleTimer(long ttlMillis) {
        stop();
        staleGameTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> removeStale(ttlMillis)));
        staleGameTimer.setCycleCount(Animation.INDEFINITE);
        staleGameTimer.play();
    }

    public void stop() {
        if (staleGameTimer != null) {
            staleGameTimer.stop();
            staleGameTimer = null;
        }
    }

    private String selectedSessionId() {
        LanDiscoveryService.DiscoveredGame selected = selectedGame();
        return selected == null ? null : selected.sessionId();
    }

    private void restoreSelection(String sessionId) {
        if (sessionId == null || listView == null) {
            return;
        }

        IntStream.range(0, listView.getItems().size())
                .filter(index -> listView.getItems().get(index).sessionId().equals(sessionId))
                .findFirst()
                .ifPresent(index -> listView.getSelectionModel().select(index));
    }
}
