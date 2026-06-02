import java.util.ArrayList;

class Enemy extends Damageable {

    double x, y;

    double kbX = 0, kbY = 0;

    double MOVESPEED = 0.075;

    double dist;

    double damage = 10;

    Hitbox hitbox = new Hitbox(x, y, 100, 100);

    Enemy(int hp, double x, double y) {

        super(hp);

        this.x = x;

        this.y = y;
    }

    void move(double playerX, double playerY, ArrayList<Enemy> enemies) {

        // Move toward player
        double distSq = Math.pow(playerX - this.x, 2)
                + Math.pow(playerY - this.y, 2);

        this.dist = Math.sqrt(distSq);

        if (this.dist > 0) {
            this.x += ((playerX - this.x) / this.dist * MOVESPEED);
            this.y += ((playerY - this.y) / this.dist * MOVESPEED);
        }

        // Prevent enemies from overlapping
        double minDistance = 0.5;

        for (Enemy enemy : enemies) {

            if (enemy == this) {
                continue;
            }

            double dx = this.x - enemy.x;
            double dy = this.y - enemy.y;

            double separation = Math.sqrt(dx * dx + dy * dy);

            if (separation > 0 && separation < minDistance) {

                double overlap = minDistance - separation;

                this.x += (dx / separation) * overlap * 0.5;
                this.y += (dy / separation) * overlap * 0.5;
            }
        }

        this.hitbox.setPosition(this.x - 25, this.y - 25);
    }
}
