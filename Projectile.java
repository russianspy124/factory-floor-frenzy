/**
 * A projectile fired by the player — either a rapier bolt or a thrown disc.
 * The two types differ in how they expire:
 *   Rapier bolt — removed when its lifespan reaches zero
 *       or it exceeds {@code Player.RAPIER_MAX_RANGE}.
 *   Disc — lifespan is set to MAX_VALUE and removal
 *       is based solely on distance from the spawn point.
 */
class Projectile {

    /** Current world-space position. */
    double x, y;

    /** Velocity in world units per tick. */
    double vx, vy;

    /** World-space position at the moment of creation, used to measure distance travelled. */
    double spawnX, spawnY;

    /** Current rotation angle in radians, incremented each tick for visual spin. */
    double rotation = 0;

    /**
     * Remaining ticks before this projectile expires.
     * Disc projectiles set this to MAX_VALUE and use distance instead.
     */
    int lifespan = 60;

    /** {@code true} if this is a disc projectile; {@code false} if it is a rapier bolt. */
    boolean isDisc;

    /**
     * Creates a rapier bolt projectile that expires by lifespan and range.
     * @param x  spawn X position in world units
     * @param y  spawn Y position in world units
     * @param vx horizontal velocity in world units per tick
     * @param vy vertical velocity in world units per tick
     */
    Projectile(double x, double y, double vx, double vy) {
        this.x      = x;
        this.y      = y;
        this.spawnX = x;
        this.spawnY = y;
        this.vx     = vx;
        this.vy     = vy;
        this.isDisc = false;
    }

    /**
     * Creates a disc projectile that expires only by distance from its spawn point.
     * @param x      spawn X position in world units
     * @param y      spawn Y position in world units
     * @param vx     horizontal velocity in world units per tick
     * @param vy     vertical velocity in world units per tick
     * @param isDisc must be {@code true}; selects the disc expiry behaviour
     */
    Projectile(double x, double y, double vx, double vy, boolean isDisc) {
        this(x, y, vx, vy);
        this.isDisc = isDisc;
        if (isDisc) lifespan = Integer.MAX_VALUE;
    }

    /**
     * Advances this projectile by one tick: moves it along its velocity vector,
     * increments the rotation angle for visual spin, and decrements the lifespan
     * (rapier bolts only).
     */
    void move() {
        x        += vx;
        y        += vy;
        rotation += 0.3;
        if (!isDisc) lifespan--;
    }

    /**
     * Returns the straight-line distance this projectile has travelled from
     * its spawn point.
     * @return distance in world units
     */
    double distanceTravelled() {
        double dx = x - spawnX;
        double dy = y - spawnY;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
