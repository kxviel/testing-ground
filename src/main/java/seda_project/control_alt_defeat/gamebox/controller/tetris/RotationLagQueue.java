package seda_project.control_alt_defeat.gamebox.controller.tetris;

import java.util.ArrayList;
import java.util.List;

final class RotationLagQueue {

    private final List<Integer> pendingTicks = new ArrayList<>();

    void clear() {
        pendingTicks.clear();
    }

    void enqueue(int delayTicks) {
        pendingTicks.add(Math.max(0, delayTicks));
    }

    int tickReadyCount() {
        int readyCount = 0;

        for (int index = 0; index < pendingTicks.size(); index++) {
            int remainingTicks = pendingTicks.get(index) - 1;
            if (remainingTicks <= 0) {
                pendingTicks.remove(index);
                readyCount++;
                index--;
            } else {
                pendingTicks.set(index, remainingTicks);
            }
        }

        return readyCount;
    }
}
