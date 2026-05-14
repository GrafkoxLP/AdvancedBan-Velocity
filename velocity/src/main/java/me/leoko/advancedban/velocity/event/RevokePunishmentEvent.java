package me.leoko.advancedban.velocity.event;

import me.leoko.advancedban.utils.Punishment;

/**
 * Fired when a punishment is revoked (manually or by auto-expiry).
 *
 * <pre>{@code
 * @Subscribe
 * public void onRevoke(RevokePunishmentEvent event) {
 *     Punishment p = event.getPunishment();
 *     boolean batch = event.isMassClear();
 * }
 * }</pre>
 */
public final class RevokePunishmentEvent {

    private final Punishment punishment;
    private final boolean massClear;

    public RevokePunishmentEvent(Punishment punishment, boolean massClear) {
        this.punishment = punishment;
        this.massClear = massClear;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    public boolean isMassClear() {
        return massClear;
    }
}
