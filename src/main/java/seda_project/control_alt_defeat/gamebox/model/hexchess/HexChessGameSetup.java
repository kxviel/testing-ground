package seda_project.control_alt_defeat.gamebox.model.hexchess;

public record HexChessGameSetup(
        String whiteName,
        String blackName,
        HexGameMode mode) {

    public HexChessGameSetup {
        whiteName = whiteName == null || whiteName.isBlank() ? "White" : whiteName.trim();
        blackName = blackName == null || blackName.isBlank() ? "Black" : blackName.trim();
        mode = mode == null ? HexGameMode.LOCAL : mode;
    }

    public static HexChessGameSetup local() {
        return new HexChessGameSetup("White", "Black", HexGameMode.LOCAL);
    }

    public static HexChessGameSetup bot() {
        return new HexChessGameSetup("White", "Bot", HexGameMode.BOT);
    }
}
