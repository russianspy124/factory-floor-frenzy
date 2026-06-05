/**
 * Contains:
 * - Swing attack animation
 * - Stab attack animation
 */

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;

public class AttackAnimations {
    /* =========================================================
     * PLAYER REFERENCE
     * ========================================================= */

    private int playerX;
    private int playerY;

    public AttackAnimations(int playerX, int playerY) {
        this.playerX = playerX;
        this.playerY = playerY;
    }

    public void setPlayerPosition(int x, int y) {
        this.playerX = x;
        this.playerY = y;
    }

    /* =========================================================
     * SWING ATTACK
     * ========================================================= */

    /** True while the weapon is rotating. */
    private boolean swinging = false;

    /** Current weapon rotation angle. */
    private double angle;

    /** Starting and ending swing angles. */
    private double startAngle;
    private double endAngle;

    /** Distance from player center to hand. */
    private final int handOffset = 40;

    /** Weapon dimensions. */
    private final int weaponWidth  = 125;
    private final int weaponHeight = 90;

    /** Hit radius of the scythe blade in screen pixels. */
    private final int swingHitRadius = 130;

    /** Enemies already hit during the current swing. */
    private final HashSet<Enemy> swingHitEnemies = new HashSet<>();

    /**
     * Begins a swing animation.
     *
     * @param facingAngle direction the player is facing (radians)
     */
    public void startSwing(double facingAngle) {
        startAngle = facingAngle - Math.PI / 2;
        endAngle   = facingAngle + Math.PI / 2;
        angle = startAngle;
        swinging = true;
        swingHitEnemies.clear();
    }

    /**
     * Updates swing animation.
     */
    public void updateSwing() {
        if (!swinging)
            return;

        angle += 0.25;

        if (angle >= endAngle) {
            swinging = false;
            swingHitEnemies.clear();
        }
    }

    /**
     * Checks which enemies are struck by the current swing frame and damages them.
     * Call once per tick while the scythe is active.
     *
     * @param enemies       live enemy list
     * @param enemyScreenX  screen-pixel X for each enemy (parallel array)
     * @param enemyScreenY  screen-pixel Y for each enemy (parallel array)
     * @param damage        damage to apply on hit
     */
    public void checkSwingHits(ArrayList<Enemy> enemies,
                               int[] enemyScreenX, int[] enemyScreenY,
                               int damage) {
        if (!swinging)
            return;

        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (swingHitEnemies.contains(enemy))
                continue;

            int dx = enemyScreenX[i] - playerX;
            int dy = enemyScreenY[i] - playerY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > swingHitRadius)
                continue;

            double enemyAngle = Math.atan2(dy, dx);

            // Normalise enemy angle into the [startAngle, endAngle] window
            double normAngle = enemyAngle;
            while (normAngle < startAngle) normAngle += 2 * Math.PI;
            while (normAngle > startAngle + 2 * Math.PI) normAngle -= 2 * Math.PI;

            if (normAngle >= startAngle && normAngle <= endAngle) {
                enemy.takeDamage(damage);
                swingHitEnemies.add(enemy);
            }
        }
    }

    /**
     * Draws the swinging weapon.
     */
    public void drawSwing(Graphics2D g2d, double facingAngle) {
        if (!swinging)
            return;

        AffineTransform old = g2d.getTransform();

        int handX = playerX + (int)(Math.cos(facingAngle) * handOffset);
        int handY = playerY + (int)(Math.sin(facingAngle) * handOffset);

        g2d.translate(handX, handY);
        g2d.rotate(angle);

        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, -weaponHeight / 2, weaponWidth, weaponHeight);

        g2d.setTransform(old);
    }

    public boolean isSwinging() {
        return swinging;
    }

    /* =========================================================
     * STAB ATTACK
     * ========================================================= */

    private boolean stabActive = false;

    private long stabStartTime;

    /** Windup before thrust begins. */
    private final int stabWindup = 120;

    /** Forward movement duration. */
    private final int stabDuration = 160;

    /** Direction of stab. */
    private double stabAngle;

    /** Current extension percentage (0-1). */
    private float stabProgress;

    /**
     * Begins a stab attack.
     *
     * @param angle direction of stab in radians
     */
    public void startStab(double angle) {
        stabActive = true;
        stabAngle = angle;
        stabStartTime = System.currentTimeMillis();
    }

    /**
     * Updates stab progression.
     */
    public void updateStab() {
        if (!stabActive)
            return;

        long elapsed = System.currentTimeMillis() - stabStartTime;

        if (elapsed < stabWindup) {
            stabProgress = 0;
        } else if (elapsed < stabWindup + stabDuration) {
            stabProgress = (float)(elapsed - stabWindup) / stabDuration;
        } else {
            stabProgress = 0;
            stabActive = false;
        }
    }

    /**
     * Draws stabbing weapon.
     */
    public void drawStab(Graphics2D g2d) {
        if (!stabActive)
            return;

        AffineTransform old = g2d.getTransform();

        double reach = 20 + (80 * stabProgress);

        int x = playerX + (int)(Math.cos(stabAngle) * reach);
        int y = playerY + (int)(Math.sin(stabAngle) * reach);

        g2d.translate(x, y);
        g2d.rotate(stabAngle);

        g2d.setColor(new Color(0, 255, 255, 160));
        g2d.fillRect(0, -15, 100, 30);

        g2d.setTransform(old);
    }

    public boolean isStabbing() {
        return stabActive;
    }
}
