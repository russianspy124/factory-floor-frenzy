import java.util.ArrayList;

/**
 * The player character, controlled via keyboard input.
 * Responsibilities managed here:
 *   Tile-collision movement (WASD)
 *   Dash — a short burst of fast movement with built-in i-frames
 *   Attacking with the rapier, scythe, or disc (weaponChoice)
 *   Receiving contact damage from enemies and enforcing i-frame protection
 *   Owning and updating all active Projectile instances
 * The player is always rendered at the centre of the screen; the camera
 * follows by offsetting every other world object relative to x/y.
 */
class Player extends Damageable {

    /** World-space position. The camera is centred on this point. */
    double x, y;

    /**
     * Pending knockback velocity in world units per tick.
     * Currently stored but not yet applied to movement.
     */
    double kbX = 0, kbY = 0;

    /**
     * Active weapon slot.
     *   0 — rapier (fast bolt)
     *   1 — scythe (wide swing arc)
     *   2 — disc   (slow, long-range projectile)
     */
    int weaponChoice = 0;

    /** Ticks remaining until the next attack is allowed. Counts down to zero. */
    int attackCooldown = 0;

    /** Ticks the attack button has been held this press, used to distinguish heavy attacks. */
    int attackHoldCounter = 0;

    /** {@code true} while the attack button is held but not yet released. */
    boolean attackHeld = false;

    /**
     * Angle (radians) from the player toward the last targeted enemy.
     * Updated on each attack and used to orient attack animations.
     */
    double facingAngle = 0;

    /**
     * Angle (radians) of the most recent WASD input direction.
     * Updated each tick by move and used to aim the dash.
     */
    double moveAngle = 0;

    /** {@code true} if at least one movement key was held during the last move call. */
    boolean isMoving = false;

    /**
     * Remaining invincibility frames. While positive the player cannot take damage
     * from enemy contact, and the health bar outline flashes white.
     * Decremented each tick by checkEnemyCollision.
     */
    int iFrames = 0;

    /** Collision hitbox, used for future wall/object interactions. */
    Hitbox hitbox = new Hitbox(0, 0, 100, 100);

    /** All projectiles currently alive in the world, owned by the player. */
    ArrayList<Projectile> projectiles = new ArrayList<>();

    /** Manages the visual swing and stab animations for the equipped weapon. */
    AttackAnimations attackAnimations;

    // --- Weapon range limits (world units) ---

    /** Maximum distance a rapier bolt travels before it disappears. */
    private static final double RAPIER_MAX_RANGE = 3.0;

    /** Maximum distance a disc travels from its spawn point before it disappears. */
    private static final double DISC_MAX_RANGE = 6.0;

    // --- Dash tuning ---

    /** Total world units covered by a single dash. */
    private static final double DASH_DISTANCE = 2.5;

    /** World units moved per tick during a dash. */
    private static final double DASH_SPEED = 0.35;

    /** Ticks the player must wait between dashes. */
    private static final int DASH_COOLDOWN = 120;

    /** {@code true} while the player is actively dashing. */
    boolean dashing = false;

    /** Ticks remaining until the next dash is available. */
    int dashCooldown = 0;

    /** World units still to be covered in the current dash. */
    private double dashRemaining = 0;

    /** Unit vector in the direction of the current dash. */
    private double dashDirX = 0, dashDirY = 0;

    /**
     * Creates a new player with the given starting HP.
     * @param hp starting (and maximum) hit points
     */
    Player(int hp) {
        super(hp);
        attackAnimations = new AttackAnimations(0, 0);
    }

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    /**
     * Moves the player according to held direction keys, checking tile collision
     * before each axis step. Also records the current movement angle for use by
     * startDash.
     * Each direction is tested and applied independently, which lets the player
     * slide along walls when a diagonal is partially blocked.
     * @param up    {@code true} if the up/W key is held
     * @param left  {@code true} if the left/A key is held
     * @param down  {@code true} if the down/S key is held
     * @param right {@code true} if the right/D key is held
     * @param map   the tile grid used for walkability checks (1 = walkable)
     */
    void move(boolean up, boolean left, boolean down, boolean right, int[][] map) {
        double dx = 0, dy = 0;

        if (up)    { dy -= 1; if (walkable(map, x,       y - 0.1)) y -= 0.1; }
        if (down)  { dy += 1; if (walkable(map, x,       y + 1.1)) y += 0.1; }
        if (left)  { dx -= 1; if (walkable(map, x - 0.4, y + 0.3)) x -= 0.1; }
        if (right) { dx += 1; if (walkable(map, x + 0.8, y + 0.3)) x += 0.1; }

        isMoving = (dx != 0 || dy != 0);
        if (isMoving) moveAngle = Math.atan2(dy, dx);
    }

    /**
     * Returns {@code true} if the given world-space point is on a walkable tile.
     * @param map the tile grid (1 = walkable)
     * @param wx  world X coordinate to test
     * @param wy  world Y coordinate to test
     * @return {@code true} if the tile at (wx, wy) is walkable
     */
    private boolean walkable(int[][] map, double wx, double wy) {
        return map[(int) wy][(int) wx] == 1;
    }

    // -------------------------------------------------------------------------
    // Dash
    // -------------------------------------------------------------------------

    /**
     * Begins a dash if one is not already active and the cooldown has expired.
     * The dash direction is the current moveAngle if the player is moving,
     * or facingAngle as a fallback when standing still. Any active attack
     * animation is cancelled immediately, and i-frames are granted for the full
     * dash duration plus a short buffer.
     * @param map the tile grid used for collision during the dash
     */
    void startDash(int[][] map) {
        if (dashing || dashCooldown > 0) return;

        attackAnimations.cancelAll();
        attackHeld        = false;
        attackHoldCounter = 0;

        dashing       = true;
        dashRemaining = DASH_DISTANCE;

        double angle = isMoving ? moveAngle : facingAngle;
        dashDirX = Math.cos(angle);
        dashDirY = Math.sin(angle);

        int dashDuration = (int) Math.ceil(DASH_DISTANCE / DASH_SPEED);
        iFrames = Math.max(iFrames, dashDuration + 5);
    }

    /**
     * Advances the dash by one game tick. Moves the player up to DASH_SPEED
     * world units along the dash direction, with axis-sliding collision so the player
     * glides along walls rather than stopping dead.
     * Must be called every tick regardless of whether a dash is active, because
     * it also decrements dashCooldown.
     * @param map the tile grid used for collision
     */
    void tickDash(int[][] map) {
        if (dashCooldown > 0) dashCooldown--;
        if (!dashing) return;

        double step = Math.min(DASH_SPEED, dashRemaining);
        double nx   = x + dashDirX * step;
        double ny   = y + dashDirY * step;

        if      (walkable(map, nx, ny)) { x = nx; y = ny; }
        else if (walkable(map, nx, y))  { x = nx; }          // slide horizontally
        else if (walkable(map, x,  ny)) { y = ny; }          // slide vertically
        else    { dashRemaining = 0; }                        // fully blocked — stop early

        dashRemaining -= step;
        if (dashRemaining <= 0) {
            dashing      = false;
            dashCooldown = DASH_COOLDOWN;
        }
    }

    /**
     * Returns how ready the dash is, as a fraction from 0.0 to 1.0.
     * Used by the HUD to fill the dash cooldown bar.
     * @return 0.0 when on cooldown or actively dashing; 1.0 when fully ready
     */
    float dashReadiness() {
        if (dashing) return 0f;
        return 1f - (float) dashCooldown / DASH_COOLDOWN;
    }

    // -------------------------------------------------------------------------
    // Combat — receiving damage
    // -------------------------------------------------------------------------

    /**
     * Checks whether any enemy is touching the player and applies contact damage
     * if i-frames have expired. Only one enemy can deal damage per call.
     * I-frames are consumed by decrementing iFrames each tick regardless
     * of whether contact occurs, so they expire naturally over time.
     * @param enemies the current list of live enemies
     */
    void checkEnemyCollision(ArrayList<Enemy> enemies) {
        if (iFrames > 0) {
            iFrames--;
            return;
        }
        for (Enemy enemy : enemies) {
            if (enemy.isTouchingPlayer()) {
                iFrames += 50;
                takeDamage((int) Enemy.DAMAGE);
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Combat — attacking
    // -------------------------------------------------------------------------

    /**
     * Fires the current weapon toward the nearest enemy. Updates facingAngle
     * as a side effect so attack animations know which way to point.
     * @param heavy   {@code true} if the player held the attack button long enough
     *                for a heavy attack (currently unused by most weapons)
     * @param enemies the list of enemies to target; must not be empty
     */
    void attack(boolean heavy, ArrayList<Enemy> enemies) {
        if (enemies.isEmpty()) return;

        Enemy closest      = findClosestEnemy(enemies);
        double closestDist = closest.distToPlayer;
        facingAngle        = Math.atan2(closest.y - y, closest.x - x);

        switch (weaponChoice) {
            case 0: // Rapier — fast bolt fired directly at the nearest enemy
                double rapierSpeed = 0.3;
                projectiles.add(new Projectile(
                    x, y,
                    (closest.x - x) / closestDist * rapierSpeed,
                    (closest.y - y) / closestDist * rapierSpeed
                ));
                attackAnimations.startStab(facingAngle);
                attackCooldown = 100;
                break;

            case 1: // Scythe — wide arc that sweeps through a 180° arc
                attackAnimations.startSwing(facingAngle);
                attackCooldown = 100;
                break;

            case 2: // Disc — slower spinning projectile that travels much farther
                double discSpeed = 0.1;
                projectiles.add(new Projectile(
                    x, y,
                    Math.cos(facingAngle) * discSpeed,
                    Math.sin(facingAngle) * discSpeed,
                    true
                ));
                attackCooldown = 100;
                break;
        }
    }

    /**
     * Advances all attack state by one tick: decrements the cooldown, increments the
     * hold counter while the button is held, and updates any active animations.
     * Also runs hit detection for the scythe swing in screen space.
     * @param enemies  the live enemy list, checked for scythe hits
     * @param tileSize pixel size of one world tile, used to convert to screen space
     * @param tilesX   number of tiles visible horizontally, used for screen-space conversion
     * @param tilesY   number of tiles visible vertically, used for screen-space conversion
     */
    void tickAttack(ArrayList<Enemy> enemies, int tileSize, int tilesX, int tilesY) {
        if (attackCooldown > 0) attackCooldown--;
        if (attackHeld) attackHoldCounter++;

        attackAnimations.updateSwing();
        attackAnimations.updateStab();

        if (attackAnimations.isSwinging() && !enemies.isEmpty()) {
            int[] screenX = new int[enemies.size()];
            int[] screenY = new int[enemies.size()];
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e    = enemies.get(i);
                screenX[i] = (int) ((e.x - x + tilesX / 2.0) * tileSize);
                screenY[i] = (int) ((e.y - y + tilesY / 2.0) * tileSize);
            }
            attackAnimations.checkSwingHits(enemies, screenX, screenY, 15, facingAngle);
        }
    }

    /**
     * Finds the enemy with the smallest distToPlayer.
     * Assumes the list is non-empty.
     * @param enemies a non-empty list of enemies
     * @return the closest enemy to the player
     */
    private Enemy findClosestEnemy(ArrayList<Enemy> enemies) {
        Enemy closest = enemies.get(0);
        for (Enemy e : enemies) {
            if (e.distToPlayer < closest.distToPlayer) closest = e;
        }
        return closest;
    }

    // -------------------------------------------------------------------------
    // Projectiles
    // -------------------------------------------------------------------------

    /**
     * Advances every live projectile by one tick (moves it along its velocity vector).
     */
    void moveProjectiles() {
        for (Projectile p : projectiles) p.move();
    }

    /**
     * Tests every projectile against every enemy and removes the projectile and
     * damages the enemy on the first hit found (one hit per projectile per tick).
     * Iterates in reverse so removal by index is safe.
     * @param enemies the live enemy list
     */
    void checkProjectileHits(ArrayList<Enemy> enemies) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            for (Enemy enemy : enemies) {
                double dx   = p.x - enemy.x;
                double dy   = p.y - enemy.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.5) {
                    enemy.takeDamage(10);
                    projectiles.remove(i);
                    break;
                }
            }
        }
    }

    /**
     * Removes any projectile whose lifespan has reached zero or which has
     * travelled beyond its type's maximum range. Iterates in reverse so
     * removal by index is safe.
     */
    void removeExpiredProjectiles() {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            double maxRange = p.isDisc ? DISC_MAX_RANGE : RAPIER_MAX_RANGE;
            if (p.lifespan <= 0 || p.distanceTravelled() > maxRange) {
                projectiles.remove(i);
            }
        }
    }
}
