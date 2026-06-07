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

    /** Number of tiles in the map along the X axis. */
    static final int MAP_WIDTH  = 15;

    /** Number of tiles in the map along the Y axis. */
    static final int MAP_HEIGHT = 9;

    /** Pixel size of one world tile. Controls zoom level for the entire game. */
    static final int TILE_SIZE  = 100;

    /** The loaded tile grid. Values: 0 = void, 1 = floor, 2 = wall. */
    int[][] mapGrid = new int[MAP_HEIGHT][MAP_WIDTH];

    // --- Difficulty / spawning ---

    /**
     * Sum of ENEMY_COST for all currently live enemies.
     * Compared against SPAWN_THRESHOLD to decide when to spawn more.
     */
    int activeDifficulty = 0;

    /** Remaining "budget" of difficulty points available to spend on future spawns. */
    int remainingDifficulty = 50;

    /** Spawn a new wave when activeDifficulty drops below this value. */
    static final int SPAWN_THRESHOLD = 15;

    /** Maximum allowed value of activeDifficulty at any one time. */
    static final int MAX_DIFFICULTY  = 30;

    /** Difficulty points each enemy costs when spawned (and refunds when killed). */
    static final int ENEMY_COST      = 3;

    /** Points awarded to the player for each enemy killed. */
    static final int POINTS_PER_KILL = 10;

    /** The player's current score, incremented by POINTS_PER_KILL on each kill. */
    int score = 0;

    // --- Core objects ---

    /** The player character. */
    Player player = new Player(100);

    /** All enemies currently alive in the world. */
    ArrayList<Enemy> enemies = new ArrayList<>();

    /** The player sprite, loaded once at startup. */
    BufferedImage playerSprite = loadImage("playerOne.png");

    // --- Input state ---

    /** {@code true} while the corresponding movement key is held down. */
    boolean upHeld, leftHeld, downHeld, rightHeld;

    // --- Window ---

    /** The top-level application window. */
    JFrame window;

    /** The panel that handles all game rendering. */
    DrawPanel panel;

    /** The Swing timer that drives the game loop at ~100 ticks per second. */
    Timer gameLoop;

    // --- Pause ---

    /** The pause menu window, created on demand and disposed on resume. */
    JFrame pauseWindow;

    /** {@code true} while the game is paused; prevents tick processing. */
    boolean paused = false;

    // -------------------------------------------------------------------------

    /**
     * Entry point. Constructs the game on the Swing event dispatch thread.
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
        loadMap("sampleMap.txt");

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
        gameLoop.start();
    }

    // -------------------------------------------------------------------------
    // Game loop
    // -------------------------------------------------------------------------

    /**
     * Advances the game by one tick. Called by the Swing timer every 10 ms.
     * Order of operations matters here: the dash moves the player before normal
     * movement input is processed, projectiles move before hit detection runs,
     * and dead enemies are removed after spawning so the difficulty budget stays accurate.
     */
    private void tick() {
        player.tickDash(mapGrid);
        if (!player.dashing) player.move(upHeld, leftHeld, downHeld, rightHeld, mapGrid);

        for (Enemy enemy : enemies) enemy.move(player.x, player.y, enemies);

        player.checkEnemyCollision(enemies);

        int visibleTilesX = panel.getWidth()  / TILE_SIZE + 1;
        int visibleTilesY = panel.getHeight() / TILE_SIZE + 1;
        player.tickAttack(enemies, TILE_SIZE, visibleTilesX, visibleTilesY);

        player.moveProjectiles();
        player.checkProjectileHits(enemies);
        player.removeExpiredProjectiles();

        spawnEnemies();
        removeDeadEnemies();

        if (!player.alive()) gameLoop.stop();
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
        if (activeDifficulty >= SPAWN_THRESHOLD) return;
        while (activeDifficulty < MAX_DIFFICULTY && remainingDifficulty > 0) {
            enemies.add(new Enemy(10, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
            activeDifficulty    += ENEMY_COST;
            remainingDifficulty -= ENEMY_COST;
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
                score            += POINTS_PER_KILL;
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
    // Pause menu
    // -------------------------------------------------------------------------

    /**
     * Opens the pause menu in a small secondary window and stops the game loop.
     * Does nothing if the game is already paused.
     * The pause window captures keyboard input and listens for:
     *   {@code P} — resume the game and close the window
     *   {@code O} — quit the application
     */
    private void openPauseMenu() {
        if (paused) return;

        paused = true;
        gameLoop.stop();
        upHeld = leftHeld = downHeld = rightHeld = false;

        pauseWindow = new JFrame("Paused");
        pauseWindow.setSize(300, 150);
        pauseWindow.setLocationRelativeTo(window);
        pauseWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JLabel hint = new JLabel(
            "<html><center>P — Resume<br><br>O — Quit</center></html>",
            SwingConstants.CENTER
        );
        pauseWindow.add(hint);

        pauseWindow.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_P) {
                    paused = false;
                    gameLoop.start();
                    pauseWindow.dispose();
                    window.requestFocus();
                }
                if (e.getKeyCode() == KeyEvent.VK_O) System.exit(0);
            }
        });

        pauseWindow.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { pauseWindow.requestFocusInWindow(); }
        });

        pauseWindow.setFocusable(true);
        pauseWindow.setVisible(true);
        pauseWindow.toFront();
        pauseWindow.requestFocusInWindow();
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    /**
     * Handles key-press events. Activates movement flags, triggers attacks and dash,
     * and cycles the weapon.
     * @param e the key event from Swing
     */
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_P: openPauseMenu();              break;
            case KeyEvent.VK_W: upHeld    = true;             break;
            case KeyEvent.VK_A: leftHeld  = true;             break;
            case KeyEvent.VK_S: downHeld  = true;             break;
            case KeyEvent.VK_D: rightHeld = true;             break;
            case KeyEvent.VK_J:
                if (player.attackCooldown <= 0) player.attackHeld = true;
                break;
            case KeyEvent.VK_K: player.startDash(mapGrid);    break;
            case KeyEvent.VK_L:
                player.weaponChoice = (player.weaponChoice + 1) % 3;
                break;
            case KeyEvent.VK_8:
                enemies.add(new Enemy(10, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
                break;
        }
    }

    /**
     * Handles key-release events. Clears movement flags and fires the attack
     * when the attack button is released.
     * @param e the key event from Swing
     */
    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: upHeld    = false; break;
            case KeyEvent.VK_A: leftHeld  = false; break;
            case KeyEvent.VK_S: downHeld  = false; break;
            case KeyEvent.VK_D: rightHeld = false; break;
            case KeyEvent.VK_J:
                if (player.attackHeld && player.attackCooldown <= 0) {
                    player.attack(player.attackHoldCounter > 300, enemies);
                    player.attackHeld        = false;
                    player.attackHoldCounter = 0;
                }
                break;
        }
    }

    /**
     * Required by KeyListener; no action needed for typed events.
     * @param e the key event from Swing
     */
    @Override public void keyTyped(KeyEvent e) {}

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

        /** Tile colour for floor tiles (tile value 1). */
        private static final Color FLOOR_COLOR = new Color(138, 65,  51);

        /** Tile colour for wall tiles (tile value 2). */
        private static final Color WALL_COLOR  = new Color(199, 93,  72);

        /** Colour of the charging glow shown when a heavy attack is being charged. */
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
         * @param g the graphics context provided by Swing
         */
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w      = getWidth();
            int h      = getHeight();
            int tilesX = w / TILE_SIZE + 1;
            int tilesY = h / TILE_SIZE + 1;

            player.attackAnimations.setPlayerPosition(w / 2, h / 2);

            drawMap(g, tilesX, tilesY);
            drawPlayer(g, g2d);
            drawProjectiles(g, g2d, tilesX, tilesY);
            drawEnemies(g, tilesX, tilesY);
            drawHUD(g2d, w, h);
            if (!player.alive()) drawGameOver(g2d, w, h);
        }

        /**
         * Draws all visible map tiles. Only tiles within the viewport are drawn;
         * out-of-bounds map coordinates are silently skipped.
         * The camera is centred on the player: each tile's screen position is
         * computed by subtracting the player's sub-tile offset ({@code player.x % 1})
         * so the world scrolls smoothly rather than snapping tile-by-tile.
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
                        case 1: g.setColor(FLOOR_COLOR); g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE); break;
                        case 2: g.setColor(WALL_COLOR);  g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE); break;
                    }
                }
            }
        }

        /**
         * Draws the player sprite at the centre of the screen, along with any
         * active attack animations (stab or swing). Also draws the heavy-attack
         * charge glow if the player has held the attack button long enough.
         * @param g   the graphics context for image and glow drawing
         * @param g2d the 2D context for transformed attack animations
         */
        private void drawPlayer(Graphics g, Graphics2D g2d) {
            int cx = getWidth()  / 2;
            int cy = getHeight() / 2;

            if (player.attackHoldCounter > 300) {
                g.setColor(HEAVY_COLOR);
                g.fillOval(cx, cy, 100, 100);
            }

            g.drawImage(playerSprite, cx - 50, cy - 50, 100, 100, null);

            player.attackAnimations.drawStab(g2d);
            player.attackAnimations.drawSwing(g2d, player.facingAngle);
        }

        /**
         * Draws all live projectiles in screen space.
         *   Disc — an orange square that rotates each tick.
         *   Rapier bolt — a red triangle whose tip points in the direction of travel.
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
                    g2d.setColor(Color.ORANGE);
                    g2d.fillRect(-20, -20, 40, 40);
                    g2d.setTransform(saved);
                } else {
                    g.setColor(Color.RED);
                    Polygon bolt = new Polygon();
                    bolt.addPoint(sx, sy);
                    bolt.addPoint((int) (sx - p.vy * 25), (int) (sy + p.vx * 25));
                    bolt.addPoint((int) (sx + p.vy * 25), (int) (sy - p.vx * 25));
                    g.drawPolygon(bolt);
                }
            }
        }

        /**
         * Draws all live enemies as green ovals in screen space.
         * @param g      the graphics context to draw into
         * @param tilesX number of tiles visible horizontally, used for world-to-screen conversion
         * @param tilesY number of tiles visible vertically, used for world-to-screen conversion
         */
        private void drawEnemies(Graphics g, int tilesX, int tilesY) {
            g.setColor(Color.GREEN);
            for (Enemy enemy : enemies) {
                int sx = (int) ((enemy.x - player.x + tilesX / 2.0) * TILE_SIZE);
                int sy = (int) ((enemy.y - player.y + tilesY / 2.0) * TILE_SIZE);
                g.fillOval(sx, sy, 50, 50);
            }
        }

        /**
         * Draws all HUD elements. Currently delegates to the health bar and dash bar.
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
            if      (fill > 0.5f)  fillColor = new Color(60,  200, 80);
            else if (fill > 0.25f) fillColor = new Color(220, 180, 0);
            else                   fillColor = new Color(210, 50,  50);
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
         * @param g2d the 2D graphics context to draw into
         * @param w   panel width in pixels, used to position the bar near the right edge
         * @param h   panel height in pixels, used to position the bar near the bottom
         */
        private void drawDashBar(Graphics2D g2d, int w, int h) {
            int barW = 160, barH = 16;
            int barX = w - barW - 20;
            int barY = h - barH - 20;
            float fill    = player.dashReadiness();
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
         * Draws a semi-transparent dark overlay and a centred "GAME OVER" message.
         * Rendered on top of all other layers when the player's HP reaches zero.
         * @param g2d the 2D graphics context to draw into
         * @param w   panel width in pixels, used to centre the text
         * @param h   panel height in pixels, used to centre the text
         */
        private void drawGameOver(Graphics2D g2d, int w, int h) {
            g2d.setColor(new Color(0, 0, 0, 160));
            g2d.fillRect(0, 0, w, h);

            String text = "GAME OVER";
            g2d.setFont(new Font("SansSerif", Font.BOLD, 72));
            FontMetrics fm = g2d.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = h / 2 - 20;

            g2d.setColor(new Color(180, 40, 40));
            g2d.drawString(text, tx + 3, ty + 3); // drop shadow
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);

            String scoreText = "SCORE  " + score;
            g2d.setFont(new Font("SansSerif", Font.BOLD, 32));
            fm = g2d.getFontMetrics();
            int sx = (w - fm.stringWidth(scoreText)) / 2;
            g2d.setColor(new Color(180, 40, 40));
            g2d.drawString(scoreText, sx + 2, ty + 52);
            g2d.setColor(Color.WHITE);
            g2d.drawString(scoreText, sx, ty + 50);
        }
    }
}
