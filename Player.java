import java.util.ArrayList;

class Player extends Damageable {
    double x, y;
    double kbX = 0, kbY = 0;
    int iFrames = 0;
    int attackCooldown = 0;
    int attackHoldCounter = 0;
    boolean attackHeld = false;
    int weaponChoice = 0; // 0 - rapier, 1 - scythe, 2 - disc

    /** Angle (radians) toward the last targeted enemy, used by attack animations. */
    double facingAngle = 0;

    /* =========================================================
     * DASH
     * ========================================================= */

    /** Total distance to travel per dash (world units). */
    private static final double DASH_DISTANCE = 2.5;

    /** Speed per tick while dashing (world units). */
    private static final double DASH_SPEED = 0.35;

    /** Ticks between dashes. */
    private static final int DASH_COOLDOWN_MAX = 120;

    /** True while the dash is actively moving the player. */
    boolean dashing = false;

    /** World-unit distance still remaining in this dash. */
    private double dashRemaining = 0;

    /** Direction of the current dash. */
    private double dashDirX = 0;
    private double dashDirY = 0;

    /** Ticks until the next dash is available (0 = ready). */
    int dashCooldown = 0;

    /**
     * Begins a dash in the direction the player is currently facing.
     * Cancels any active attack animation.
     *
     * @param mapArray collision map
     */
    void startDash(int[][] mapArray) {
        if (dashing || dashCooldown > 0) return;

        // Cancel attack animations
        attackAnimations.cancelAll();
        attackHeld = false;
        attackHoldCounter = 0;

        dashing = true;
        dashRemaining = DASH_DISTANCE;
        double dashAngle = isMoving ? moveAngle : facingAngle;
        dashDirX = Math.cos(dashAngle);
        dashDirY = Math.sin(dashAngle);

        // Grant iFrames for the full dash duration plus a small buffer
        int dashTicks = (int) Math.ceil(DASH_DISTANCE / DASH_SPEED);
        iFrames = Math.max(iFrames, dashTicks + 5);
    }

    /**
     * Advances the dash by one tick. Must be called each game tick.
     *
     * @param mapArray collision map
     */
    void tickDash(int[][] mapArray) {
        if (dashCooldown > 0) dashCooldown--;

        if (!dashing) return;

        double step = Math.min(DASH_SPEED, dashRemaining);

        double nx = x + dashDirX * step;
        double ny = y + dashDirY * step;

        // Collision: only move on walkable tiles, with axis sliding
        if (mapArray[(int) ny][(int) nx] == 1) {
            x = nx;
            y = ny;
        } else if (mapArray[(int) y][(int) nx] == 1) {
            x = nx;
        } else if (mapArray[(int) ny][(int) x] == 1) {
            y = ny;
        } else {
            dashRemaining = 0; // wall — stop early
        }

        dashRemaining -= step;
        if (dashRemaining <= 0) {
            dashing = false;
            dashCooldown = DASH_COOLDOWN_MAX;
        }
    }

    /** Returns 0.0 (empty/on cooldown) to 1.0 (full/ready). */
    float dashCooldownFraction() {
        if (dashing) return 0f;
        return 1f - (float) dashCooldown / DASH_COOLDOWN_MAX;
    }

    /** Max disc travel distance in world tiles before it disappears. */
    private static final double DISC_MAX_DISTANCE = 6.0;

    Hitbox hitbox = new Hitbox(0, 0, 100, 100);

    ArrayList<Projectile> projectiles = new ArrayList<Projectile>();

    AttackAnimations attackAnimations;

    Player(int hp) {
        super(hp);
        attackAnimations = new AttackAnimations(0, 0);
    }

    /** Direction of the last WASD input (radians). Updated each tick a key is held. */
    double moveAngle = 0;
    /** True if the player pressed any movement key this tick. */
    boolean isMoving = false;

    void move(boolean wPressed, boolean aPressed, boolean sPressed, boolean dPressed, int[][] mapArray) {
        double dx = 0, dy = 0;
        if (wPressed) { dy -= 1; if (mapArray[(int)(y)][(int)(x)] == 1)         y -= .1; }
        if (sPressed) { dy += 1; if (mapArray[(int)(y + 1.1)][(int)(x)] == 1)   y += .1; }
        if (aPressed) { dx -= 1; if (mapArray[(int)(y + .3)][(int)(x - .4)] == 1) x -= .1; }
        if (dPressed) { dx += 1; if (mapArray[(int)(y + .3)][(int)(x + 0.8)] == 1) x += .1; }

        isMoving = (dx != 0 || dy != 0);
        if (isMoving) moveAngle = Math.atan2(dy, dx);
    }

    void checkEnemyCollision(ArrayList<Enemy> enemies) {
        if (iFrames > 0) {
            iFrames--;
        } else {
            for (Enemy enemy : enemies) {
                if (enemy.dist <= 0.2) {
                    iFrames += 50;
                    takeDamage((int) enemy.damage);
                }
            }
        }
    }

    void attack(boolean heavy, ArrayList<Enemy> enemies) {
        if (enemies.isEmpty()) {
            return;
        }
        Enemy closest = enemies.get(0);
        double closestDist = 100;
        for (Enemy enemy : enemies) {
            if (enemy.dist < closestDist) {
                closestDist = enemy.dist;
                closest = enemy;
            }
        }

        // Compute the angle toward the closest enemy for animations
        facingAngle = Math.atan2(closest.y - y, closest.x - x);

        if (weaponChoice == 0) { // rapier — fires a projectile toward closest enemy
            double speed = 0.3;
            projectiles.add(new Projectile(x, y,
                    (closest.x - x) / closestDist * speed,
                    (closest.y - y) / closestDist * speed));
            attackAnimations.startStab(facingAngle);
            attackCooldown = 100;
        }
        if (weaponChoice == 1) { // scythe — wide swing arc
            attackAnimations.startSwing(facingAngle);
            attackCooldown = 100;
        }
        if (weaponChoice == 2) { // disc — thrown projectile
            double discSpeed = 0.1;
            projectiles.add(new Projectile(x, y,
                    Math.cos(facingAngle) * discSpeed,
                    Math.sin(facingAngle) * discSpeed,
                    true));
            attackCooldown = 100;
        }
    }

    void moveProjectiles() {
        for (Projectile projectile : projectiles) {
            projectile.extend();
        }
    }

    /** Max rapier travel distance in world tiles before it disappears. */
    private static final double RAPIER_MAX_DISTANCE = 3.0;

    void checkProjectileLifespan() {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            if (p.isDisc) {
                // Disc is removed once it travels far enough from its spawn point
                double dx = p.x - p.spawnX;
                double dy = p.y - p.spawnY;
                if (Math.sqrt(dx * dx + dy * dy) > DISC_MAX_DISTANCE) {
                    projectiles.remove(i);
                }
            } else {
                // Rapier: remove if lifespan expired OR it travelled beyond max range
                double dx = p.x - p.spawnX;
                double dy = p.y - p.spawnY;
                if (p.lifespan <= 0 || Math.sqrt(dx * dx + dy * dy) > RAPIER_MAX_DISTANCE) {
                    projectiles.remove(i);
                }
            }
        }
    }

    void checkProjectileHits(ArrayList<Enemy> enemies) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile projectile = projectiles.get(i);

            for (Enemy enemy : enemies) {
                double dx = projectile.x - enemy.x;
                double dy = projectile.y - enemy.y;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < 0.5) {
                    enemy.takeDamage(10);
                    projectiles.remove(i);
                    break;
                }
            }
        }
    }

    void tickAttack(ArrayList<Enemy> enemies, int tileSize, int tilesX, int tilesY) {
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (attackHeld) {
            attackHoldCounter++;
        }
        attackAnimations.updateSwing();
        attackAnimations.updateStab();

        // Check scythe hits (screen-space arc detection)
        if (attackAnimations.isSwinging() && !enemies.isEmpty()) {
            int[] screenX = new int[enemies.size()];
            int[] screenY = new int[enemies.size()];
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e = enemies.get(i);
                screenX[i] = (int)((e.x - x + tilesX / 2.0) * tileSize);
                screenY[i] = (int)((e.y - y + tilesY / 2.0) * tileSize);
            }
            attackAnimations.checkSwingHits(enemies, screenX, screenY, 15);
        }
    }
}
