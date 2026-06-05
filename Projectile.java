class Projectile {

    double x, y;
    double vx, vy;
    double spawnX, spawnY;
    double rotation = 0;
    int lifespan = 60;
    boolean isDisc;

    // Rapier bolt — removed by lifespan and max distance
    Projectile(double x, double y, double vx, double vy) {
        this.x = x;
        this.y = y;
        this.spawnX = x;
        this.spawnY = y;
        this.vx = vx;
        this.vy = vy;
        this.isDisc = false;
    }

    // Disc — removed by distance only, so lifespan is set to max
    Projectile(double x, double y, double vx, double vy, boolean isDisc) {
        this(x, y, vx, vy);
        this.isDisc = isDisc;
        if (isDisc) lifespan = Integer.MAX_VALUE;
    }

    void move() {
        x += vx;
        y += vy;
        rotation += 0.3;
        if (!isDisc) lifespan--;
    }

    double distanceTravelled() {
        double dx = x - spawnX;
        double dy = y - spawnY;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
