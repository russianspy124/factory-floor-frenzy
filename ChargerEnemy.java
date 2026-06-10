import java.util.ArrayList;

/**
 * An enemy that approaches the player until within charge range, then
 * telegraphs a dash with a 1-second charge-up (showing a rectangle in the
 * dash direction), and finally rockets along that rectangle.
 * <p>
 * State machine:
 * APPROACH  — walk toward the player at MOVE_SPEED
 * CHARGING  — stand still, count down CHARGE_TICKS, show dash rectangle
 * DASHING   — move rapidly along the locked dash direction for DASH_TICKS
 * COOLDOWN  — brief pause before approaching again
 */
class ChargerEnemy extends Enemy {

    // --- tuning ---

    /**
     * World units at which the enemy stops approaching and begins charging.
     */
    static final double CHARGE_RANGE = 4.0;

    /**
     * Ticks for the charge-up telegraph (100 ticks ≈ 1 second at 10 ms/tick).
     */
    private static final int CHARGE_TICKS = 100;

    /**
     * World units per tick during the dash.
     */
    private static final double DASH_SPEED = 0.55;

    /**
     * Maximum world units the dash travels before stopping.
     */
    private static final double DASH_DISTANCE = 5.0;

    /**
     * Ticks to wait in cooldown before approaching again.
     */
    private static final int COOLDOWN_TICKS = 60;

    /**
     * Damage dealt to the player per tick while dashing through them.
     */
    static final double DASH_DAMAGE = 15;

    // --- state ---

    enum State {APPROACH, CHARGING, DASHING, COOLDOWN}

    State state = State.APPROACH;

    /**
     * Ticks remaining in the current CHARGING phase.
     */
    int chargeTicks = 0;

    /**
     * Ticks remaining in the current DASHING phase.
     */
    double dashRemaining = 0;

    /**
     * Ticks remaining in the current COOLDOWN phase.
     */
    int cooldownTicks = 0;

    /**
     * Direction of the locked dash (unit vector). Set at the start of CHARGING.
     */
    double dashDirX = 0, dashDirY = 0;

    /**
     * World-space origin of the current dash. Used with dashDirX/Y to draw
     * the telegraph rectangle in Map's drawEnemies.
     */
    double dashOriginX, dashOriginY;

    ChargerEnemy(int hp, double x, double y) {
        super(hp, x, y);
    }

    @Override
    void move(double playerX, double playerY, ArrayList<Enemy> allEnemies) {
        double dx = playerX - x;
        double dy = playerY - y;
        distToPlayer = Math.sqrt(dx * dx + dy * dy);

        switch (state) {

            case APPROACH:
                if (distToPlayer <= CHARGE_RANGE) {
                    // Lock dash direction toward player NOW, before charging starts
                    double len = distToPlayer > 0 ? distToPlayer : 1;
                    dashDirX = dx / len;
                    dashDirY = dy / len;
                    dashOriginX = x;
                    dashOriginY = y;
                    chargeTicks = CHARGE_TICKS;
                    state = State.CHARGING;
                } else {
                    if (distToPlayer > 0) {
                        x += (dx / distToPlayer) * MOVE_SPEED;
                        y += (dy / distToPlayer) * MOVE_SPEED;
                    }
                }
                break;

            case CHARGING:
                // Update origin so the rect follows position (enemy stands still)
                dashOriginX = x;
                dashOriginY = y;
                if (--chargeTicks <= 0) {
                    dashRemaining = DASH_DISTANCE;
                    state = State.DASHING;
                }
                break;

            case DASHING:
                double step = Math.min(DASH_SPEED, dashRemaining);
                x += dashDirX * step;
                y += dashDirY * step;
                dashRemaining -= step;
                if (dashRemaining <= 0) {
                    cooldownTicks = COOLDOWN_TICKS;
                    state = State.COOLDOWN;
                }
                isAttacking = true;
                attackAnimTicks = 20;
                break;

            case COOLDOWN:
                if (--cooldownTicks <= 0) state = State.APPROACH;
                break;
        }

        if (attackAnimTicks > 0) attackAnimTicks--;
        else if (state != State.DASHING) isAttacking = false;

        separateFromEnemies(allEnemies);
        hitbox.setPosition(x - 25, y - 25);
    }

    /**
     * Returns true only while this enemy is actively dashing.
     */
    boolean isDashing() {
        return state == State.DASHING;
    }

    /**
     * Returns true while the charge telegraph should be drawn.
     */
    boolean isCharging() {
        return state == State.CHARGING;
    }

    /**
     * 0→1 progress of the charge-up, used to animate the telegraph.
     */
    float chargeProgress() {
        return 1f - (float) chargeTicks / CHARGE_TICKS;
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
