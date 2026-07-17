package seda_project.control_alt_defeat.gamebox.model.tetris;

public record TetrisEffectState(
        int gravityPercent,
        int gravityTicks,
        int rotationEffectTicks,
        int rotationLagTicks) {

    public static final int NORMAL_GRAVITY_PERCENT = 100;
    public static final int ROTATION_LAG_TICKS = 20;
    public static final int MIN_GRAVITY_PERCENT = 25;
    public static final int MAX_GRAVITY_PERCENT = 400;
    public static final int MAX_EFFECT_TICKS = 10_000;

    public TetrisEffectState {
        gravityPercent = gravityPercent <= 0
                ? NORMAL_GRAVITY_PERCENT
                : Math.min(MAX_GRAVITY_PERCENT, Math.max(MIN_GRAVITY_PERCENT, gravityPercent));
        gravityTicks = Math.min(MAX_EFFECT_TICKS, Math.max(0, gravityTicks));
        rotationEffectTicks = Math.min(MAX_EFFECT_TICKS, Math.max(0, rotationEffectTicks));
        rotationLagTicks = Math.min(MAX_EFFECT_TICKS, Math.max(0, rotationLagTicks));
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
        long adjusted = (long) Math.max(0, baseGravityMillis) * gravityPercent / NORMAL_GRAVITY_PERCENT;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(80L, adjusted));
    }
}
