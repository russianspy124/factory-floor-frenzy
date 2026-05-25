class Projectile {
    double x = 0, y = 0, vx, vy, rotation;
    int size = 40, lifespan = 10;

    Projectile(double vx, double vy) {
        this.vx = vx;
        this.vy = vy;

    }

    void extend() {
        x += vx;
        y += vy;
        lifespan -= 1;
    }

}
