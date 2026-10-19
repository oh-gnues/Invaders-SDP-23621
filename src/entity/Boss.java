package entity;

import java.util.Set;
import engine.GameSettings;

/** Behavior shared by the Stage 5 and Stage 10 encounters. */
public interface Boss {
    int getPositionX();
    int getPositionY();
    /** Current remaining health points. */
    int getHp();
    /** Health points this boss starts an encounter with. */
    int getMaxHp();
    boolean isDestroyed();
    void destroy();
    void update();
    void moveBoss(int screenWidth, int screenHeight, GameSettings settings);
    boolean shoot(Set<Bullet> bullets, GameSettings settings);
    int manageCollisionsBoss(Bullet bullet);
}
