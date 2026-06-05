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

public class Map implements KeyListener {

    // --- Map layout ---
    static final int MAP_WIDTH = 15;
    static final int MAP_HEIGHT = 9;
    static final int TILE_SIZE = 100;
    int[][] mapGrid = new int[MAP_HEIGHT][MAP_WIDTH];

    // --- Difficulty / spawning ---
    int activeDifficulty = 0;
    int remainingDifficulty = 50;
    static final int SPAWN_THRESHOLD = 15; // spawn a new wave when enemy count drops below this
    static final int MAX_DIFFICULTY = 30;
    static final int ENEMY_COST = 3;

    // --- Core objects ---
    Player player = new Player(100);
    ArrayList<Enemy> enemies = new ArrayList<>();
    BufferedImage playerSprite = loadImage("playerOne.png");

    // --- Input state ---
    boolean upHeld, leftHeld, downHeld, rightHeld;

    // --- Window ---
    JFrame window;
    DrawPanel panel;
    Timer gameLoop;

    // --- Pause ---
    JFrame pauseWindow;
    boolean paused = false;

    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Map::new);
    }

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

    private void tick() {
        player.tickDash(mapGrid);
        if (!player.dashing) player.move(upHeld, leftHeld, downHeld, rightHeld, mapGrid);

        for (Enemy enemy : enemies) enemy.move(player.x, player.y, enemies);

        player.checkEnemyCollision(enemies);

        int visibleTilesX = panel.getWidth() / TILE_SIZE + 1;
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

    private void spawnEnemies() {
        if (activeDifficulty >= SPAWN_THRESHOLD) return;
        while (activeDifficulty < MAX_DIFFICULTY && remainingDifficulty > 0) {
            enemies.add(new Enemy(10, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
            activeDifficulty += ENEMY_COST;
            remainingDifficulty -= ENEMY_COST;
        }
    }

    private void removeDeadEnemies() {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            if (!enemies.get(i).alive()) {
                activeDifficulty -= ENEMY_COST;
                enemies.remove(i);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Map loading
    // -------------------------------------------------------------------------

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
            @Override
            public void keyPressed(KeyEvent e) {
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
            @Override
            public void windowOpened(WindowEvent e) {
                pauseWindow.requestFocusInWindow();
            }
        });

        pauseWindow.setFocusable(true);
        pauseWindow.setVisible(true);
        pauseWindow.toFront();
        pauseWindow.requestFocusInWindow();
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_P:
                openPauseMenu();
                break;
            case KeyEvent.VK_W:
                upHeld = true;
                break;
            case KeyEvent.VK_A:
                leftHeld = true;
                break;
            case KeyEvent.VK_S:
                downHeld = true;
                break;
            case KeyEvent.VK_D:
                rightHeld = true;
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
                enemies.add(new Enemy(10, Math.random() * MAP_WIDTH, Math.random() * MAP_HEIGHT));
                break;
        }
    }

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

    @Override
    public void keyTyped(KeyEvent e) {
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    class DrawPanel extends JPanel {

        private static final Color FLOOR_COLOR = new Color(138, 65, 51);
        private static final Color WALL_COLOR = new Color(199, 93, 72);
        private static final Color HEAVY_COLOR = new Color(255, 255, 255, 100);

        DrawPanel() {
            setPreferredSize(new Dimension(800, 500));
            setBackground(Color.BLACK);
        }

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
            drawPlayer(g, g2d);
            drawProjectiles(g, g2d, tilesX, tilesY);
            drawEnemies(g, tilesX, tilesY);
            drawHUD(g2d, w, h);
            if (!player.alive()) drawGameOver(g2d, w, h);
        }

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
                            g.setColor(FLOOR_COLOR);
                            g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                            break;
                        case 2:
                            g.setColor(WALL_COLOR);
                            g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                            break;
                    }
                }
            }
        }

        private void drawPlayer(Graphics g, Graphics2D g2d) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            if (player.attackHoldCounter > 300) {
                g.setColor(HEAVY_COLOR);
                g.fillOval(cx, cy, 100, 100);
            }

            g.drawImage(playerSprite, cx - 50, cy - 50, 100, 100, null);

            player.attackAnimations.drawStab(g2d);
            player.attackAnimations.drawSwing(g2d, player.facingAngle);
        }

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
                    // Rapier bolt drawn as a small triangle pointing in the direction of travel
                    g.setColor(Color.RED);
                    Polygon bolt = new Polygon();
                    bolt.addPoint(sx, sy);
                    bolt.addPoint((int) (sx - p.vy * 25), (int) (sy + p.vx * 25));
                    bolt.addPoint((int) (sx + p.vy * 25), (int) (sy - p.vx * 25));
                    g.drawPolygon(bolt);
                }
            }
        }

        private void drawEnemies(Graphics g, int tilesX, int tilesY) {
            g.setColor(Color.GREEN);
            for (Enemy enemy : enemies) {
                int sx = (int) ((enemy.x - player.x + tilesX / 2.0) * TILE_SIZE);
                int sy = (int) ((enemy.y - player.y + tilesY / 2.0) * TILE_SIZE);
                g.fillOval(sx, sy, 50, 50);
            }
        }

        private void drawHUD(Graphics2D g2d, int w, int h) {
            drawHealthBar(g2d, h);
            drawDashBar(g2d, w, h);
        }

        private void drawHealthBar(Graphics2D g2d, int h) {
            int barW = 200, barH = 16;
            int barX = 20, barY = h - barH - 20;
            float fill = Math.max(0f, (float) player.hp / player.maxHp);

            // Track
            g2d.setColor(new Color(30, 30, 30, 200));
            g2d.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);

            // Fill — shifts from green to yellow to red as HP falls
            Color fillColor;
            if (fill > 0.5f) fillColor = new Color(60, 200, 80);
            else if (fill > 0.25f) fillColor = new Color(220, 180, 0);
            else fillColor = new Color(210, 50, 50);
            g2d.setColor(fillColor);
            g2d.fillRoundRect(barX, barY, (int) (barW * fill), barH, 6, 6);

            // Flashing outline during i-frames
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

        private void drawDashBar(Graphics2D g2d, int w, int h) {
            int barW = 160, barH = 16;
            int barX = w - barW - 20;
            int barY = h - barH - 20;
            float fill = player.dashReadiness();
            boolean ready = fill >= 1f;

            // Track
            g2d.setColor(new Color(30, 30, 30, 200));
            g2d.fillRoundRect(barX - 2, barY - 2, barW + 4, barH + 4, 8, 8);

            // Fill
            g2d.setColor(ready ? new Color(100, 210, 255) : new Color(60, 130, 200));
            g2d.fillRoundRect(barX, barY, (int) (barW * fill), barH, 6, 6);

            // Glow outline when ready
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

        private void drawGameOver(Graphics2D g2d, int w, int h) {
            g2d.setColor(new Color(0, 0, 0, 160));
            g2d.fillRect(0, 0, w, h);

            String text = "GAME OVER";
            g2d.setFont(new Font("SansSerif", Font.BOLD, 72));
            FontMetrics fm = g2d.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = h / 2 - 20;

            g2d.setColor(new Color(180, 40, 40)); // drop shadow
            g2d.drawString(text, tx + 3, ty + 3);
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);
        }
    }
}
