package engine;

/**
 * Sound effects for the player's ship: taking a hit and being destroyed.
 * Built on top of {@link SoundManager}, so the .wav files live in the same
 * place as the other sounds (the res/ folder).
 *
 * Branch: sfx/player-damage-death
 */
public final class PlayerSounds {

    /** Played when the player loses a life but is still alive. */
    private static final String DAMAGE_SOUND = "player_damage.wav";
    /** Played when the player loses the last life (game over). */
    private static final String DEATH_SOUND = "player_death.wav";

    /** Utility class, not meant to be instantiated. */
    private PlayerSounds() {
    }

    /**
     * Short, sharp impact: the ship was hit but survives.
     */
    public static void playDamage() {
        SoundManager.playSound(DAMAGE_SOUND);
    }

    /**
     * Long descending explosion: the last life is lost, the game is over.
     */
    public static void playDeath() {
        SoundManager.playSound(DEATH_SOUND);
    }

    /**
     * Picks the right sound right after the ship has been hit.
     *
     * @param livesRemaining
     *            Lives left after the hit (0 means game over).
     */
    public static void playHit(final int livesRemaining) {
        if (livesRemaining <= 0) {
            playDeath();
        } else {
            playDamage();
        }
    }
}