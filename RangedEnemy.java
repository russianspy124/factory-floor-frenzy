import java.util.ArrayList;

/**
 * A ranged enemy that approaches the player until within firing range, then
 * stops and periodically fires projectiles toward the player.
 * <p>
 * Behaviour:
 * - Walk toward the player while distToPlayer > FIRE_RANGE.
 * - Stand still and shoot once per FIRE_COOLDOWN ticks while in range.
 * - Gently separates from overlapping enemies to prevent clumping.
 * <p>
 * Projectiles fired by this enemy are stored in {@link #projectiles} and
 * managed (moved, drawn, collision-checked) by Map each tick.
 */
class RangedEnemy extends Enemy {

    /**
     * World units at which this enemy stops walking and starts shooting.
     */
    static final double FIRE_RANGE = 5.0;

    /**
     * Ticks between shots.
     */
    private static final int FIRE_COOLDOWN = 120;

    /**
     * Speed of fired projectiles in world units per tick.
     */
    private static final double PROJECTILE_SPEED = 0.12;

    /**
     * Damage each projectile deals to the player.
     */
    static final double PROJECTILE_DAMAGE = 8;

    /**
     * Ticks until this enemy may fire again.
     */
    int fireCooldown = 60; // initial offset so it doesn't fire immediately on spawn

    /**
     * Live projectiles this enemy has fired; managed externally by Map.
     */
    ArrayList<EnemyProjectile> projectiles = new ArrayList<>();

    RangedEnemy(int hp, double x, double y) {
        super(hp, x, y);
    }

    /**
     * Each tick: if out of range walk closer, otherwise hold position and
     * count down the fire cooldown.  Separation from other enemies always applies.
     */
    @Override
    void move(double playerX, double playerY, ArrayList<Enemy> allEnemies) {
        double dx = playerX - x;
        double dy = playerY - y;
        distToPlayer = Math.sqrt(dx * dx + dy * dy);

        if (distToPlayer > FIRE_RANGE) {
            // approach
            if (distToPlayer > 0) {
                x += (dx / distToPlayer) * MOVE_SPEED;
                y += (dy / distToPlayer) * MOVE_SPEED;
            }
        } else {
            // in range — count down cooldown, fire when ready
            if (fireCooldown > 0) {
                fireCooldown--;
            } else {
                fireAt(playerX, playerY);
                fireCooldown = FIRE_COOLDOWN;
                isAttacking = true;
                attackAnimTicks = 50;
            }
        }

        if (attackAnimTicks > 0) attackAnimTicks--;
        else isAttacking = false;

        separateFromEnemies(allEnemies);
        hitbox.setPosition(x - 25, y - 25);
    }

    private void fireAt(double targetX, double targetY) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist == 0) return;
        projectiles.add(new EnemyProjectile(
                x, y,
                (dx / dist) * PROJECTILE_SPEED,
                (dy / dist) * PROJECTILE_SPEED
        ));
    }

    /**
     * Pushes this enemy away from any other enemy closer than MIN_SPACING.
     * Extracted here because the parent's version is private.
     */
    private void separateFromEnemies(ArrayList<Enemy> allEnemies) {
        for (Enemy other : allEnemies) {
            if (other == this) continue;
            double dx = x - other.x;
            double dy = y - other.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0 && dist < MIN_SPACING) {
                double overlap = MIN_SPACING - dist;
                x += (dx / dist) * overlap * 0.5;
                y += (dy / dist) * overlap * 0.5;
            }
        }
    }
}
