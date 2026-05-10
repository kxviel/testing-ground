package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

public enum Rotation {
    SPAWN,
    RIGHT,
    HALF,
    LEFT;

    public Rotation clockwise() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Rotation counterClockwise() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
