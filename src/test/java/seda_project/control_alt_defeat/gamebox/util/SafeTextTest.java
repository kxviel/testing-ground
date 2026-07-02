package seda_project.control_alt_defeat.gamebox.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeTextTest {

    @Test
    void playerNamesFallBackNormalizeAndTruncate() {
        assertEquals("Player 1", SafeText.playerName("", "Player 1"));
        assertEquals("Alice Bob", SafeText.playerName(" Alice\tBob\n", "Player 1"));
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                SafeText.playerName("A".repeat(128), "Player 1"));
    }
}
