import java.util.ArrayList;

class Player extends Damageable {

    // World position
    double x, y;

    // Knockback velocity (currently tracked but not yet applied)
    double kbX = 0, kbY = 0;

    // Weapon selection: 0 = rapier, 1 = scythe, 2 = disc
    int weaponChoice = 0;

    // Attack state
    int attackCooldown = 0;
    int attackHoldCounter = 0;
    boolean attackHeld = false;

    // The angle toward the last targeted enemy, used by attack animations
    double facingAngle = 0;

    // Movement state — updated each tick by move()
    double moveAngle = 0;
    boolean isMoving = false;

    // Invincibility frames — while positive, the player takes no damage
    int iFrames = 0;

    Hitbox hitbox = new Hitbox(0, 0, 100, 100);
    ArrayList<Projectile> projectiles = new ArrayList<>();
    AttackAnimations attackAnimations;

    // --- Weapon range limits ---
    private static final double RAPIER_MAX_RANGE = 3.0; // world units
    private static final double DISC_MAX_RANGE = 6.0; // world units

    // --- Dash tuning ---
    private static final double DASH_DISTANCE = 2.5;  // world units per dash
    private static final double DASH_SPEED = 0.35; // world units per tick
    private static final int DASH_COOLDOWN = 120;  // ticks between dashes

    boolean dashing = false;
    int dashCooldown = 0;
    private double dashRemaining = 0;
    private double dashDirX = 0;
    private double dashDirY = 0;

    Player(int hp) {
        super(hp);
        attackAnimations = new AttackAnimations(0, 0);
    }

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    void move(boolean up, boolean left, boolean down, boolean right, int[][] map) {
        double dx = 0, dy = 0;

        if (up) {
            dy -= 1;
            if (walkable(map, x, y - 0.1)) y -= 0.1;
        }
        if (down) {
            dy += 1;
            if (walkable(map, x, y + 1.1)) y += 0.1;
        }
        if (left) {
            dx -= 1;
            if (walkable(map, x - 0.4, y + 0.3)) x -= 0.1;
        }
        if (right) {
            dx += 1;
            if (walkable(map, x + 0.8, y + 0.3)) x += 0.1;
        }

        isMoving = (dx != 0 || dy != 0);
        if (isMoving) moveAngle = Math.atan2(dy, dx);
    }

    private boolean walkable(int[][] map, double wx, double wy) {
        return map[(int) wy][(int) wx] == 1;
    }

    // -------------------------------------------------------------------------
    // Dash
    // -------------------------------------------------------------------------

    void startDash(int[][] map) {
        if (dashing || dashCooldown > 0) return;

        attackAnimations.cancelAll();
        attackHeld = false;
        attackHoldCounter = 0;

        dashing = true;
        dashRemaining = DASH_DISTANCE;

        double angle = isMoving ? moveAngle : facingAngle;
        dashDirX = Math.cos(angle);
        dashDirY = Math.sin(angle);

        int dashDuration = (int) Math.ceil(DASH_DISTANCE / DASH_SPEED);
        iFrames = Math.max(iFrames, dashDuration + 5);
    }

    void tickDash(int[][] map) {
        if (dashCooldown > 0) dashCooldown--;
        if (!dashing) return;

        double step = Math.min(DASH_SPEED, dashRemaining);
        double nx = x + dashDirX * step;
        double ny = y + dashDirY * step;

        if (walkable(map, nx, ny)) {
            x = nx;
            y = ny;
        } else if (walkable(map, nx, y)) {
            x = nx;
        } else if (walkable(map, x, ny)) {
            y = ny;
        } else {
            dashRemaining = 0;
        } // hit a wall — stop early

        dashRemaining -= step;
        if (dashRemaining <= 0) {
            dashing = false;
            dashCooldown = DASH_COOLDOWN;
        }
    }

    // 0.0 = on cooldown, 1.0 = ready
    float dashReadiness() {
        if (dashing) return 0f;
        return 1f - (float) dashCooldown / DASH_COOLDOWN;
    }

    // -------------------------------------------------------------------------
    // Combat — player taking damage
    // -------------------------------------------------------------------------

    void checkEnemyCollision(ArrayList<Enemy> enemies) {
        if (iFrames > 0) {
            iFrames--;
            return;
        }
        for (Enemy enemy : enemies) {
            if (enemy.isTouchingPlayer()) {
                iFrames += 50;
                takeDamage((int) Enemy.DAMAGE);
                break; // one hit per collision frame
            }
        }
    }

    // -------------------------------------------------------------------------
    // Combat — player attacking
    // -------------------------------------------------------------------------

    void attack(boolean heavy, ArrayList<Enemy> enemies) {
        if (enemies.isEmpty()) return;

        Enemy closest = findClosestEnemy(enemies);
        double closestDist = closest.distToPlayer;
        facingAngle = Math.atan2(closest.y - y, closest.x - x);

        switch (weaponChoice) {
            case 0: // Rapier — fast bolt toward the nearest enemy
                double rapierSpeed = 0.3;
                projectiles.add(new Projectile(
                        x, y,
                        (closest.x - x) / closestDist * rapierSpeed,
                        (closest.y - y) / closestDist * rapierSpeed
                ));
                attackAnimations.startStab(facingAngle);
                attackCooldown = 100;
                break;

            case 1: // Scythe — wide sweeping arc
                attackAnimations.startSwing(facingAngle);
                attackCooldown = 100;
                break;

            case 2: // Disc — slower spinning projectile, travels farther
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

    void tickAttack(ArrayList<Enemy> enemies, int tileSize, int tilesX, int tilesY) {
        if (attackCooldown > 0) attackCooldown--;
        if (attackHeld) attackHoldCounter++;

        attackAnimations.updateSwing();
        attackAnimations.updateStab();

        if (attackAnimations.isSwinging() && !enemies.isEmpty()) {
            int[] screenX = new int[enemies.size()];
            int[] screenY = new int[enemies.size()];
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e = enemies.get(i);
                screenX[i] = (int) ((e.x - x + tilesX / 2.0) * tileSize);
                screenY[i] = (int) ((e.y - y + tilesY / 2.0) * tileSize);
            }
            attackAnimations.checkSwingHits(enemies, screenX, screenY, 15);
        }
    }

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

    void moveProjectiles() {
        for (Projectile p : projectiles) p.move();
    }

    void checkProjectileHits(ArrayList<Enemy> enemies) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            for (Enemy enemy : enemies) {
                double dx = p.x - enemy.x;
                double dy = p.y - enemy.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.5) {
                    enemy.takeDamage(10);
                    projectiles.remove(i);
                    break;
                }
            }
        }
    }

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
