import java.util.ArrayList;

class Enemy extends Damageable {

    double x, y;
    double distToPlayer;

    private static final double MOVE_SPEED = 0.075;
    private static final double MIN_SPACING = 0.5;   // world units — enemies won't overlap closer than this
    private static final double CONTACT_DIST = 0.2;   // world units — close enough to damage the player
    static final double DAMAGE = 10;

    Hitbox hitbox = new Hitbox(0, 0, 100, 100);

    Enemy(int hp, double x, double y) {
        super(hp);
        this.x = x;
        this.y = y;
    }

    void move(double playerX, double playerY, ArrayList<Enemy> allEnemies) {
        moveTowardPlayer(playerX, playerY);
        separateFromOtherEnemies(allEnemies);
        hitbox.setPosition(x - 25, y - 25);
    }

    boolean isTouchingPlayer() {
        return distToPlayer <= CONTACT_DIST;
    }

    private void moveTowardPlayer(double playerX, double playerY) {
        double dx = playerX - x;
        double dy = playerY - y;
        distToPlayer = Math.sqrt(dx * dx + dy * dy);

        if (distToPlayer > 0) {
            x += (dx / distToPlayer) * MOVE_SPEED;
            y += (dy / distToPlayer) * MOVE_SPEED;
        }
    }

    private void separateFromOtherEnemies(ArrayList<Enemy> allEnemies) {
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
