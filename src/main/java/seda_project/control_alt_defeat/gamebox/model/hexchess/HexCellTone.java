package seda_project.control_alt_defeat.gamebox.model.hexchess;

public enum HexCellTone {
    LIGHT("hex-cell-light"),
    MID("hex-cell-mid"),
    DARK("hex-cell-dark");

    private final String styleClass;

    HexCellTone(String styleClass) {
        this.styleClass = styleClass;
    }

    public String styleClass() {
        return styleClass;
    }
}
