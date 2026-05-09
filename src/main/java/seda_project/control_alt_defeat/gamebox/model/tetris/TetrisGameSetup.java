package seda_project.control_alt_defeat.gamebox.model.tetris;

public record TetrisGameSetup(
        String playerOneName,
        String playerTwoName,
        TetrisGameConfig config,
        TetrisGameMode mode,
        PlayerSide localSide) {

    public TetrisGameSetup(String playerOneName, String playerTwoName, TetrisGameConfig config) {
        this(playerOneName, playerTwoName, config, TetrisGameMode.LOCAL, null);
    }

    public TetrisGameSetup {
        config = config == null ? TetrisGameConfig.defaultConfig() : config;
        mode = mode == null ? TetrisGameMode.LOCAL : mode;
    }

    public static TetrisGameSetup local(String playerOneName, String playerTwoName, TetrisGameConfig config) {
        return new TetrisGameSetup(playerOneName, playerTwoName, config, TetrisGameMode.LOCAL, null);
    }

    public static TetrisGameSetup host(String hostName, String joinerName, TetrisGameConfig config) {
        return new TetrisGameSetup(hostName, joinerName, config, TetrisGameMode.LAN, PlayerSide.BOTTOM);
    }

    public static TetrisGameSetup join(String hostName, String joinerName, TetrisGameConfig config) {
        return new TetrisGameSetup(hostName, joinerName, config, TetrisGameMode.LAN, PlayerSide.TOP);
    }

    public boolean isLocal() {
        return mode == TetrisGameMode.LOCAL;
    }
}
