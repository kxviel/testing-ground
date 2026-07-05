package seda_project.control_alt_defeat.gamebox.controller.memory;

import java.util.Queue;

import seda_project.control_alt_defeat.gamebox.model.memory.BoardVariant;
import seda_project.control_alt_defeat.gamebox.network.GameClient;
import seda_project.control_alt_defeat.gamebox.network.GameServer;
import seda_project.control_alt_defeat.gamebox.util.SafeText;

public record MemoryGameRouteData(
        BoardVariant variant,
        GameController.Mode mode,
        GameServer server,
        GameClient client,
        Queue<String> pendingMessages,
        String playerOneName,
        String playerTwoName) {

    private static final String PLAYER_ONE = SafeText.PLAYER_ONE_NAME;
    private static final String PLAYER_TWO = SafeText.PLAYER_TWO_NAME;

    public MemoryGameRouteData {
        playerOneName = SafeText.playerName(playerOneName, PLAYER_ONE);
        playerTwoName = SafeText.playerName(playerTwoName, PLAYER_TWO);
    }

    public static MemoryGameRouteData local(BoardVariant variant, String playerOneName, String playerTwoName) {
        return new MemoryGameRouteData(
                variant,
                GameController.Mode.LOCAL,
                null,
                null,
                null,
                playerOneName,
                playerTwoName);
    }

    public static MemoryGameRouteData host(
            BoardVariant variant,
            GameServer server,
            Queue<String> pendingMessages,
            String playerOneName) {
        return new MemoryGameRouteData(
                variant,
                GameController.Mode.NETWORK_HOST,
                server,
                null,
                pendingMessages,
                playerOneName,
                PLAYER_TWO);
    }

    public static MemoryGameRouteData join(
            GameClient client,
            Queue<String> pendingMessages,
            String playerTwoName) {
        return new MemoryGameRouteData(
                null,
                GameController.Mode.NETWORK_CLIENT,
                null,
                client,
                pendingMessages,
                PLAYER_ONE,
                playerTwoName);
    }
}
