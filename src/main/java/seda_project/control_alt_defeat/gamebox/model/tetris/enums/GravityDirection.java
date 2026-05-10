package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

public enum GravityDirection {
    DOWN(1),
    UP(-1);

    private final int rowStep;

    GravityDirection(int rowStep) {
        this.rowStep = rowStep;
    }

    public int rowStep() {
        return rowStep;
    }
}
