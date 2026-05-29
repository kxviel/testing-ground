package seda_project.control_alt_defeat.gamebox.controller.tetris;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TetrisMenuControllerTest {

    @Test
    void localNamesFallBackToDefaultsWhenBlank() {
        assertEquals("Player 1", TetrisMenuController.defaultIfBlank("", "Player 1"));
        assertEquals("Player 2", TetrisMenuController.defaultIfBlank("   ", "Player 2"));
        assertEquals("Alice", TetrisMenuController.defaultIfBlank(" Alice ", "Player 1"));
    }
}
