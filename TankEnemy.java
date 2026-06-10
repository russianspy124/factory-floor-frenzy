import java.util.ArrayList;

/**
 * A slow, high-HP melee enemy that walks directly toward the player and
 * deals contact damage on touch — like the basic Enemy, but tankier and slower.
 * <p>
 * Visual distinction (handled in Map's drawEnemies): drawn as a larger dark-red oval.
 */
class TankEnemy extends Enemy {

    /**
     * Movement speed — slower than a standard enemy.
     */
    private static final double TANK_MOVE_SPEED = 0.045;

    /**
     * Damage dealt to the player per contact event.
     */
    static final double DAMAGE = 20;

    TankEnemy(int hp, double x, double y) {
        super(hp, x, y);
    }

    @Override
    void move(double playerX, double playerY, ArrayList<Enemy> allEnemies) {
        double dx = playerX - x;
        double dy = playerY - y;
        distToPlayer = Math.sqrt(dx * dx + dy * dy);

        if (distToPlayer > 0) {
            x += (dx / distToPlayer) * TANK_MOVE_SPEED;
            y += (dy / distToPlayer) * TANK_MOVE_SPEED;
        }

        separateFromEnemies(allEnemies);
        hitbox.setPosition(x - 25, y - 25);

        // Trigger attack animation when close to the player
        if (distToPlayer <= 0.6) {
            isAttacking = true;
            attackAnimTicks = 40;
        } else if (attackAnimTicks > 0) {
            attackAnimTicks--;
        } else {
            isAttacking = false;
        }
    }

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
