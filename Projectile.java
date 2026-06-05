class Projectile {
    double x, y, vx, vy, rotation;
    int size = 40, lifespan = 60;
    boolean isDisc = false;

    // Rapier projectile
    Projectile(double startX, double startY, double vx, double vy) {
        this.x = startX;
        this.y = startY;
        this.vx = vx;
        this.vy = vy;
    }

    // Disc projectile — higher speed, no lifespan (removed by distance instead)
    Projectile(double startX, double startY, double vx, double vy, boolean isDisc) {
        this(startX, startY, vx, vy);
        this.isDisc = isDisc;
        if (isDisc) lifespan = Integer.MAX_VALUE; // distance-based removal instead
    }

    void extend() {
        x += vx;
        y += vy;
        rotation += 0.3;
        if (!isDisc) lifespan--;
    }
}
