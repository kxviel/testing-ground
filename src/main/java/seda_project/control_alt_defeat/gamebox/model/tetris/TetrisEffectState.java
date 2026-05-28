package seda_project.control_alt_defeat.gamebox.model.tetris;

public record TetrisEffectState(int gravityPercent, int gravityTicks, int rotationDelayTicks) {

    public static final int NORMAL_GRAVITY_PERCENT = 100;

    public TetrisEffectState {
        gravityPercent = gravityPercent <= 0 ? NORMAL_GRAVITY_PERCENT : gravityPercent;
        gravityTicks = Math.max(0, gravityTicks);
        rotationDelayTicks = Math.max(0, rotationDelayTicks);
    }

    public static TetrisEffectState none() {
        return new TetrisEffectState(NORMAL_GRAVITY_PERCENT, 0, 0);
    }

    public TetrisEffectState withGravityPercent(int nextGravityPercent, int ticks) {
        return new TetrisEffectState(nextGravityPercent, ticks, rotationDelayTicks);
    }

    public TetrisEffectState withRotationDelay(int ticks) {
        return new TetrisEffectState(gravityPercent, gravityTicks, ticks);
    }

    public TetrisEffectState tick() {
        int nextGravityTicks = Math.max(0, gravityTicks - 1);
        int nextRotationDelayTicks = Math.max(0, rotationDelayTicks - 1);
        int nextGravityPercent = nextGravityTicks == 0 ? NORMAL_GRAVITY_PERCENT : gravityPercent;

        return new TetrisEffectState(nextGravityPercent, nextGravityTicks, nextRotationDelayTicks);
    }

    public boolean hasRotationDelay() {
        return rotationDelayTicks > 0;
    }

    public int gravityMillis(int baseGravityMillis) {
        return Math.max(80, baseGravityMillis * gravityPercent / NORMAL_GRAVITY_PERCENT);
    }
}
