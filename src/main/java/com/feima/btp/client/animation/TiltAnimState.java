package com.feima.btp.client.animation;

/**
 * 内聚的状态机：IDLE / TILTING / FIRE / EXITING。
 *
 * <p>FIRE 与 TILTING 的唯一区别是"层里的 IAnimation 是 fire 还是 loop"——
 * 对 TiltAdjust 而言两者等价（都应用修正），对 BTPAnimator 而言需要区分以决定
 * fire 结束后切回 loop 的时机。
 */
public final class TiltAnimState {
    public enum Phase { IDLE, TILTING, FIRE, EXITING }

    private static final int PRONE_RECOVERY_FRAMES = 8;

    private Phase phase = Phase.IDLE;
    private long exitStartMs = 0L;
    private boolean proneRecovery = false;
    private int proneTransitionFrames = 0;

    public Phase phase() { return phase; }
    public boolean isTilting() { return phase == Phase.TILTING; }
    public boolean isFiring() { return phase == Phase.FIRE; }
    public boolean isExiting() { return phase == Phase.EXITING; }
    public boolean inProneRecovery() { return proneRecovery; }

    /** 倾斜动画是否处于活跃状态（含 FIRE）——TiltAdjust 应用条件。 */
    public boolean isAnimActive() {
        return phase == Phase.TILTING || phase == Phase.FIRE;
    }

    public void markTilting() {
        phase = Phase.TILTING;
        proneRecovery = false;
    }

    public void markFiring() {
        phase = Phase.FIRE;
    }

    public void beginExit() {
        phase = Phase.EXITING;
        exitStartMs = System.currentTimeMillis();
    }

    public boolean exitElapsed(long transitionMs) {
        return phase == Phase.EXITING
                && System.currentTimeMillis() - exitStartMs > transitionMs + 50;
    }

    public void reset() {
        phase = Phase.IDLE;
        exitStartMs = 0L;
        proneRecovery = false;
        // 必须一并清零：否则 recovery 期间被 reset 后，残留帧数会让
        // tickProneTransition() 继续返回 true，导致 hardClear 多走几帧。
        proneTransitionFrames = 0;
    }

    public void onProneChanged(boolean wasProne, boolean isProne) {
        if (wasProne && !isProne) {
            proneTransitionFrames = PRONE_RECOVERY_FRAMES;
            proneRecovery = true;
        }
    }

    public boolean tickProneTransition() {
        if (proneTransitionFrames > 0) {
            proneTransitionFrames--;
            return true;
        }
        return false;
    }
}