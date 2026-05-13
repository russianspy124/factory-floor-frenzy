class Player extends Damageable{
    double x, y;
    double kbX = 0, kbY = 0;

    Hitbox hitbox = new Hitbox(0, 0, 100, 100);
    //    Damageable hp = new Damageable(50);

    Player(int hp) {
        super(hp);
    }
}