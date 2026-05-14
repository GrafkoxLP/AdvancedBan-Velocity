package me.leoko.advancedban.velocity.event;

import me.leoko.advancedban.utils.Punishment;

/**
 * Fired when a punishment is created. Subscribe with {@code @Subscribe} on Velocity's EventManager:
 *
 * <pre>{@code
 * @Subscribe
 * public void onPunish(PunishmentEvent event) {
 *     Punishment p = event.getPunishment();
 *     // do something
 * }
 * }</pre>
 *
 * Velocity events are plain POJOs — no base class.
 */
public final class PunishmentEvent {

    private final Punishment punishment;

    public PunishmentEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }
}
