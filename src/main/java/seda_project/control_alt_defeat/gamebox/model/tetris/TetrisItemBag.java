package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class TetrisItemBag {

    private final List<TetrisItemType> remainingTypes = new ArrayList<>();

    public TetrisItemType next(Random random) {
        return next(random, TetrisItemType.eligibleForOpponentState(true));
    }

    public TetrisItemType next(Random random, Set<TetrisItemType> eligibleTypes) {
        if (eligibleTypes == null || eligibleTypes.isEmpty()) {
            return null;
        }

        remainingTypes.removeIf(type -> !eligibleTypes.contains(type));
        if (remainingTypes.isEmpty()) {
            refill(random, eligibleTypes);
        }

        return remainingTypes.remove(remainingTypes.size() - 1);
    }

    public void reset() {
        remainingTypes.clear();
    }

    private void refill(Random random, Set<TetrisItemType> eligibleTypes) {
        remainingTypes.addAll(eligibleTypes);
        Collections.shuffle(remainingTypes, random);
    }
}
