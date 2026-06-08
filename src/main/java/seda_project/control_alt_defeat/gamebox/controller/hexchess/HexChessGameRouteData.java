package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;

public record HexChessGameRouteData(
        HexChessGameSetup setup,
        GameServer server,
        GameClient client) {

    public static HexChessGameRouteData host(HexChessGameSetup setup, GameServer server) {
        return new HexChessGameRouteData(setup.withMode(HexGameMode.NETWORK_HOST), server, null);
    }

    public static HexChessGameRouteData join(HexChessGameSetup setup, GameClient client) {
        return new HexChessGameRouteData(setup.withMode(HexGameMode.NETWORK_CLIENT), null, client);
    }
}
