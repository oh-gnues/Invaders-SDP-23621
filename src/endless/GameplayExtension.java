package endless;

import engine.*;

import java.awt.Graphics2D;

/**
 * Optional cross-mode feature. Register the implementation's full class name in
 * res/META-INF/services/endless.GameplayExtension. No EndlessMode edit is needed.
 * Implementations must honor session.isPersistent() for all progression writes.
 */
public interface GameplayExtension {
    enum Event { START, SHOT, ENEMY_HIT, ENEMY_DEFEATED, PLAYER_HIT,
        ITEM, WAVE_STARTED, WAVE_CLEARED, FINISH }
    default void onEvent(GameplaySession session, Event event, Object value) { }
    default void update(GameplaySession session, double seconds) { }
    default void draw(GameplaySession session, Graphics2D graphics) { }
    /** Return null to keep the normal catalog; the first provider wins. */
    default entity.Entity createEnemy(GameplaySession session, int wave, int index, int x, int y) { return null; }
    default entity.Boss createBoss(GameplaySession session, int wave, int centerX, int y) { return null; }
    default double enemyHealth(GameplaySession session, entity.Entity enemy, double normalHealth) { return normalHealth; }
}
