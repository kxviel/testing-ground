package seda_project.control_alt_defeat.gamebox.model.hexchess;

import seda_project.control_alt_defeat.gamebox.util.SafeText;

public record HexChessGameSetup(
        String whiteName,
        String blackName,
        HexGameMode mode,
        HexBoard initialBoard,
        HexPieceColor startingTurn,
        boolean customPosition) {

    public HexChessGameSetup {
        whiteName = SafeText.playerName(whiteName, SafeText.PLAYER_ONE_NAME);
        blackName = SafeText.playerName(blackName, SafeText.PLAYER_TWO_NAME);
        mode = mode == null ? HexGameMode.LOCAL : mode;
        initialBoard = initialBoard == null ? HexBoard.standard() : initialBoard;
        startingTurn = startingTurn == null ? HexPieceColor.WHITE : startingTurn;
    }

    public HexChessGameSetup(String whiteName, String blackName, HexGameMode mode) {
        this(whiteName, blackName, mode, HexBoard.standard(), HexPieceColor.WHITE, false);
    }

    public HexChessGameSetup(
            String whiteName,
            String blackName,
            HexGameMode mode,
            HexBoard initialBoard,
            HexPieceColor startingTurn) {
        this(whiteName, blackName, mode, initialBoard, startingTurn, false);
    }

    public static HexChessGameSetup local() {
        return new HexChessGameSetup(SafeText.PLAYER_ONE_NAME, SafeText.PLAYER_TWO_NAME, HexGameMode.LOCAL);
    }

    public static HexChessGameSetup bot() {
        return new HexChessGameSetup(SafeText.PLAYER_ONE_NAME, "Bot", HexGameMode.BOT);
    }

    public HexChessGameSetup withMode(HexGameMode nextMode) {
        return new HexChessGameSetup(whiteName, blackName, nextMode, initialBoard, startingTurn, customPosition);
    }
}
