import java.awt.*;

/**
 * An axis-aligned rectangular region in world space used for collision detection.
 * Coordinates are stored as doubles to match the world-space positions of
 * game entities, but are cast to {@code int} when creating the underlying
 * Rectangle for intersection tests.
 */
class Hitbox {

    /** World-space X position of the top-left corner. */
    double x, y;

    /** Width and height of the hitbox in world-space units. */
    int width, height;

    /**
     * Creates a hitbox at the given position with the given dimensions.
     * @param x      initial X position (top-left corner)
     * @param y      initial Y position (top-left corner)
     * @param width  width in world-space units
     * @param height height in world-space units
     */
    Hitbox(double x, double y, int width, int height) {
        this.x      = x;
        this.y      = y;
        this.width  = width;
        this.height = height;
    }

    /**
     * Moves the hitbox to a new top-left position, typically called each tick
     * to keep it in sync with the owning entity's world position.
     * @param x new X position
     * @param y new Y position
     */
    void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Tests whether this hitbox overlaps with another.
     * @param other the hitbox to test against
     * @return {@code true} if the two rectangles intersect
     */
    boolean intersects(Hitbox other) {
        return toRect().intersects(other.toRect());
    }

    /**
     * Converts this hitbox to an AWT Rectangle for use with
     * standard intersection methods.
     * @return a Rectangle representing this hitbox
     */
    private Rectangle toRect() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
