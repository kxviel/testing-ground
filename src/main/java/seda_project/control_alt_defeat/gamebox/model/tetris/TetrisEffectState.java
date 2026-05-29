package seda_project.control_alt_defeat.gamebox.model.tetris;

public record TetrisEffectState(
        int gravityPercent,
        int gravityTicks,
        int rotationEffectTicks,
        int rotationLagTicks) {

    public static final int NORMAL_GRAVITY_PERCENT = 100;
    public static final int ROTATION_LAG_TICKS = 20;

    public TetrisEffectState {
        gravityPercent = gravityPercent <= 0 ? NORMAL_GRAVITY_PERCENT : gravityPercent;
        gravityTicks = Math.max(0, gravityTicks);
        rotationEffectTicks = Math.max(0, rotationEffectTicks);
        rotationLagTicks = Math.max(0, rotationLagTicks);
    }

    /** Backward-compatible accessor for serialized snapshots and tests. */
    public int rotationDelayTicks() {
        return rotationEffectTicks;
    }

    public static TetrisEffectState none() {
        return new TetrisEffectState(NORMAL_GRAVITY_PERCENT, 0, 0, 0);
    }

    public TetrisEffectState withGravityPercent(int nextGravityPercent, int ticks) {
        return new TetrisEffectState(nextGravityPercent, ticks, rotationEffectTicks, rotationLagTicks);
    }

    public TetrisEffectState withRotationDelay(int effectTicks) {
        return new TetrisEffectState(gravityPercent, gravityTicks, effectTicks, rotationLagTicks);
    }

    public TetrisEffectState scheduleRotationLag(int ticks) {
        return new TetrisEffectState(gravityPercent, gravityTicks, rotationEffectTicks, ticks);
    }

    public TetrisEffectState tick() {
        int nextGravityTicks = Math.max(0, gravityTicks - 1);
        int nextRotationEffectTicks = Math.max(0, rotationEffectTicks - 1);
        int nextRotationLagTicks = Math.max(0, rotationLagTicks - 1);
        int nextGravityPercent = nextGravityTicks == 0 ? NORMAL_GRAVITY_PERCENT : gravityPercent;

        return new TetrisEffectState(
                nextGravityPercent,
                nextGravityTicks,
                nextRotationEffectTicks,
                nextRotationLagTicks);
    }

    public boolean hasRotationDelayEffect() {
        return rotationEffectTicks > 0;
    }

    public boolean canAcceptRotationInput() {
        return rotationLagTicks == 0;
    }

    public boolean shouldApplyDelayedRotation() {
        return rotationLagTicks == 1;
    }

    public int gravityMillis(int baseGravityMillis) {
        return Math.max(80, baseGravityMillis * gravityPercent / NORMAL_GRAVITY_PERCENT);
    }
}
