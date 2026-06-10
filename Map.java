import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.util.ArrayList;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Top-level game class. Owns the window, game loop, map data, player, and enemy list.
 * Also implements KeyListener directly so all input handling stays in one place.
 * The inner DrawPanel handles all rendering and is kept as an inner class so it
 * can read game state without needing explicit references passed around.
 * The world uses a tile-based coordinate system where one unit equals one tile.
 * TILE_SIZE converts between world units and screen pixels.
 */
public class Map implements KeyListener {

    // --- Map layout ---

    /**
     * Number of tiles in the map along the X axis.
     */
    static final int MAP_WIDTH = 137;

    /**
     * Number of tiles in the map along the Y axis.
     */
    static final int MAP_HEIGHT = 20;

    /**
     * Pixel size of one world tile. Controls zoom level for the entire game.
     */
    static final int TILE_SIZE = 100;

    /**
     * The loaded tile grid. Values: 0 = void, 1 = floor, 2 = wall, 3 = wallTop.
     */
    int[][] mapGrid = new int[MAP_HEIGHT][MAP_WIDTH];

    // --- Difficulty / spawning ---

    /**
     * Sum of ENEMY_COST for all currently live enemies.
     * Compared against SPAWN_THRESHOLD to decide when to spawn more.
     */
    int activeDifficulty = 0;

    /**
     * Remaining "budget" of difficulty points available to spend on future spawns.
     */
    int remainingDifficulty = 50;

    /**
     * Spawn a new wave when activeDifficulty drops below this value.
     */
    static final int SPAWN_THRESHOLD = 15;

    /**
     * Maximum allowed value of activeDifficulty at any one time.
     * Grows by 10 each time a wave is fully cleared (stageCount increments).
     */
    int maxDifficulty = 30;

    /**
     * Increments each time a wave is fully cleared; drives wave escalation.
     */
    int stageCount = 0;

    /**
     * Difficulty points each enemy costs when spawned (and refunds when killed).
     */
    static final int ENEMY_COST = 3;

    /**
     * Points awarded to the player for each enemy killed.
     */
    static final int POINTS_PER_KILL = 10;

    /**
     * The player's current score, incremented by POINTS_PER_KILL on each kill.
     */
    int score = 0;

    /**
     * Controls which screen is currently shown.
     * 0 — start screen (title / main menu)
     * 1 — rules screen
     * 2 — playing
     */
    int gameState = 0;

    /**
     * Tick counter used to drive animations on the start screen.
     */
    int screenTick = 0;

    // --- Core objects ---

    /**
     * The player character.
     */
    Player player = new Player(100);

    /**
     * All enemies currently alive in the world.
     */
    ArrayList<Enemy> enemies = new ArrayList<>();

    /**
     * The player sprite, loaded once at startup.
     */
    BufferedImage playerSprite = loadImage("playerSprite.png");

    //Used to determine where in the player sprite sheet to render. 0 down, 1 right, 2 up, 3 left
    int playerDirection = 0;

    // --- Input state ---

    /**
     * {@code true} while the corresponding movement key is held down.
     */
    boolean upHeld, leftHeld, downHeld, rightHeld;

    // --- Window ---

    /**
     * The top-level application window.
     */
    JFrame window;

    /**
     * The panel that handles all game rendering.
     */
    DrawPanel panel;

    /**
     * The Swing timer that drives the game loop at ~100 ticks per second.
     */
    Timer gameLoop;

    // --- Pause ---

    /**
     * {@code true} while the game is paused; prevents tick processing.
     */
    boolean paused = false;

    // -------------------------------------------------------------------------

    /**
     * Entry point. Constructs the game on the Swing event dispatch thread.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Map::new);
    }

    /**
     * Constructs the game: loads the map, positions the player, creates the window,
     * and starts the game loop.
     */
    Map() {
        loadMap("Gamemap");

        player.x = 4;
        player.y = 4;

        window = new JFrame("Factory Floor Frenzy");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.addKeyListener(this);
        window.setExtendedState(JFrame.MAXIMIZED_BOTH);

        panel = new DrawPanel();
        window.add(panel);
        window.setVisible(true);
        window.requestFocus();

        gameLoop = new Timer(10, e -> tick());
        gameLoop.start(); // drives start-screen animation; gameplay begins when J is pressed
    }

    // -------------------------------------------------------------------------
    // Game loop
    // -------------------------------------------------------------------------

    /**
     * Transitions from the start screen into the game.
     */
    private void startGame() {
        resetGame();
        gameState = 2;
    }

    /**
     * Resets all game state so a fresh run can begin.
     */
    private void resetGame() {
        score = 0;
        activeDifficulty = 0;
        remainingDifficulty = 50;
        maxDifficulty = 30;
        stageCount = 0;
        enemies.clear();
        player = new Player(100);
        player.x = 4;
        player.y = 4;
        upHeld = leftHeld = downHeld = rightHeld = false;
        paused = false;
        gameLoop.start();
    }

    /**
     * Advances the game by one tick. Called by the Swing timer every 10 ms.
     * Order of operations matters here: the dash moves the player before normal
     * movement input is processed, projectiles move before hit detection runs,
     * and dead enemies are removed after spawning so the difficulty budget stays accurate.
     */
    private void tick() {
        screenTick++;
        if (gameState != 2) {
            panel.repaint();
            return;
        }

        player.tickDash(mapGrid);
        if (!player.dashing) player.move(upHeld, leftHeld, downHeld, rightHeld, mapGrid);

        for (Enemy enemy : enemies) enemy.move(player.x, player.y, enemies);

        // Move and expire enemy projectiles; check hits on player
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (!(e instanceof RangedEnemy)) continue;
            RangedEnemy re = (RangedEnemy) e;
            for (int j = re.projectiles.size() - 1; j >= 0; j--) {
                EnemyProjectile p = re.projectiles.get(j);
                p.move();
                if (p.expired()) {
                    re.projectiles.remove(j);
                    continue;
                }
                double dx = p.x - player.x;
                double dy = p.y - player.y;
                if (Math.sqrt(dx * dx + dy * dy) < 0.4 && player.iFrames <= 0) {
                    player.iFrames += 50;
                    player.takeDamage((int) RangedEnemy.PROJECTILE_DAMAGE);
                    re.projectiles.remove(j);
                }
            }
        }

        // Charger dash contact damage
        for (Enemy e : enemies) {
            if (e instanceof ChargerEnemy) {
                ChargerEnemy ce = (ChargerEnemy) e;
                if (ce.isDashing() && ce.distToPlayer < 0.5 && player.iFrames <= 0) {
                    player.iFrames += 60;
                    player.takeDamage((int) ChargerEnemy.DASH_DAMAGE);
                }
            }
        }

        player.checkEnemyCollision(enemies);

        int visibleTilesX = panel.getWidth() / TILE_SIZE + 1;
        int visibleTilesY = panel.getHeight() / TILE_SIZE + 1;
        player.tickAttack(enemies, TILE_SIZE, visibleTilesX, visibleTilesY);

        player.moveProjectiles();
        player.checkProjectileHits(enemies);
        player.removeExpiredProjectiles();

        if (!player.alive()) {
            panel.repaint();
            return;
        }
        spawnEnemies();
        removeDeadEnemies();
        panel.repaint();
    }

    // -------------------------------------------------------------------------
    // Enemy spawning
    // -------------------------------------------------------------------------

    /**
     * Spawns enemies if the active difficulty is below SPAWN_THRESHOLD,
     * spending from remainingDifficulty until the cap or budget is reached.
     * Each spawned enemy increases activeDifficulty by ENEMY_COST.
     */
    private void spawnEnemies() {
        if (activeDifficulty < SPAWN_THRESHOLD) {
            while (activeDifficulty < maxDifficulty && remainingDifficulty > 0) {
                double spawnX = Math.random() * MAP_WIDTH;
                double spawnY = Math.random() * MAP_HEIGHT;
                // roll 0-4: 0=tank (rare), 1=ranged, 2=charger, 3-4=basic melee
                int roll = (int) (Math.random() * 5);
                switch (roll) {
                    case 0:
                        enemies.add(new TankEnemy(25, spawnX, spawnY));
                        break;
                    case 1:
                        enemies.add(new RangedEnemy(12, spawnX, spawnY));
                        break;
                    case 2:
                        enemies.add(new ChargerEnemy(15, spawnX, spawnY));
                        break;
                    default:
                        enemies.add(new Enemy(10, spawnX, spawnY));
                        break;
                }
                activeDifficulty += ENEMY_COST;
                remainingDifficulty -= ENEMY_COST;
            }

            // Wave fully cleared — escalate
            if (activeDifficulty < 3) {
                stageCount++;
                remainingDifficulty = (int) 50 * (stageCount + 2 / 2);
                maxDifficulty += 10;
            }
        }
    }


    /**
     * Removes all enemies whose HP has reached zero or below, refunding their
     * difficulty cost to activeDifficulty so new enemies can spawn.
     * Iterates in reverse so removal by index is safe.
     */
    private void removeDeadEnemies() {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            if (!enemies.get(i).alive()) {
                activeDifficulty -= ENEMY_COST;
                score += POINTS_PER_KILL;
                enemies.remove(i);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Map loading
    // -------------------------------------------------------------------------

    /**
     * Reads a plain-text tile map from disk and populates mapGrid.
     * Each character in the file is interpreted as a single digit (0, 1, or 2).
     *
     * @param filename path to the map file, relative to the working directory
     */
    private void loadMap(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            for (int row = 0; row < MAP_HEIGHT; row++) {
                String line = reader.readLine();
                for (int col = 0; col < MAP_WIDTH; col++) {
                    mapGrid[row][col] = Character.getNumericValue(line.charAt(col));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load map: " + e.getMessage());
        }
    }

    /**
     * Loads an image from disk and returns it as a BufferedImage.
     * Shows an error dialog and returns {@code null} if the file cannot be read.
     *
     * @param filename path to the image file, relative to the working directory
     * @return the loaded image, or {@code null} on failure
     */
    private static BufferedImage loadImage(String filename) {
        try {
            return ImageIO.read(new File(filename));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to load image: " + filename, "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Pause
    // -------------------------------------------------------------------------

    /**
     * Toggles the pause overlay. Only has effect during active gameplay
     * (gameState == 2) and not on the start screen or game-over screen.
     * The overlay is drawn by DrawPanel; no secondary window is used.
     */
    private void togglePause() {
        if (gameState != 2) return;
        if (!player.alive()) return;
        paused = !paused;
        if (paused) {
            gameLoop.stop();
            upHeld = leftHeld = downHeld = rightHeld = false;
            panel.repaint();
        } else {
            gameLoop.start();
            window.requestFocus();
        }
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    /**
     * Handles key-press events. Activates movement flags, triggers attacks and dash,
     * and cycles the weapon.
     *
     * @param e the key event from Swing
     */
    @Override
    public void keyPressed(KeyEvent e) {
        // Handle start and rules screens first
        if (gameState == 0) {
            if (e.getKeyCode() == KeyEvent.VK_J) {
                startGame();
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_K) {
                gameState = 1;
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_L) {
                System.exit(0);
                return;
            }
        }
        if (gameState == 1) {
            if (e.getKeyCode() == KeyEvent.VK_K || e.getKeyCode() == KeyEvent.VK_J) {
                gameState = 0;
                return;
            }
        }
        // Game-over screen (player dead, still in gameState 2)
        if (gameState == 2 && !player.alive()) {
            if (e.getKeyCode() == KeyEvent.VK_J) {
                resetGame();
                return;
            }        // play again
            if (e.getKeyCode() == KeyEvent.VK_K) {
                gameState = 0;
                panel.repaint();
                return;
            } // main menu
            return; // block all other keys
        }
        //0 down, 1 right, 2 up, 3 left
        if (gameState != 2) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_P:
                togglePause();
                break;
            case KeyEvent.VK_O:
                if (paused) System.exit(0);
                break;
            case KeyEvent.VK_W:
                upHeld = true;
                playerDirection = 2;
                break;
            case KeyEvent.VK_A:
                leftHeld = true;
                playerDirection = 3;
                break;
            case KeyEvent.VK_S:
                downHeld = true;
                playerDirection = 0;
                break;
            case KeyEvent.VK_D:
                rightHeld = true;
                playerDirection = 1;
                break;
            case KeyEvent.VK_J:
                if (player.attackCooldown <= 0) player.attackHeld = true;
                break;
            case KeyEvent.VK_K:
                player.startDash(mapGrid);
                break;
            case KeyEvent.VK_L:
                player.weaponChoice = (player.weaponChoice + 1) % 3;
                break;
            case KeyEvent.VK_8:
                int r = (int) (Math.random() * 5);
                if (r == 0) enemies.add(new TankEnemy(25, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
                else if (r == 1)
                    enemies.add(new RangedEnemy(12, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
                else if (r == 2)
                    enemies.add(new ChargerEnemy(15, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
                else enemies.add(new Enemy(10, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
                break;
        }
    }

    /**
     * Handles key-release events. Clears movement flags and fires the attack
     * when the attack button is released.
     *
     * @param e the key event from Swing
     */
    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
                upHeld = false;
                break;
            case KeyEvent.VK_A:
                leftHeld = false;
                break;
            case KeyEvent.VK_S:
                downHeld = false;
                break;
            case KeyEvent.VK_D:
                rightHeld = false;
                break;
            case KeyEvent.VK_J:
                if (player.attackHeld && player.attackCooldown <= 0) {
                    player.attack(player.attackHoldCounter > 300, enemies);
                    player.attackHeld = false;
                    player.attackHoldCounter = 0;
                }
                break;
        }
    }

    /**
     * Required by KeyListener; no action needed for typed events.
     *
     * @param e the key event from Swing
     */
    @Override
    public void keyTyped(KeyEvent e) {
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * The panel that renders the entire game each frame.
     * All drawing is broken into private helpers so paintComponent reads
     * as a clear list of layers drawn bottom-to-top: map tiles → player → projectiles
     * → enemies → HUD → game-over overlay.
     * This is an inner class so it can read the outer Map's fields
     * (player, enemies, mapGrid, etc.) without needing explicit references.
     */
    class DrawPanel extends JPanel {

        // --- Enemy and projectile sprites ---
        BufferedImage tankPunchLeft  = loadImage("tank-punch-left.png");
        BufferedImage tankPunchRight = loadImage("tank-punch-right.png");
        BufferedImage chargeLeft     = loadImage("charge-left.png");
        BufferedImage chargeRight    = loadImage("charge-right.png");
        BufferedImage smelterSprite  = loadImage("ceo.png");
        BufferedImage flameDiscSprite = loadImage("flame_disc.png");
        BufferedImage rapierSprite = loadImage("rapierThrust.png");
        BufferedImage regularEnemySprite = loadImage("regular-enemy.png");

        /**
         * Fallback tile colour for floor tiles (tile value 1) if sprite is missing.
         */
        private static final Color FLOOR_COLOR = new Color(138, 65, 51);

        /**
         * Fallback tile colour for wall tiles (tile value 2) if sprite is missing.
         */
        private static final Color WALL_COLOR = new Color(199, 93, 72);

        //Wall top sprite
        BufferedImage wallTop = loadImage("wallTop.png");

        //wall side sprite
        BufferedImage wallSide = loadImage("wallSide.png");

        //Floor Texture
        BufferedImage floorTile = loadImage("floorTile.png");

        /**
         * Colour of the charging glow shown when a heavy attack is being charged.
         */
        private static final Color HEAVY_COLOR = new Color(255, 255, 255, 100);

        /**
         * Creates the draw panel with a sensible default size.
         * The window is immediately maximised, so this size is only briefly visible.
         */
        DrawPanel() {
            setPreferredSize(new Dimension(800, 500));
            setBackground(Color.BLACK);
        }

        /**
         * Renders one complete frame. Called by Swing in response to repaint.
         * Enables antialiasing for smoother edges on rotated shapes.
         *
         * @param g the graphics context provided by Swing
         */
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int tilesX = w / TILE_SIZE + 1;
            int tilesY = h / TILE_SIZE + 1;

            player.attackAnimations.setPlayerPosition(w / 2, h / 2);

            drawMap(g, tilesX, tilesY);
            if (gameState == 0) {
                drawStartScreen(g2d, w, h);
                return;
            }
            if (gameState == 1) {
                drawRulesScreen(g2d, w, h);
                return;
            }
            drawPlayer(g, g2d);
            drawProjectiles(g, g2d, tilesX, tilesY);
            drawEnemies(g, g2d, tilesX, tilesY);
            drawHUD(g2d, w, h);
            if (!player.alive()) drawGameOver(g2d, w, h);
            if (paused) drawPauseOverlay(g2d, w, h);
        }

        /**
         * Draws all visible map tiles. Only tiles within the viewport are drawn;
         * out-of-bounds map coordinates are silently skipped.
         * The camera is centred on the player: each tile's screen position is
         * computed by subtracting the player's sub-tile offset ({@code player.x % 1})
         * so the world scrolls smoothly rather than snapping tile-by-tile.
         *
         * @param g      the graphics context to draw into
         * @param tilesX number of tiles visible horizontally
         * @param tilesY number of tiles visible vertically
         */
        private void drawMap(Graphics g, int tilesX, int tilesY) {
            for (int row = 0; row <= tilesY; row++) {
                for (int col = 0; col <= tilesX; col++) {
                    int mapRow = (int) player.y - tilesY / 2 + row;
                    int mapCol = (int) player.x - tilesX / 2 + col;
                    if (mapRow < 0 || mapCol < 0 || mapRow >= MAP_HEIGHT || mapCol >= MAP_WIDTH) continue;

                    int screenX = (int) ((col - player.x % 1) * TILE_SIZE);
                    int screenY = (int) ((row - player.y % 1) * TILE_SIZE);

                    switch (mapGrid[mapRow][mapCol]) {
                        case 1:
                            if (floorTile != null) g.drawImage(floorTile, screenX, screenY, TILE_SIZE, TILE_SIZE, null);
                            else {
                                g.setColor(FLOOR_COLOR);
                                g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                            }
                            break;
                        case 2:
                            if (wallSide != null) g.drawImage(wallSide, screenX, screenY, TILE_SIZE, TILE_SIZE, null);
                            else {
                                g.setColor(WALL_COLOR);
                                g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                            }
                            break;
                        case 3:
                            if (wallTop != null) g.drawImage(wallTop, screenX, screenY, TILE_SIZE, TILE_SIZE, null);
                            else {
                                g.setColor(WALL_COLOR);
                                g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                            }
                            break;
                    }
                }
            }
        }

        /**
         * Draws the player sprite at the centre of the screen, along with any
         * active attack animations (stab or swing). Also draws the heavy-attack
         * charge glow if the player has held the attack button long enough.
         *
         * @param g   the graphics context for image and glow drawing
         * @param g2d the 2D context for transformed attack animations
         */
        private void drawPlayer(Graphics g, Graphics2D g2d) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            if (player.attackHoldCounter > 300) {
                g.setColor(HEAVY_COLOR);
                g.fillOval(cx, cy, 100, 100);
            }

            // Draw the correct directional frame from the sprite sheet.
            // Each frame is 80px wide × 105px tall; playerDirection selects the column.
            // 0=down, 1=right, 2=up, 3=left
            g.drawImage(playerSprite,
                    cx, cy, cx + 80, cy + 105,                          // dest rect
                    playerDirection * 80, 0, playerDirection * 80 + 80, 105,  // src rect
                    null);

            player.attackAnimations.drawStab(g2d);
            player.attackAnimations.drawSwing(g2d, player.facingAngle);
        }

        /**
         * Draws all live projectiles in screen space.
         * Disc — an orange square that rotates each tick.
         * Rapier bolt — a red triangle whose tip points in the direction of travel.
         *
         * @param g      the graphics context for polygon drawing
         * @param g2d    the 2D context for rotated disc drawing
         * @param tilesX number of tiles visible horizontally, used for world-to-screen conversion
         * @param tilesY number of tiles visible vertically, used for world-to-screen conversion
         */
        private void drawProjectiles(Graphics g, Graphics2D g2d, int tilesX, int tilesY) {
            for (Projectile p : player.projectiles) {
                int sx = (int) ((p.x - player.x + tilesX / 2.0) * TILE_SIZE);
                int sy = (int) ((p.y - player.y + tilesY / 2.0) * TILE_SIZE);

                if (p.isDisc) {
                    AffineTransform saved = g2d.getTransform();
                    g2d.translate(sx, sy);
                    g2d.rotate(p.rotation);
                    if (flameDiscSprite != null) {
                        // Draw the flame disc sprite centred on the projectile
                        int dw = 45, dh = 45;
                        g2d.drawImage(flameDiscSprite, -dw / 2, -dh / 2, dw, dh, null);
                    } else {
                        g2d.setColor(Color.ORANGE);
                        g2d.fillRect(-20, -20, 40, 40);
                    }
                    g2d.setTransform(saved);
                } else {
                    // Rapier bolt — sprite starts at projectile position, tip points forward
                    double angle = Math.atan2(p.vy, p.vx);
                    AffineTransform saved = g2d.getTransform();
                    g2d.translate(sx, sy);
                    g2d.rotate(angle);
                    // if (rapierSprite != null) {
                        int rw = 100, rh = 30;
                        g2d.drawImage(rapierSprite, -rw, -rh / 2, rw, rh, null);
                //}else {
                        // g2d.setColor(Color.RED);
                        // Polygon bolt = new Polygon();
                        // bolt.addPoint(0, 0);
                        // bolt.addPoint(-25, -10);
                        // bolt.addPoint(-25, 10);
                        // g2d.fillPolygon(bolt);
                    //}
                    g2d.setTransform(saved);
                }
            }
        }

        /**
         * Draws all live enemies in screen space using their sprite sheets.
         * Tank    — tank-punch-left/right sprite sheet (7 frames, 150×140 px per frame)
         * Charger — charge-left/right sprite sheet (3 frames, 120×120 px per frame)
         * Ranged  — smelter sprite sheet (4 frames, 45×230 total → each 45×57.5 but
         *           stored as 180×230: 4 columns of 45px wide)
         *
         * @param g      the graphics context for polygon/oval fallbacks
         * @param g2d    the 2D context for affine transforms and outlines
         * @param tilesX number of tiles visible horizontally
         * @param tilesY number of tiles visible vertically
         */
        private void drawEnemies(Graphics g, Graphics2D g2d, int tilesX, int tilesY) {
            for (Enemy enemy : enemies) {
                int sx = (int) ((enemy.x - player.x + tilesX / 2.0) * TILE_SIZE);
                int sy = (int) ((enemy.y - player.y + tilesY / 2.0) * TILE_SIZE);

                // Advance animation frame
                enemy.animTick++;
                if (enemy.animTick >= Enemy.ANIM_SPEED) {
                    enemy.animTick = 0;
                    enemy.animFrame++;
                }

                if (enemy instanceof ChargerEnemy) {
                    ChargerEnemy ce = (ChargerEnemy) enemy;

                    // Telegraph rectangle while charging
                    if (ce.isCharging()) {
                        float prog = ce.chargeProgress();
                        int rectLen = (int) (prog * ChargerEnemy.CHARGE_RANGE * TILE_SIZE * 1.5);
                        int rectW = 18;
                        AffineTransform saved = g2d.getTransform();
                        g2d.translate(sx + 25, sy + 25);
                        g2d.rotate(Math.atan2(ce.dashDirY, ce.dashDirX));
                        g2d.setColor(new Color(255, 80, 0, (int) (60 + 130 * prog)));
                        g2d.fillRect(0, -rectW / 2, rectLen, rectW);
                        g2d.setColor(new Color(255, 160, 0, (int) (120 + 120 * prog)));
                        g2d.setStroke(new BasicStroke(1.5f));
                        g2d.drawRect(0, -rectW / 2, rectLen, rectW);
                        g2d.setTransform(saved);
                        g2d.setStroke(new BasicStroke(1f));
                    }

                    // Choose left or right sheet based on player relative position
                    boolean facingLeft = player.x < ce.x;
                    BufferedImage sheet = facingLeft ? chargeLeft : chargeRight;

                    // 3 frames: APPROACH=0, CHARGING=1, DASHING=2
                    int frame;
                    switch (ce.state) {
                        case CHARGING: frame = 1; break;
                        case DASHING:  frame = 2; break;
                        default:       frame = ce.animFrame % 3; break;
                    }

                    // sheet is 360×120 (3 frames × 120px wide, 120px tall)
                    int fw = 120, fh = 120;
                    if (sheet != null) {
                        g.drawImage(sheet,
                            sx - fw / 2, sy - fh / 2, sx + fw / 2, sy + fh / 2,
                            frame * fw, 0, frame * fw + fw, fh,
                            null);
                    } else {
                        // Fallback
                        Color bodyColor = ce.isDashing() ? new Color(255, 255, 200) : new Color(255, 140, 30);
                        g.setColor(bodyColor);
                        g.fillOval(sx, sy, 50, 50);
                    }

                } else if (enemy instanceof RangedEnemy) {
                    RangedEnemy re = (RangedEnemy) enemy;

                    // Draw fired projectiles
                    g2d.setColor(new Color(80, 200, 255));
                    for (EnemyProjectile p : re.projectiles) {
                        int px = (int) ((p.x - player.x + tilesX / 2.0) * TILE_SIZE);
                        int py = (int) ((p.y - player.y + tilesY / 2.0) * TILE_SIZE);
                        int[] xs = {px, px + 8, px, px - 8};
                        int[] ys = {py - 8, py, py + 8, py};
                        g2d.fillPolygon(xs, ys, 4);
                        g2d.setColor(new Color(180, 240, 255));
                        g2d.drawPolygon(xs, ys, 4);
                        g2d.setColor(new Color(80, 200, 255));
                    }

                    // smelter.png: 180×230 → 4 frames, each 45×230 wide... 
                    // Actually smelter is a single character with 4 directional frames side by side
                    // 180/4 = 45px wide, 230px tall per frame
                    if (smelterSprite != null) {
                        int drawW = 60, drawH = 92; // maintain 150:230 aspect ratio
                        if (enemy.isFlashing()) {
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                            g2d.setColor(Color.WHITE);
                            g2d.fillRect(sx - drawW / 2, sy - drawH / 2, drawW, drawH);
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                        }
                        g.drawImage(smelterSprite, sx - drawW / 2, sy - drawH / 2, drawW, drawH, null);
                    } else {
                        g.setColor(new Color(90, 80, 200));
                        g.fillOval(sx, sy, 50, 50);
                        boolean inRange = re.distToPlayer <= RangedEnemy.FIRE_RANGE;
                        g2d.setColor(inRange ? new Color(80, 200, 255) : new Color(140, 120, 220));
                        g2d.setStroke(new BasicStroke(2f));
                        g2d.drawOval(sx - 5, sy - 5, 60, 60);
                        g2d.setStroke(new BasicStroke(1f));
                    }

                } else if (enemy instanceof TankEnemy) {
                    // tank-punch-left/right: 1050×140 → 7 frames, each 150×140
                    boolean facingLeft = player.x < enemy.x;
                    BufferedImage sheet = facingLeft ? tankPunchLeft : tankPunchRight;
                    if (sheet != null) {
                        // Frames 0-3 = walk cycle, frames 4-6 = punch attack
                        int frame;
                        if (enemy.isAttacking) {
                            frame = 4 + (enemy.animFrame % 3);
                        } else {
                            frame = enemy.animFrame % 4;
                        }
                        int fw = 150, fh = 140;
                        int drawW = 100, drawH = 93;
                        // Flash white on damage
                        if (enemy.isFlashing()) {
                            // Draw with white composite
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                            g2d.setColor(Color.WHITE);
                            g2d.fillOval(sx - drawW / 2, sy - drawH / 2, drawW, drawH);
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                        }
                        g.drawImage(sheet,
                            sx - drawW / 2, sy - drawH / 2, sx + drawW / 2, sy + drawH / 2,
                            frame * fw, 0, frame * fw + fw, fh,
                            null);
                    } else {
                        g.setColor(enemy.isFlashing() ? Color.WHITE : new Color(140, 20, 20));
                        g.fillOval(sx - 10, sy - 10, 70, 70);
                    }

                } else {
                    // Basic melee enemy
                    if (regularEnemySprite != null) {
                        if (enemy.isFlashing()) {
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                            g2d.setColor(Color.WHITE);
                            g2d.fillOval(sx, sy, 80, 100);
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                        }
                        g.drawImage(regularEnemySprite, sx - 40, sy - 50, 80, 100, null);
                    } else {
                        g.setColor(enemy.isFlashing() ? Color.WHITE : new Color(60, 180, 60));
                        g.fillOval(sx, sy, 50, 50);
                        g2d.setColor(new Color(20, 120, 20));
                        g2d.setStroke(new BasicStroke(2f));
                        g2d.drawOval(sx, sy, 50, 50);
                        g2d.setStroke(new BasicStroke(1f));
                    }
                }
            }
        }

        /**
         * Draws all HUD elements. Currently delegates to the health bar and dash bar.
         *
         * @param g2d the 2D graphics context to draw into
         * @param w   panel width in pixels
         * @param h   panel height in pixels
         */
        private void drawHUD(Graphics2D g2d, int w, int h) {
            drawHealthBar(g2d, h);
            drawDashBar(g2d, w, h);
            drawScore(g2d, w);
        }

        /**
         * Draws the player's current score in the top-right corner of the screen.
         *
         * @param g2d the 2D graphics context to draw into
         * @param w   panel width in pixels, used to right-align the text
         */
        private void drawScore(Graphics2D g2d, int w) {
            String text = "SCORE  " + score;
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            FontMetrics fm = g2d.getFontMetrics();
            int tx = w - fm.stringWidth(text) - 20;
            int ty = 36;

            g2d.setColor(new Color(0, 0, 0, 140));
            g2d.drawString(text, tx + 2, ty + 2); // drop shadow
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);
        }

        /**
         * Draws the health bar in the bottom-left corner of the screen.
         * The fill colour shifts from green to yellow to red as HP drops below
         * 50% and 25% respectively. The bar outline flashes white while the player
         * has active i-frames, giving clear visual feedback of invulnerability.
         *
         * @param g2d the 2D graphics context to draw into
         * @param h   panel height in pixels, used to position the bar near the bottom
         */
        private void drawHealthBar(Graphics2D g2d, int h) {
            int barW = 200, barH = 16;
            int barX = 20, barY = h - barH - 20;
            float fill = Math.max(0f, (float) player.hp / player.maxHp);

            g2d.setColor(new Color(30, 30, 30, 200));
            g2d.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);

            Color fillColor;
            if (fill > 0.5f) fillColor = new Color(60, 200, 80);
            else if (fill > 0.25f) fillColor = new Color(220, 180, 0);
            else fillColor = new Color(210, 50, 50);
            g2d.setColor(fillColor);
            g2d.fillRoundRect(barX, barY, (int) (barW * fill), barH, 6, 6);

            if (player.iFrames > 0 && (player.iFrames / 4) % 2 == 0) {
                g2d.setColor(new Color(255, 255, 255, 180));
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);
                g2d.setStroke(new BasicStroke(1f));
            }

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2d.drawString("HP  " + Math.max(0, player.hp) + " / " + player.maxHp, barX, barY - 5);
        }

        /**
         * Draws the dash cooldown bar in the bottom-right corner of the screen.
         * The bar fills from left to right as the cooldown expires. When the dash
         * is fully ready, the bar turns bright blue and gains a glowing outline, and
         * the label updates to show the keybind as a reminder.
         *
         * @param g2d the 2D graphics context to draw into
         * @param w   panel width in pixels, used to position the bar near the right edge
         * @param h   panel height in pixels, used to position the bar near the bottom
         */
        private void drawDashBar(Graphics2D g2d, int w, int h) {
            int barW = 160, barH = 16;
            int barX = w - barW - 20;
            int barY = h - barH - 20;
            float fill = player.dashReadiness();
            boolean ready = fill >= 1f;

            g2d.setColor(new Color(30, 30, 30, 200));
            g2d.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);

            g2d.setColor(ready ? new Color(100, 210, 255) : new Color(60, 130, 200));
            g2d.fillRoundRect(barX, barY, (int) (barW * fill), barH, 6, 6);

            if (ready) {
                g2d.setColor(new Color(180, 240, 255, 160));
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);
                g2d.setStroke(new BasicStroke(1f));
            }

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2d.drawString(ready ? "DASH  [K]" : "DASH", barX, barY - 5);
        }

        /**
         * Draws a styled game-over overlay consistent with the start and pause screens.
         * Shows the final score and offers J — Play Again and K — Main Menu.
         */
        private void drawGameOver(Graphics2D g2d, int w, int h) {
            int cx = w / 2, cy = h / 2;

            // radial vignette over the frozen world
            float[] fractions = {0f, 0.5f, 1f};
            Color[] colors = {new Color(0, 0, 0, 120), new Color(0, 0, 0, 180), new Color(0, 0, 0, 240)};
            g2d.setPaint(new java.awt.RadialGradientPaint(cx, cy,
                    Math.max(w, h) * 0.65f, fractions, colors));
            g2d.fillRect(0, 0, w, h);

            // card
            int cardW = 420, cardH = 360;
            int cardX = cx - cardW / 2, cardY = cy - cardH / 2;

            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRoundRect(cardX + 8, cardY + 8, cardW, cardH, 20, 20);

            GradientPaint cardBg = new GradientPaint(cardX, cardY, new Color(18, 5, 5),
                    cardX, cardY + cardH, new Color(35, 8, 8));
            g2d.setPaint(cardBg);
            g2d.fillRoundRect(cardX, cardY, cardW, cardH, 20, 20);

            // border — deep crimson
            g2d.setColor(new Color(180, 40, 30, 210));
            g2d.setStroke(new BasicStroke(1.8f));
            g2d.drawRoundRect(cardX, cardY, cardW, cardH, 20, 20);
            g2d.setColor(new Color(180, 40, 30, 55));
            g2d.setStroke(new BasicStroke(1f));
            g2d.drawRoundRect(cardX + 6, cardY + 6, cardW - 12, cardH - 12, 14, 14);

            // gear accents (tinted red)
            drawGear(g2d, cardX + 34, cardY + 34, 22, 8, Math.PI / 5, new Color(180, 40, 30, 90));
            drawGear(g2d, cardX + cardW - 34, cardY + 34, 22, 8, 0, new Color(180, 40, 30, 90));

            // "GAME OVER" heading
            g2d.setFont(new Font("SansSerif", Font.BOLD, 52));
            FontMetrics fmH = g2d.getFontMetrics();
            String heading = "GAME OVER";
            int hx = cx - fmH.stringWidth(heading) / 2;
            int hy = cardY + 72;

            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(heading, hx + 3, hy + 3);
            g2d.setColor(new Color(120, 20, 15, 110));
            g2d.drawString(heading, hx + 2, hy + 2);
            GradientPaint hGrad = new GradientPaint(0, hy - 44, new Color(255, 180, 160),
                    0, hy, new Color(210, 40, 30));
            g2d.setPaint(hGrad);
            g2d.drawString(heading, hx, hy);

            // divider
            int divY = cardY + 90;
            GradientPaint dl = new GradientPaint(cardX + 20, divY, new Color(0, 0, 0, 0), cx, divY, new Color(180, 40, 30, 180));
            GradientPaint dr = new GradientPaint(cx, divY, new Color(180, 40, 30, 180), cardX + cardW - 20, divY, new Color(0, 0, 0, 0));
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.setPaint(dl);
            g2d.drawLine(cardX + 20, divY, cx, divY);
            g2d.setPaint(dr);
            g2d.drawLine(cx, divY, cardX + cardW - 20, divY);

            // "FINAL SCORE" label
            String scoreLabel = "FINAL SCORE";
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));
            FontMetrics fmSL = g2d.getFontMetrics();
            g2d.setColor(new Color(180, 100, 90, 180));
            g2d.drawString(scoreLabel, cx - fmSL.stringWidth(scoreLabel) / 2, divY + 26);

            // score value
            g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
            FontMetrics fmScore = g2d.getFontMetrics();
            String scoreVal = String.valueOf(score);
            int scoreY = divY + 80;
            g2d.setColor(new Color(0, 0, 0, 160));
            g2d.drawString(scoreVal, cx - fmScore.stringWidth(scoreVal) / 2 + 2, scoreY + 2);
            GradientPaint sGrad = new GradientPaint(0, scoreY - 40, new Color(255, 210, 180),
                    0, scoreY, new Color(220, 100, 80));
            g2d.setPaint(sGrad);
            g2d.drawString(scoreVal, cx - fmScore.stringWidth(scoreVal) / 2, scoreY);

            // action buttons
            int btnY = scoreY + 30;
            float pulse = (float) (0.75 + 0.25 * Math.sin(screenTick * 0.08));
            drawMenuButton(g2d, cx - 170, btnY, "J", "PLAY AGAIN", pulse, new Color(220, 100, 30));
            drawMenuButton(g2d, cx + 20, btnY, "K", "MENU", 1.0f, new Color(130, 130, 130));

            // flavour line
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
            g2d.setColor(new Color(140, 60, 50, 160));
            String tip = "The factory claims another soul...";
            FontMetrics fmT = g2d.getFontMetrics();
            g2d.drawString(tip, cx - fmT.stringWidth(tip) / 2, cardY + cardH - 18);
        }

        /**
         * Draws an in-game pause overlay. The world is still visible underneath
         * through a dark vignette. A centred card shows the game title, score,
         * and key hints for resuming or quitting.
         */
        private void drawPauseOverlay(Graphics2D g2d, int w, int h) {
            // --- blurred vignette: dark radial gradient over the gameplay ---
            int cx = w / 2, cy = h / 2;
            float[] fractions = {0f, 0.55f, 1f};
            Color[] colors = {
                    new Color(0, 0, 0, 100),
                    new Color(0, 0, 0, 160),
                    new Color(0, 0, 0, 230)
            };
            g2d.setPaint(new java.awt.RadialGradientPaint(cx, cy,
                    Math.max(w, h) * 0.65f, fractions, colors));
            g2d.fillRect(0, 0, w, h);

            // --- card dimensions ---
            int cardW = 380, cardH = 290;
            int cardX = cx - cardW / 2;
            int cardY = cy - cardH / 2;

            // card shadow
            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRoundRect(cardX + 8, cardY + 8, cardW, cardH, 20, 20);

            // card background
            GradientPaint cardBg = new GradientPaint(cardX, cardY, new Color(18, 8, 4),
                    cardX, cardY + cardH, new Color(30, 12, 6));
            g2d.setPaint(cardBg);
            g2d.fillRoundRect(cardX, cardY, cardW, cardH, 20, 20);

            // card border — warm amber
            g2d.setColor(new Color(200, 110, 50, 200));
            g2d.setStroke(new BasicStroke(1.8f));
            g2d.drawRoundRect(cardX, cardY, cardW, cardH, 20, 20);

            // inner inset border
            g2d.setColor(new Color(200, 110, 50, 60));
            g2d.setStroke(new BasicStroke(1f));
            g2d.drawRoundRect(cardX + 6, cardY + 6, cardW - 12, cardH - 12, 14, 14);

            // gear accents (static while paused — screenTick is frozen)
            drawGear(g2d, cardX + 34, cardY + 34, 22, 8, Math.PI / 6, new Color(200, 110, 50, 90));
            drawGear(g2d, cardX + cardW - 34, cardY + 34, 22, 8, 0, new Color(200, 110, 50, 90));

            // --- "PAUSED" heading ---
            g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
            FontMetrics fmH = g2d.getFontMetrics();
            String heading = "PAUSED";
            int hx = cx - fmH.stringWidth(heading) / 2;
            int hy = cardY + 66;
            // shadow
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(heading, hx + 3, hy + 3);
            // ember glow
            g2d.setColor(new Color(160, 60, 20, 100));
            g2d.drawString(heading, hx + 2, hy + 2);
            // lit gradient
            GradientPaint hGrad = new GradientPaint(0, hy - 38, new Color(255, 220, 160),
                    0, hy, new Color(210, 90, 20));
            g2d.setPaint(hGrad);
            g2d.drawString(heading, hx, hy);

            // divider
            int divY = cardY + 82;
            GradientPaint dl = new GradientPaint(cardX + 20, divY, new Color(0, 0, 0, 0),
                    cx, divY, new Color(200, 110, 50, 180));
            GradientPaint dr = new GradientPaint(cx, divY, new Color(200, 110, 50, 180),
                    cardX + cardW - 20, divY, new Color(0, 0, 0, 0));
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.setPaint(dl);
            g2d.drawLine(cardX + 20, divY, cx, divY);
            g2d.setPaint(dr);
            g2d.drawLine(cx, divY, cardX + cardW - 20, divY);

            // --- current score ---
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 13));
            FontMetrics fmScore = g2d.getFontMetrics();
            String scoreLabel = "S C O R E   " + score;
            g2d.setColor(new Color(200, 160, 100, 200));
            g2d.drawString(scoreLabel, cx - fmScore.stringWidth(scoreLabel) / 2, divY + 24);

            // --- action buttons ---
            int btnY = divY + 50;
            drawMenuButton(g2d, cx - 160, btnY, "P", "RESUME", 1.0f, new Color(220, 100, 30));
            drawMenuButton(g2d, cx + 10, btnY, "O", "QUIT", 1.0f, new Color(140, 50, 40));

            // --- tips row ---
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
            g2d.setColor(new Color(140, 100, 60, 160));
            String tip = "Your factory awaits...";
            FontMetrics fmT = g2d.getFontMetrics();
            g2d.drawString(tip, cx - fmT.stringWidth(tip) / 2, cardY + cardH - 18);
        }

        /**
         * Draws an atmospheric start screen with title, animated gear accents,
         * and two button prompts: J to play, K to read rules.
         */
        private void drawStartScreen(Graphics2D g2d, int w, int h) {
            // dark gradient overlay
            GradientPaint bg = new GradientPaint(0, 0, new Color(10, 5, 2), 0, h, new Color(35, 15, 8));
            g2d.setPaint(bg);
            g2d.fillRect(0, 0, w, h);

            // animated gear accents
            drawGear(g2d, w / 4, h / 3, 90, 12, screenTick * 0.3, new Color(180, 90, 40, 80));
            drawGear(g2d, 3 * w / 4, 2 * h / 3, 70, 10, -screenTick * 0.4, new Color(180, 90, 40, 60));
            drawGear(g2d, w / 2 + 220, h / 2 - 140, 40, 8, screenTick * 0.7, new Color(200, 110, 50, 90));

            // glowing horizontal rules
            int lineY = h / 2 - 90;
            g2d.setStroke(new BasicStroke(1.5f));
            GradientPaint lg1 = new GradientPaint(w / 2 - 260, lineY, new Color(0, 0, 0, 0), w / 2, lineY, new Color(200, 110, 50, 200));
            GradientPaint lg2 = new GradientPaint(w / 2, lineY, new Color(200, 110, 50, 200), w / 2 + 260, lineY, new Color(0, 0, 0, 0));
            g2d.setPaint(lg1);
            g2d.drawLine(w / 2 - 260, lineY, w / 2, lineY);
            g2d.setPaint(lg2);
            g2d.drawLine(w / 2, lineY, w / 2 + 260, lineY);

            // subtitle
            String subtitle = "A R C A D E   S U R V I V A L";
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 13));
            FontMetrics fmSub = g2d.getFontMetrics();
            g2d.setColor(new Color(200, 140, 80, 200));
            g2d.drawString(subtitle, (w - fmSub.stringWidth(subtitle)) / 2, lineY - 10);

            // main title
            g2d.setFont(new Font("SansSerif", Font.BOLD, 86));
            FontMetrics fmT = g2d.getFontMetrics();
            String title = "FACTORY FLOOR";
            String title2 = "FRENZY";
            int t1x = (w - fmT.stringWidth(title)) / 2;
            int t2x = (w - fmT.stringWidth(title2)) / 2;
            int t1y = h / 2 - 10;
            int t2y = h / 2 + 80;

            // drop shadow
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(title, t1x + 4, t1y + 4);
            g2d.drawString(title2, t2x + 4, t2y + 4);
            // ember glow
            g2d.setColor(new Color(160, 60, 20, 120));
            g2d.drawString(title, t1x + 2, t1y + 2);
            g2d.drawString(title2, t2x + 2, t2y + 2);
            // lit gradient text
            GradientPaint tGrad = new GradientPaint(0, t1y - 80, new Color(255, 220, 160), 0, t2y, new Color(220, 100, 30));
            g2d.setPaint(tGrad);
            g2d.drawString(title, t1x, t1y);
            g2d.drawString(title2, t2x, t2y);

            // pulsing button prompts
            float pulse = (float) (0.75 + 0.25 * Math.sin(screenTick * 0.08));
            int btnY = t2y + 80;
            drawMenuButton(g2d, w / 2 - 200, btnY, "J", "PLAY", pulse, new Color(220, 100, 30));
            drawMenuButton(g2d, w / 2 - 20, btnY, "K", "RULES", 1.0f, new Color(130, 130, 130));
            drawMenuButton(g2d, w / 2 + 160, btnY, "L", "QUIT", 1.0f, new Color(160, 50, 50));

            // hint footer
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
            g2d.setColor(new Color(120, 80, 50, 150));
            String credit = "WASD — move   ·   J — attack   ·   K — dash   ·   L — swap weapon";
            FontMetrics fmC = g2d.getFontMetrics();
            g2d.drawString(credit, (w - fmC.stringWidth(credit)) / 2, h - 24);
        }

        /**
         * Draws a toothed gear centred at (cx, cy).
         */
        private void drawGear(Graphics2D g2d, int cx, int cy, int r, int teeth,
                              double angle, Color col) {
            int inner = (int) (r * 0.72);
            int hub = (int) (r * 0.22);
            int n = teeth * 2;
            int[] xs = new int[n], ys = new int[n];
            for (int i = 0; i < n; i++) {
                double a = angle + i * Math.PI / teeth;
                int rad = (i % 2 == 0) ? r : inner;
                xs[i] = cx + (int) (rad * Math.cos(a));
                ys[i] = cy + (int) (rad * Math.sin(a));
            }
            g2d.setColor(col);
            g2d.setStroke(new BasicStroke(2.5f));
            g2d.drawPolygon(xs, ys, n);
            g2d.drawOval(cx - hub, cy - hub, hub * 2, hub * 2);
        }

        /**
         * Draws a key badge + label button for the menu.
         */
        private void drawMenuButton(Graphics2D g2d, int x, int y, String key, String label,
                                    float pulse, Color accent) {
            // measure label to auto-size button width
            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            int labelW = g2d.getFontMetrics().stringWidth(label);
            int bw = Math.max(130, 10 + 30 + 10 + labelW + 14); // badge + gap + label + padding
            int bh = 46;
            g2d.setColor(new Color(20, 10, 5, 200));
            g2d.fillRoundRect(x, y, bw, bh, 12, 12);
            g2d.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (180 * pulse)));
            g2d.setStroke(new BasicStroke(1.8f));
            g2d.drawRoundRect(x, y, bw, bh, 12, 12);

            // key badge
            int kx = x + 10, ky = y + 8, ks = 30;
            g2d.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (220 * pulse)));
            g2d.fillRoundRect(kx, ky, ks, ks, 6, 6);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 17));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.setColor(Color.BLACK);
            g2d.drawString(key, kx + (ks - fm.stringWidth(key)) / 2, ky + 21);

            // label
            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            fm = g2d.getFontMetrics();
            g2d.setColor(new Color(230, 200, 160, (int) (240 * pulse)));
            g2d.drawString(label, kx + ks + 10, y + bh / 2 + 5);
        }

        /**
         * Draws the rules / how-to-play screen.
         */
        private void drawRulesScreen(Graphics2D g2d, int w, int h) {
            GradientPaint bg = new GradientPaint(0, 0, new Color(10, 5, 2), 0, h, new Color(25, 10, 5));
            g2d.setPaint(bg);
            g2d.fillRect(0, 0, w, h);

            drawGear(g2d, 60, h / 2, 55, 10, screenTick * 0.2, new Color(180, 90, 40, 60));
            drawGear(g2d, w - 60, h / 2, 55, 10, -screenTick * 0.2, new Color(180, 90, 40, 60));

            // heading
            String heading = "HOW TO PLAY";
            g2d.setFont(new Font("SansSerif", Font.BOLD, 46));
            FontMetrics fmH = g2d.getFontMetrics();
            int hx = (w - fmH.stringWidth(heading)) / 2;
            int hy = h / 2 - 200;
            g2d.setColor(new Color(0, 0, 0, 160));
            g2d.drawString(heading, hx + 3, hy + 3);
            GradientPaint hGrad = new GradientPaint(0, hy - 40, new Color(255, 210, 140), 0, hy, new Color(210, 90, 20));
            g2d.setPaint(hGrad);
            g2d.drawString(heading, hx, hy);

            g2d.setColor(new Color(200, 110, 50, 150));
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.drawLine(w / 2 - 220, hy + 14, w / 2 + 220, hy + 14);

            // controls table
            String[][] controls = {
                    {"WASD", "Move your character"},
                    {"J", "Attack  (hold to charge a heavy strike)"},
                    {"K", "Dash  (short burst — invincible mid-dash)"},
                    {"L", "Cycle weapon  (rapier → scythe → disc)"},
                    {"P", "Pause / resume"},
            };
            int startY = hy + 54;
            int rowH = 44;
            int colKey = w / 2 - 240;
            int colDesc = w / 2 - 90;

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2d.setColor(new Color(160, 110, 70, 160));
            g2d.drawString("KEY", colKey, startY - 8);
            g2d.drawString("ACTION", colDesc, startY - 8);

            for (int i = 0; i < controls.length; i++) {
                int ry = startY + i * rowH;
                if (i % 2 == 0) {
                    g2d.setColor(new Color(255, 150, 50, 15));
                    g2d.fillRoundRect(colKey - 10, ry - 20, 490, rowH - 4, 8, 8);
                }
                // key badge
                String k = controls[i][0];
                g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
                int bw2 = g2d.getFontMetrics().stringWidth(k) + 18;
                g2d.setColor(new Color(200, 90, 30, 220));
                g2d.fillRoundRect(colKey - 4, ry - 17, bw2, 24, 6, 6);
                g2d.setColor(Color.BLACK);
                g2d.drawString(k, colKey + 5, ry - 1);
                // description
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 15));
                g2d.setColor(new Color(230, 200, 160));
                g2d.drawString(controls[i][1], colDesc, ry - 1);
            }

            // tips
            int tipY = startY + controls.length * rowH + 20;
            g2d.setColor(new Color(200, 110, 50, 120));
            g2d.setStroke(new BasicStroke(1f));
            g2d.drawLine(w / 2 - 220, tipY, w / 2 + 220, tipY);
            String[] tips = {
                    "Survive as long as possible — enemies spawn in relentless waves.",
                    "Kill enemies to earn points. Charge J for a devastating heavy attack.",
                    "Dash through danger — you're invincible during the whole burst.",
            };
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 13));
            g2d.setColor(new Color(180, 140, 90, 180));
            for (int i = 0; i < tips.length; i++) {
                FontMetrics fmT = g2d.getFontMetrics();
                g2d.drawString("· " + tips[i], (w - fmT.stringWidth("· " + tips[i])) / 2, tipY + 22 + i * 22);
            }

            // back button
            float pulse = (float) (0.8 + 0.2 * Math.sin(screenTick * 0.08));
            drawMenuButton(g2d, w / 2 - 65, tipY + 90, "K", "BACK", pulse, new Color(130, 130, 130));
        }
    }
}
