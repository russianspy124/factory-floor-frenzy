import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;

class AttackAnimations {

    // Screen-space position of the player center, kept in sync each frame
    private int playerX;
    private int playerY;

    AttackAnimations(int playerX, int playerY) {
        this.playerX = playerX;
        this.playerY = playerY;
    }

    void setPlayerPosition(int x, int y) {
        playerX = x;
        playerY = y;
    }

    // -------------------------------------------------------------------------
    // Scythe swing
    // -------------------------------------------------------------------------

    private static final int HAND_OFFSET = 40;  // px from player center to grip
    private static final int WEAPON_WIDTH = 125;
    private static final int WEAPON_HEIGHT = 90;
    private static final int SWING_HIT_RADIUS = 130; // px — how far the blade reaches
    private static final double SWING_SPEED = 0.25; // radians per tick

    private boolean swinging = false;
    private double swingAngle;
    private double swingStart;
    private double swingEnd;
    private final HashSet<Enemy> alreadyHitThisSwing = new HashSet<>();

    void startSwing(double facingAngle) {
        swingStart = facingAngle - Math.PI / 2;
        swingEnd = facingAngle + Math.PI / 2;
        swingAngle = swingStart;
        swinging = true;
        alreadyHitThisSwing.clear();
    }

    void updateSwing() {
        if (!swinging) return;
        swingAngle += SWING_SPEED;
        if (swingAngle >= swingEnd) {
            swinging = false;
            alreadyHitThisSwing.clear();
        }
    }

    void checkSwingHits(ArrayList<Enemy> enemies, int[] enemyScreenX, int[] enemyScreenY, int damage) {
        if (!swinging) return;

        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (alreadyHitThisSwing.contains(enemy)) continue;

            int dx = enemyScreenX[i] - playerX;
            int dy = enemyScreenY[i] - playerY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > SWING_HIT_RADIUS) continue;

            // Check the enemy falls within the current arc
            double angle = Math.atan2(dy, dx);
            while (angle < swingStart) angle += 2 * Math.PI;
            while (angle > swingStart + 2 * Math.PI) angle -= 2 * Math.PI;

            if (angle >= swingStart && angle <= swingEnd) {
                enemy.takeDamage(damage);
                alreadyHitThisSwing.add(enemy);
            }
        }
    }

    void drawSwing(Graphics2D g2d, double facingAngle) {
        if (!swinging) return;

        AffineTransform saved = g2d.getTransform();
        int gripX = playerX + (int) (Math.cos(facingAngle) * HAND_OFFSET);
        int gripY = playerY + (int) (Math.sin(facingAngle) * HAND_OFFSET);

        g2d.translate(gripX, gripY);
        g2d.rotate(swingAngle);
        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, -WEAPON_HEIGHT / 2, WEAPON_WIDTH, WEAPON_HEIGHT);
        g2d.setTransform(saved);
    }

    boolean isSwinging() {
        return swinging;
    }

    // -------------------------------------------------------------------------
    // Rapier stab
    // -------------------------------------------------------------------------

    private static final int STAB_WINDUP_MS = 120; // ms before the thrust begins
    private static final int STAB_DURATION_MS = 160; // ms the thrust takes to fully extend

    private boolean stabActive = false;
    private long stabStartTime;
    private double stabAngle;
    private float stabProgress; // 0.0 = retracted, 1.0 = fully extended

    void startStab(double angle) {
        stabActive = true;
        stabAngle = angle;
        stabStartTime = System.currentTimeMillis();
    }

    void updateStab() {
        if (!stabActive) return;

        long elapsed = System.currentTimeMillis() - stabStartTime;

        if (elapsed < STAB_WINDUP_MS) {
            stabProgress = 0f;
        } else if (elapsed < STAB_WINDUP_MS + STAB_DURATION_MS) {
            stabProgress = (float) (elapsed - STAB_WINDUP_MS) / STAB_DURATION_MS;
        } else {
            stabProgress = 0f;
            stabActive = false;
        }
    }

    void drawStab(Graphics2D g2d) {
        if (!stabActive) return;

        AffineTransform saved = g2d.getTransform();
        double reach = 20 + (80 * stabProgress);
        int tipX = playerX + (int) (Math.cos(stabAngle) * reach);
        int tipY = playerY + (int) (Math.sin(stabAngle) * reach);

        g2d.translate(tipX, tipY);
        g2d.rotate(stabAngle);
        g2d.setColor(new Color(0, 255, 255, 160));
        g2d.fillRect(0, -15, 100, 30);
        g2d.setTransform(saved);
    }

    boolean isStabbing() {
        return stabActive;
    }

    // -------------------------------------------------------------------------
    // Shared
    // -------------------------------------------------------------------------

    // Immediately cancels all animations — called when the player dashes mid-attack
    void cancelAll() {
        swinging = false;
        stabActive = false;
        stabProgress = 0f;
        alreadyHitThisSwing.clear();
    }
}
