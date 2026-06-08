/**
 * Base class for any game entity that can receive damage, be stunned,
 * and visually flash to indicate a recent hit.
 * Damage is gated by a time-based invulnerability window ({@value #INVULN_MS} ms),
 * so rapid hits are automatically absorbed without extra logic in subclasses.
 */
class Damageable {

    /** Current hit points. Reaches 0 or below on death. */
    int hp;

    /** Maximum hit points, set once at construction. Used by the HUD to draw the health bar. */
    int maxHp;

    /** Timestamp of the last successful hit, in milliseconds. */
    private long lastHitTime = 0;

    /** Timestamp at which the stun effect expires, in milliseconds. */
    private long stunEndTime = 0;

    /** Milliseconds of invulnerability granted after taking a hit. */
    private static final int INVULN_MS = 500;

    /** Duration of the stun effect applied after a hit, in milliseconds. */
    private static final int STUN_MS = 120;

    /** Duration of the hit-flash visual effect, in milliseconds. */
    private static final int FLASH_MS = 120;

    /**
     * Creates a new damageable entity with the given starting HP.
     * @param hp the starting (and maximum) hit points
     */
    Damageable(int hp) {
        this.hp    = hp;
        this.maxHp = hp;
    }

    /**
     * Returns whether this entity is currently able to take damage.
     * Entities are immune for {@value #INVULN_MS} ms after each hit.
     * @return {@code true} if the invulnerability window has expired
     */
    boolean canTakeDamage() {
        return System.currentTimeMillis() - lastHitTime > INVULN_MS;
    }

    /**
     * Applies damage to this entity if it is not currently invulnerable.
     * Also starts the stun and flash timers.
     * @param amount the amount of HP to subtract
     */
    void takeDamage(int amount) {
        if (!canTakeDamage()) return;
        long now    = System.currentTimeMillis();
        hp         -= amount;
        lastHitTime = now;
        stunEndTime = now + STUN_MS;
    }

    /**
     * Returns whether this entity is currently stunned (i.e. within the stun window
     * that follows a hit).
     * @return {@code true} if the stun has not yet expired
     */
    boolean isStunned() { return System.currentTimeMillis() < stunEndTime; }

    /**
     * Returns whether this entity should currently display a hit-flash effect.
     * @return {@code true} if within {@value #FLASH_MS} ms of the last hit
     */
    boolean isFlashing() { return System.currentTimeMillis() - lastHitTime < FLASH_MS; }

    /**
     * Returns whether this entity is still alive.
     * @return {@code true} if hp is greater than zero
     */
    boolean alive() { return hp > 0; }
}
