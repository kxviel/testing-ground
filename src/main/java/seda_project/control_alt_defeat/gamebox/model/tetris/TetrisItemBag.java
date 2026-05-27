package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TetrisItemBag {

    private final List<TetrisItemType> remainingTypes = new ArrayList<>();

    public TetrisItemType next(Random random) {
        if (remainingTypes.isEmpty()) {
            refill(random);
        }

        return remainingTypes.remove(remainingTypes.size() - 1);
    }

    public void reset() {
        remainingTypes.clear();
    }

    private void refill(Random random) {
        remainingTypes.addAll(List.of(TetrisItemType.values()));
        Collections.shuffle(remainingTypes, random);
    }
}
