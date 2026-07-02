package seda_project.control_alt_defeat.gamebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.model.memory.GameModel;

class GameLogicTest {

    @Test
    void memoryVariantAndMatchFlowWork() {
        assertEquals(3, BoardVariant.computeVariants(2).size());

        GameModel model = new GameModel(2, 2, 2, 2, List.of("A", "A", "B", "B"), 0);
        assertEquals(GameModel.SelectResult.OPENED, model.selectCard(0));
        assertEquals(GameModel.SelectResult.RESOLVED_MATCH, model.selectCard(1));
        assertEquals(1, model.getScore(0));

        model.selectCard(2);
        model.selectCard(3);
        assertTrue(model.isGameOver());
    }
}
