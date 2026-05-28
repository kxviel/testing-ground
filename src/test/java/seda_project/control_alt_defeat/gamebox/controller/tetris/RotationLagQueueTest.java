package seda_project.control_alt_defeat.gamebox.controller.tetris;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RotationLagQueueTest {

    @Test
    void queuedRotationBecomesReadyAfterTwentyTicks() {
        RotationLagQueue queue = new RotationLagQueue();
        queue.enqueue(20);

        for (int tick = 0; tick < 19; tick++) {
            assertEquals(0, queue.tickReadyCount());
        }

        assertEquals(1, queue.tickReadyCount());
        assertEquals(0, queue.tickReadyCount());
    }

    @Test
    void queuedRotationsKeepIndependentDeadlines() {
        RotationLagQueue queue = new RotationLagQueue();
        queue.enqueue(20);

        for (int tick = 0; tick < 10; tick++) {
            assertEquals(0, queue.tickReadyCount());
        }

        queue.enqueue(20);

        for (int tick = 0; tick < 9; tick++) {
            assertEquals(0, queue.tickReadyCount());
        }

        assertEquals(1, queue.tickReadyCount());

        for (int tick = 0; tick < 9; tick++) {
            assertEquals(0, queue.tickReadyCount());
        }

        assertEquals(1, queue.tickReadyCount());
    }
}
