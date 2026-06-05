import java.awt.*;

class Hitbox {

    double x, y;
    int width, height;

    Hitbox(double x, double y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    boolean intersects(Hitbox other) {
        return toRect().intersects(other.toRect());
    }

    private Rectangle toRect() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
