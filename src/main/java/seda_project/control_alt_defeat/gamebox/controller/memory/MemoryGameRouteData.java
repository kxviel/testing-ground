package seda_project.control_alt_defeat.gamebox.controller.memory;

import java.util.Queue;

import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;

public record MemoryGameRouteData(
        BoardVariant variant,
        GameController.Mode mode,
        GameServer server,
        GameClient client,
        Queue<String> pendingMessages) {

    public static MemoryGameRouteData local(BoardVariant variant) {
        return new MemoryGameRouteData(variant, GameController.Mode.LOCAL, null, null, null);
    }

    public static MemoryGameRouteData host(BoardVariant variant, GameServer server) {
        return new MemoryGameRouteData(variant, GameController.Mode.NETWORK_HOST, server, null, null);
    }

    public static MemoryGameRouteData join(GameClient client, Queue<String> pendingMessages) {
        return new MemoryGameRouteData(null, GameController.Mode.NETWORK_CLIENT, null, client, pendingMessages);
    }
}
