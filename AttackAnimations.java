import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import javax.imageio.ImageIO;

/**
 * Manages the visual and hit-detection logic for the player's two melee attacks:
 * the scythe swing and the rapier stab.
 * This class works in screen space (pixels). The owning DrawPanel
 * calls setPlayerPosition once per frame to keep the origin in sync with the
 * player's on-screen centre before any drawing or hit-check calls are made.
 * Both animations are independent and can technically be active at the same time,
 * though the game only starts one per attack. cancelAll stops both
 * immediately, used when the player dashes mid-attack.
 */
class AttackAnimations {

    /** Screen-space X coordinate of the player's centre. Updated every frame. */
    private int playerX;

    /** Screen-space Y coordinate of the player's centre. Updated every frame. */
    private int playerY;

    /** Scythe sprite, loaded once at construction. */
    private final BufferedImage scytheSprite = loadSprite("scythe.png");

    /** Rapier sprite, loaded once at construction. */
    private final BufferedImage rapierSprite = loadSprite("rapierThrust.png");

    private static BufferedImage loadSprite(String filename) {
        try { return ImageIO.read(new File(filename)); }
        catch (IOException e) { return null; }
    }

    /**
     * Creates an AttackAnimations instance anchored to the given screen position.
     * @param playerX initial screen-space X of the player centre
     * @param playerY initial screen-space Y of the player centre
     */
    AttackAnimations(int playerX, int playerY) {
        this.playerX = playerX;
        this.playerY = playerY;
    }

    /**
     * Moves the player-centre anchor used for all drawing and hit detection.
     * Must be called once per frame before any draw or hit-check method.
     * @param x screen-space X of the player centre
     * @param y screen-space Y of the player centre
     */
    void setPlayerPosition(int x, int y) {
        playerX = x;
        playerY = y;
    }

    // -------------------------------------------------------------------------
    // Scythe swing
    // -------------------------------------------------------------------------

    /** Pixels from the player centre to where the hand grips the weapon. */
    private static final int HAND_OFFSET = 40;

    /** Width of the scythe weapon rectangle in pixels. */
    private static final int WEAPON_WIDTH = 125;

    /** Height of the scythe weapon rectangle in pixels. */
    private static final int WEAPON_HEIGHT = 90;

    /** Radius in screen pixels within which the swinging blade can hit enemies. */
    private static final int SWING_HIT_RADIUS = 130;

    /** Rotation speed of the swing in radians per tick. */
    private static final double SWING_SPEED = 0.25;

    /** {@code true} while the scythe is mid-swing. */
    private boolean swinging = false;

    /** Current angle of the weapon, in radians. */
    private double swingAngle;

    /** Angle at which the swing begins, in radians. */
    private double swingStart;

    /** Angle at which the swing ends, in radians. */
    private double swingEnd;

    /**
     * Enemies struck during the current swing. Prevents a fast-moving weapon
     * from hitting the same enemy more than once per swing.
     */
    private final HashSet<Enemy> alreadyHitThisSwing = new HashSet<>();

    /**
     * Starts a new scythe swing centred on the given direction.
     * The arc spans 180° (±90° from the facing angle).
     * @param facingAngle the direction the player is facing, in radians
     */
    void startSwing(double facingAngle) {
        swingStart = facingAngle - Math.PI / 2;
        swingEnd   = facingAngle + Math.PI / 2;
        swingAngle = swingStart;
        swinging   = true;
        alreadyHitThisSwing.clear();
    }

    /**
     * Advances the swing animation by one tick.
     * The swing ends automatically when the blade reaches swingEnd.
     */
    void updateSwing() {
        if (!swinging) return;
        swingAngle += SWING_SPEED;
        if (swingAngle >= swingEnd) {
            swinging = false;
            alreadyHitThisSwing.clear();
        }
    }

    /**
     * Checks each enemy against the current swing arc and applies damage to any
     * that fall within SWING_HIT_RADIUS pixels and within the angular window.
     * Each enemy can only be hit once per swing.
     * @param enemies      the live enemy list
     * @param enemyScreenX screen-space X for each enemy (parallel to {@code enemies})
     * @param enemyScreenY screen-space Y for each enemy (parallel to {@code enemies})
     * @param damage       HP to subtract from each enemy that is struck
     * @param facingAngle  the direction the player is facing, used to locate the grip pivot
     */
    void checkSwingHits(ArrayList<Enemy> enemies, int[] enemyScreenX, int[] enemyScreenY, int damage, double facingAngle) {
        if (!swinging) return;

        // Use the grip point as the pivot to match drawSwing's visual origin
        int gripX = playerX + (int) (Math.cos(facingAngle) * HAND_OFFSET);
        int gripY = playerY + (int) (Math.sin(facingAngle) * HAND_OFFSET);

        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (alreadyHitThisSwing.contains(enemy)) continue;

            int    dx   = enemyScreenX[i] - gripX;
            int    dy   = enemyScreenY[i] - gripY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > SWING_HIT_RADIUS) continue;

            // Normalise the enemy's angle into the [swingStart, swingEnd] window
            double angle = Math.atan2(dy, dx);
            while (angle < swingStart)                angle += 2 * Math.PI;
            while (angle > swingStart + 2 * Math.PI)  angle -= 2 * Math.PI;

            if (angle >= swingStart && angle <= swingEnd) {
                enemy.takeDamage(damage);
                alreadyHitThisSwing.add(enemy);
            }
        }
    }

    /**
     * Draws the swinging scythe for the current frame.
     * Does nothing if no swing is active.
     * @param g2d         the graphics context to draw into
     * @param facingAngle the direction the player is facing, used to position the grip
     */
    void drawSwing(Graphics2D g2d, double facingAngle) {
        if (!swinging) return;

        AffineTransform saved = g2d.getTransform();
        int gripX = playerX + (int) (Math.cos(facingAngle) * HAND_OFFSET);
        int gripY = playerY + (int) (Math.sin(facingAngle) * HAND_OFFSET);

        g2d.translate(gripX, gripY);
        g2d.rotate(swingAngle);
        if (scytheSprite != null) {
            g2d.drawImage(scytheSprite, 0, -WEAPON_HEIGHT / 2, WEAPON_WIDTH, WEAPON_HEIGHT, null);
        } else {
            g2d.setColor(Color.GRAY);
            g2d.fillRect(0, -WEAPON_HEIGHT / 2, WEAPON_WIDTH, WEAPON_HEIGHT);
        }
        g2d.setTransform(saved);
    }

    /**
     * Returns whether a scythe swing is currently active.
     * @return {@code true} if the swing animation is playing
     */
    boolean isSwinging() { return swinging; }

    // -------------------------------------------------------------------------
    // Rapier stab
    // -------------------------------------------------------------------------

    /** Milliseconds of windup before the rapier thrust begins extending. */
    private static final int STAB_WINDUP_MS = 120;

    /** Milliseconds the thrust takes to fully extend from retracted to maximum reach. */
    private static final int STAB_DURATION_MS = 160;

    /** {@code true} while the stab animation is playing (windup + thrust). */
    private boolean stabActive = false;

    /** System time in milliseconds when the stab was started. */
    private long stabStartTime;

    /** Direction of the stab, in radians. */
    private double stabAngle;

    /**
     * How far the blade has extended, from 0.0 (retracted) to 1.0 (fully extended).
     * Used to interpolate the tip position each frame.
     */
    private float stabProgress;

    /**
     * Starts a rapier stab animation in the given direction.
     * @param angle direction of the thrust, in radians
     */
    void startStab(double angle) {
        stabActive    = true;
        stabAngle     = angle;
        stabStartTime = System.currentTimeMillis();
    }

    /**
     * Advances the stab animation by one tick, updating stabProgress.
     * The animation ends automatically after the windup plus thrust duration.
     */
    void updateStab() {
        if (!stabActive) return;

        long elapsed = System.currentTimeMillis() - stabStartTime;

        if (elapsed < STAB_WINDUP_MS) {
            stabProgress = 0f;
        } else if (elapsed < STAB_WINDUP_MS + STAB_DURATION_MS) {
            stabProgress = (float) (elapsed - STAB_WINDUP_MS) / STAB_DURATION_MS;
        } else {
            stabProgress = 0f;
            stabActive   = false;
        }
    }

    /**
     * Draws the rapier blade at its current extension for this frame.
     * Does nothing if the stab animation is not active.
     * @param g2d the graphics context to draw into
     */
    void drawStab(Graphics2D g2d) {
        if (!stabActive) return;
        AffineTransform saved = g2d.getTransform();

        // Translate to player centre, rotate to face the stab direction.
        // The sprite is drawn with x=0 at the handle (player side) extending
        // to x=rw at the tip. Thrust animates the whole sprite forward by up
        // to 40px so the blade visibly lunges outward.
        int rw = 100, rh = 30;
        double thrust = 40 * stabProgress;
        g2d.translate(playerX, playerY);
        g2d.rotate(stabAngle);

        if (rapierSprite != null) {
            g2d.drawImage(rapierSprite, (int) thrust, -rh / 2, rw, rh, null);
            
        } else {
            g2d.setColor(new Color(0, 255, 255, 160));
            g2d.fillRect((int) thrust, -rh / 2, rw, rh);
        }

        g2d.setTransform(saved);
    }

    /**
     * Returns whether a rapier stab animation is currently active.
     * @return {@code true} if the stab animation is playing
     */
    boolean isStabbing() { return stabActive; }

    /**
     * Checks each enemy against the current stab and applies damage to any that
     * fall within the blade's reach. Only tests during the active thrust phase
     * (stabProgress > 0). Each enemy can only be hit once per stab.
     * @param enemies      the live enemy list
     * @param enemyScreenX screen-space X for each enemy (parallel to {@code enemies})
     * @param enemyScreenY screen-space Y for each enemy (parallel to {@code enemies})
     * @param damage       HP to subtract from each enemy that is struck
     */
    void checkStabHits(ArrayList<Enemy> enemies, int[] enemyScreenX, int[] enemyScreenY, int damage) {
        if (!stabActive || stabProgress <= 0f) return;

        // Mirror drawStab's geometry: blade occupies x=[thrust, thrust+rw] along stabAngle.
        // Use the tip (furthest point) as the hit origin with a generous radius.
        int rw = 100;
        double thrust = 40 * stabProgress;
        double tipDist = thrust + rw;
        int tipX = playerX + (int) (Math.cos(stabAngle) * tipDist);
        int tipY = playerY + (int) (Math.sin(stabAngle) * tipDist);

        // Hit radius covers roughly the front half of the blade
        int hitRadius = rw / 2 + 20;

        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            int dx = enemyScreenX[i] - tipX;
            int dy = enemyScreenY[i] - tipY;
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                enemy.takeDamage(damage);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared
    // -------------------------------------------------------------------------

    /**
     * Immediately cancels all active animations and resets all related state.
     * Called when the player dashes mid-attack.
     */
    void cancelAll() {
        swinging     = false;
        stabActive   = false;
        stabProgress = 0f;
        alreadyHitThisSwing.clear();
    }
}
