class Enemy extends Damageable {
    double x, y;
    double kbX = 0, kbY = 0;
    double MOVESPEED = 0.075;
    double dist;

    Hitbox hitbox = new Hitbox(x, y, 100, 100);
//    Damageable hp = new Damageable(50);

    Enemy(int hp, double x, double y) {
        super(hp);
        this.x = x;
        this.y = y;
    }

    void move(double playerX, double playerY) {
        double dist = Math.pow((playerX - this.x), 2) + Math.pow((playerY - this.y), 2);
        this.dist = Math.sqrt(dist);
        this.x += ((playerX - this.x) / this.dist * MOVESPEED);
        this.y += ((playerY - this.y) / this.dist * MOVESPEED);
        this.hitbox.setPosition(this.x - 25, this.y - 25);

    }
}