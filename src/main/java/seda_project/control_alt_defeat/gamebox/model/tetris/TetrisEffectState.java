package seda_project.control_alt_defeat.gamebox.model.tetris;

import seda_project.control_alt_defeat.gamebox.model.tetris.enums.TetrisItemType;

public record TetrisEffectState(
        int gravityPercent,
        int gravityTicks,
        int rotationEffectTicks,
        int rotationLagTicks,
        TetrisItemType gravityEffectType,
        TetrisItemType rotationEffectType) {

    public static final int NORMAL_GRAVITY_PERCENT = 100;
    public static final int ROTATION_LAG_TICKS = 20;
    public static final int MIN_GRAVITY_PERCENT = 25;
    public static final int MAX_GRAVITY_PERCENT = 400;
    public static final int MAX_EFFECT_TICKS = 10_000;

    /**
     * Keeps the original constructor usable for older snapshots and callers
     * that do not have effect-source metadata.
     */
    public TetrisEffectState(
            int gravityPercent,
            int gravityTicks,
            int rotationEffectTicks,
            int rotationLagTicks) {
        this(gravityPercent, gravityTicks, rotationEffectTicks, rotationLagTicks, null, null);
    }

    public TetrisEffectState {
        gravityPercent = gravityPercent <= 0
                ? NORMAL_GRAVITY_PERCENT
                : Math.min(MAX_GRAVITY_PERCENT, Math.max(MIN_GRAVITY_PERCENT, gravityPercent));
        gravityTicks = Math.min(MAX_EFFECT_TICKS, Math.max(0, gravityTicks));
        rotationEffectTicks = Math.min(MAX_EFFECT_TICKS, Math.max(0, rotationEffectTicks));
        rotationLagTicks = Math.min(MAX_EFFECT_TICKS, Math.max(0, rotationLagTicks));
        gravityEffectType = gravityTicks == 0 ? null : normalizeGravityEffectType(gravityEffectType);
        rotationEffectType = rotationEffectTicks == 0 ? null : normalizeRotationEffectType(rotationEffectType);
    }

    public static TetrisEffectState none() {
        return new TetrisEffectState(NORMAL_GRAVITY_PERCENT, 0, 0, 0);
    }

    public TetrisEffectState withGravityPercent(int nextGravityPercent, int ticks) {
        return new TetrisEffectState(
                nextGravityPercent,
                ticks,
                rotationEffectTicks,
                rotationLagTicks,
                gravityEffectType,
                rotationEffectType);
    }

    public TetrisEffectState withGravityPercent(
            int nextGravityPercent,
            int ticks,
            TetrisItemType effectType) {
        return new TetrisEffectState(
                nextGravityPercent,
                ticks,
                rotationEffectTicks,
                rotationLagTicks,
                effectType,
                rotationEffectType);
    }

    public TetrisEffectState withRotationDelay(int effectTicks) {
        return new TetrisEffectState(
                gravityPercent,
                gravityTicks,
                effectTicks,
                rotationLagTicks,
                gravityEffectType,
                rotationEffectType);
    }

    public TetrisEffectState withRotationDelay(int effectTicks, TetrisItemType effectType) {
        return new TetrisEffectState(
                gravityPercent,
                gravityTicks,
                effectTicks,
                rotationLagTicks,
                gravityEffectType,
                effectType);
    }

    public TetrisEffectState scheduleRotationLag(int ticks) {
        return new TetrisEffectState(
                gravityPercent,
                gravityTicks,
                rotationEffectTicks,
                ticks,
                gravityEffectType,
                rotationEffectType);
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
                nextRotationLagTicks,
                nextGravityTicks == 0 ? null : gravityEffectType,
                nextRotationEffectTicks == 0 ? null : rotationEffectType);
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

    private static TetrisItemType normalizeGravityEffectType(TetrisItemType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SPEED_UP_OPPONENT, SLOW_SELF, SLOW_OPPONENT -> type;
            default -> null;
        };
    }

    private static TetrisItemType normalizeRotationEffectType(TetrisItemType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ROTATION_DELAY_OPPONENT, ROTATION_DELAY_SELF -> type;
            default -> null;
        };
    }
}
