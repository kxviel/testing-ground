package seda_project.control_alt_defeat.gamebox.model.tetris;

public record TetrisGameSetup(String playerOneName, String playerTwoName, TetrisGameConfig config) {

    public TetrisGameSetup {
        config = config == null ? TetrisGameConfig.defaultConfig() : config;
    }
}
