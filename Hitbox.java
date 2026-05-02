import java.awt.*;

class Hitbox {
    double x, y;
    int w, h;
    Hitbox(double x, double y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }
    Rectangle getRect() { return new Rectangle((int)x, (int)y, w, h); }
    boolean intersects(Hitbox other) {
        return getRect().intersects(other.getRect());
    }
    void setPosition(double x, double y) {
        this.x = x; this.y = y;
    }
}