package seda_project.control_alt_defeat.gamebox.controller.hexchess;

import seda_project.control_alt_defeat.gamebox.model.hexchess.HexChessGameSetup;
import seda_project.control_alt_defeat.gamebox.model.hexchess.HexGameMode;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import java.util.Queue;

public record HexChessGameRouteData(
        HexChessGameSetup setup,
        GameServer server,
        GameClient client,
        Queue<String> pendingMessages) {

    public static HexChessGameRouteData host(HexChessGameSetup setup, GameServer server, Queue<String> pendingMessages) {
        return new HexChessGameRouteData(setup.withMode(HexGameMode.NETWORK_HOST), server, null, pendingMessages);
    }

    public static HexChessGameRouteData join(HexChessGameSetup setup, GameClient client, Queue<String> pendingMessages) {
        return new HexChessGameRouteData(setup.withMode(HexGameMode.NETWORK_CLIENT), null, client, pendingMessages);
    }
}
