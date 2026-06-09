import java.util.ArrayList;

/**
 * An enemy that chases the player and damages them on contact.
 * Each tick, the enemy moves directly toward the player at MOVE_SPEED
 * and then gently pushes away from any overlapping enemies to prevent clumping.
 */
class Enemy extends Damageable {

    /** World-space position. */
    double x, y;

    /**
     * Straight-line distance to the player, updated every tick by move.
     * Used by Player to find the closest target and detect contact.
     */
    double distToPlayer;

    /** Movement speed in world units per tick. */
    static final double MOVE_SPEED = 0.075;

    /**
     * Minimum distance between two enemies in world units.
     * Enemies push apart when closer than this to avoid stacking.
     */
    static final double MIN_SPACING = 0.5;

    /**
     * Distance to the player, in world units, at which contact damage is applied.
     */
    private static final double CONTACT_DIST = 0.2;

    /** Damage dealt to the player per contact event. */
    static final double DAMAGE = 10;

    /** Collision hitbox, repositioned each tick to follow the enemy. */
    Hitbox hitbox = new Hitbox(0, 0, 100, 100);

    /**
     * Creates a new enemy at the given world-space position.
     * @param hp starting hit points
     * @param x  initial X position in world units
     * @param y  initial Y position in world units
     */
    Enemy(int hp, double x, double y) {
        super(hp);
        this.x = x;
        this.y = y;
    }

    /**
     * Updates this enemy's position for one game tick: moves toward the player,
     * separates from nearby enemies, and syncs the hitbox.
     * @param playerX    player's current X position in world units
     * @param playerY    player's current Y position in world units
     * @param allEnemies the full enemy list, used for separation
     */
    void move(double playerX, double playerY, ArrayList<Enemy> allEnemies) {
        moveTowardPlayer(playerX, playerY);
        separateFromOtherEnemies(allEnemies);
        hitbox.setPosition(x - 25, y - 25);
    }

    /**
     * Returns whether this enemy is close enough to the player to deal contact damage.
     * @return {@code true} if distToPlayer is within {@value #CONTACT_DIST} world units
     */
    boolean isTouchingPlayer() {
        return distToPlayer <= CONTACT_DIST;
    }

    /**
     * Moves this enemy one step toward the player and updates distToPlayer.
     * @param playerX player's X position in world units
     * @param playerY player's Y position in world units
     */
    private void moveTowardPlayer(double playerX, double playerY) {
        double dx = playerX - x;
        double dy = playerY - y;
        distToPlayer = Math.sqrt(dx * dx + dy * dy);

        if (distToPlayer > 0) {
            x += (dx / distToPlayer) * MOVE_SPEED;
            y += (dy / distToPlayer) * MOVE_SPEED;
        }
    }

    /**
     * Pushes this enemy away from any other enemy that is closer than {@value #MIN_SPACING}
     * world units. Each pair shares the correction equally.
     * @param allEnemies the full enemy list (this enemy is skipped)
     */
    private void separateFromOtherEnemies(ArrayList<Enemy> allEnemies) {
        for (Enemy other : allEnemies) {
            if (other == this) continue;

            double dx   = x - other.x;
            double dy   = y - other.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > 0 && dist < MIN_SPACING) {
                double overlap = MIN_SPACING - dist;
                x += (dx / dist) * overlap * 0.5;
                y += (dy / dist) * overlap * 0.5;
            }
        }
    }
}
