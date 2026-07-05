package seda_project.control_alt_defeat.gamebox.model.tetris.enums;

public enum PlayerSide {
    BOTTOM(GravityDirection.DOWN),
    TOP(GravityDirection.DOWN);

    private final GravityDirection gravityDirection;

    PlayerSide(GravityDirection gravityDirection) {
        this.gravityDirection = gravityDirection;
    }

    public GravityDirection gravityDirection() {
        return gravityDirection;
    }

    public PlayerSide opponent() {
        return this == BOTTOM ? TOP : BOTTOM;
    }
}
