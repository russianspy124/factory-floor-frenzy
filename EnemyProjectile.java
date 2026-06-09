/**
 * A projectile fired by an enemy (currently only {@link RangedEnemy}).
 * Moves along a fixed velocity vector each tick and expires after a set
 * lifespan.  Collision checking and removal are handled by Map.
 */
class EnemyProjectile {

    /** Current world-space position. */
    double x, y;

    /** Velocity in world units per tick. */
    double vx, vy;

    /** Ticks remaining before this projectile is removed. */
    int lifespan = 200;

    EnemyProjectile(double x, double y, double vx, double vy) {
        this.x  = x;
        this.y  = y;
        this.vx = vx;
        this.vy = vy;
    }

    /** Advances this projectile by one tick. */
    void move() {
        x += vx;
        y += vy;
        lifespan--;
    }

    boolean expired() { return lifespan <= 0; }
}
