package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

public enum GravityDirection {
    DOWN(1, 0),
    UP(-1, 0),
    RIGHT(0, 1),
    LEFT(0, -1);

    private final int rowStep;
    private final int columnStep;

    GravityDirection(int rowStep, int columnStep) {
        this.rowStep = rowStep;
        this.columnStep = columnStep;
    }

    public int rowStep() {
        return rowStep;
    }

    public int columnStep() {
        return columnStep;
    }

    public boolean isHorizontal() {
        return columnStep != 0;
    }
}
