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

    /** Max disc travel distance in world tiles before it disappears. */
    private static final double DISC_MAX_DISTANCE = 6.0;

    Hitbox hitbox = new Hitbox(0, 0, 100, 100);

    ArrayList<Projectile> projectiles = new ArrayList<Projectile>();

    AttackAnimations attackAnimations;

    Player(int hp) {
        super(hp);
        attackAnimations = new AttackAnimations(0, 0);
    }

    void move(boolean wPressed, boolean aPressed, boolean sPressed, boolean dPressed, int[][] mapArray) {
        if (wPressed) {
            if (mapArray[(int) (y)][(int) (x)] == 1) {
                y -= .1;
            }
        }
        if (aPressed) {
            if (mapArray[(int) (y + .3)][(int) (x - .4)] == 1) {
                x -= .1;
            }
        }
        if (sPressed) {
            if (mapArray[(int) (y + 1.1)][(int) (x)] == 1) {
                y += .1;
            }
        }
        if (dPressed) {
            if (mapArray[(int) (y + .3)][(int) (x + 0.8)] == 1) {
                x += .1;
            }
        }
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
        System.out.println("attacked");
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
            double discSpeed = 0.3;
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

    void checkProjectileLifespan() {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            if (p.isDisc) {
                // Disc is removed once it travels far enough from its spawn point
                double dx = p.x - x;
                double dy = p.y - y;
                if (Math.sqrt(dx * dx + dy * dy) > DISC_MAX_DISTANCE) {
                    projectiles.remove(i);
                }
            } else {
                if (p.lifespan <= 0) {
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
