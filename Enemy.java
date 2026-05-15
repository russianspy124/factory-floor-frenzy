class Enemy extends Damageable{
    double x, y;
    double kbX = 0, kbY = 0;
    double MOVESPEED= 0.075;

    Hitbox hitbox = new Hitbox(x,y,100,100);
//    Damageable hp = new Damageable(50);

    Enemy(int hp, double x, double y) {
        super(hp);
        this.x=x;
        this.y=y;
    }
    void move(double playerX,double playerY){
        double resultVect = Math.pow(playerX-this.x, 2)+Math.pow(playerY-this.y,2);
        resultVect=Math.sqrt(resultVect);
        this.x+=((playerX-this.x)/resultVect*MOVESPEED);
        this.y+=((playerY-this.y)/resultVect*MOVESPEED);
        this.hitbox.setPosition(this.x-25, this.y-25);
        
    }
}