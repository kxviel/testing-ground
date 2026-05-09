package seda_project.control_alt_defeat.gamebox.controller.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.TetrisGameSetup;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;

public record TetrisGameRouteData(TetrisGameSetup setup, GameServer server, GameClient client) {

    public static TetrisGameRouteData local(TetrisGameSetup setup) {
        return new TetrisGameRouteData(setup, null, null);
    }

    public static TetrisGameRouteData host(TetrisGameSetup setup, GameServer server) {
        return new TetrisGameRouteData(setup, server, null);
    }

    public static TetrisGameRouteData join(TetrisGameSetup setup, GameClient client) {
        return new TetrisGameRouteData(setup, null, client);
    }
}
