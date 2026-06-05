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
    JFrame window;
    DrawPanel panel;
    Timer timer;
    int mapWidth = 15, mapHeight = 9, tileSize = 100, tilesX, tilesY;
    int difficultyValue = 0, roomDifficulty = 50;
    int[][] mapArray = new int[mapHeight][mapWidth];
    boolean WPressed = false, APressed = false, SPressed = false, DPressed = false;
    BufferedImage playerSprite = loadImage("playerOne.png");
    ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    Player player = new Player(100);

    // Pause menu
    JFrame pauseWindow;
    boolean paused = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new Map();
            }
        });
    }

    Map() {
        window = new JFrame("MAP");
        window.setSize(200, 200);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.addKeyListener(this);
        File mapFile = new File("sampleMap.txt");
        FileReader in;
        BufferedReader readFile;
        String readinvalue;

        panel = new DrawPanel();
        window.setExtendedState(JFrame.MAXIMIZED_BOTH);
        window.add(panel);
        window.setVisible(true);
        window.requestFocus();
        try {
            in = new FileReader(mapFile);
            readFile = new BufferedReader(in);
            for (int i = 0; i < mapHeight; i++) {
                readinvalue = readFile.readLine();
                System.out.println(readinvalue);
                for (int j = 0; j < mapWidth; j++) {
                    mapArray[i][j] = Integer.parseInt(readinvalue.substring(j, j + 1));
                }
            }
            readFile.close();
            in.close();
        } catch (Exception e) {
            System.out.println("An error has occured loading the map from file");
        }

        // Set player starting position
        player.x = 4;
        player.y = 4;

        timer = new Timer(10, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                player.move(WPressed, APressed, SPressed, DPressed, mapArray);
                moveEnemies();
                player.checkEnemyCollision(enemies);
                int curTilesX = panel.getWidth() / tileSize + 1;
                int curTilesY = panel.getHeight() / tileSize + 1;
                player.tickAttack(enemies, tileSize, curTilesX, curTilesY);
                player.checkProjectileHits(enemies);
                player.checkProjectileLifespan();
                player.moveProjectiles();
                spawnEnemies();
                removeDeadEnemies();
                panel.repaint();
            }
        });
        timer.start();
    }

    private void openPauseMenu() {
        if (paused) {
            return;
        }

        paused = true;
        timer.stop();

        // Prevent stuck movement keys after resuming
        WPressed = false;
        APressed = false;
        SPressed = false;
        DPressed = false;

        pauseWindow = new JFrame("Paused");
        pauseWindow.setSize(300, 150);
        pauseWindow.setLocationRelativeTo(window);
        pauseWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JLabel label = new JLabel(
            "<html><center>P - Resume Game<br><br>O - Exit Game</center></html>",
            SwingConstants.CENTER
        );

        pauseWindow.add(label);

        pauseWindow.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_P) {
                    paused = false;
                    timer.start();
                    pauseWindow.dispose();
                    // Return keyboard focus to the game window
                    window.requestFocus();
                }
                if (k == KeyEvent.VK_O) {
                    System.exit(0);
                }
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

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        // Pause menu
        if (k == KeyEvent.VK_P) {
            openPauseMenu();
            return;
        }

        if (k == KeyEvent.VK_W) {
            WPressed = true;
        }
        if (k == KeyEvent.VK_A) {
            APressed = true;
        }
        if (k == KeyEvent.VK_S) {
            SPressed = true;
        }
        if (k == KeyEvent.VK_D) {
            DPressed = true;
        }
        // Spawns enemies for testing
        if (k == KeyEvent.VK_8) {
            for (int i = 0; i < 1; i++) {
                enemies.add(new Enemy(10, Math.random() * mapWidth, Math.random() * mapHeight));
            }
        }
        if (k == KeyEvent.VK_J) {
            // Attack
            if (player.attackCooldown <= 0) {
                player.attackHeld = true;
            }
        }
        if (k == KeyEvent.VK_K) {
            // TODO: Dash
        }
        if (k == KeyEvent.VK_L) {
            // TODO: Switch weapon — 0 - rapier, 1 - scythe, 2 - disc
             player.weaponChoice++;
             player.weaponChoice = player.weaponChoice % 3;
        }
        if (k == KeyEvent.VK_U) {
            // TODO: Pickup
        }
        if (k == KeyEvent.VK_I) {
            // TODO: Unbound
        }
        if (k == KeyEvent.VK_O) {
            // TODO: Escape
        }
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_W) {
            WPressed = false;
        }
        if (k == KeyEvent.VK_A) {
            APressed = false;
        }
        if (k == KeyEvent.VK_S) {
            SPressed = false;
        }
        if (k == KeyEvent.VK_D) {
            DPressed = false;
        }
        if (k == KeyEvent.VK_J && player.attackHeld) {
            if (player.attackCooldown <= 0) {
                player.attack(player.attackHoldCounter > 300, enemies);
                player.attackHeld = false;
                player.attackHoldCounter = 0;
            }
        }
    }

    void moveEnemies() {
        for (Enemy enemy : enemies) {
            enemy.move(player.x, player.y, enemies);
        }
    }

    void spawnEnemies() {
        if (difficultyValue < 15) { // once fewer than a certain number of enemies remain, spawn next wave
            while (difficultyValue < 30 && roomDifficulty > 0) { // spawn until room runs out or max difficulty reached
                // TODO: refactor when new enemy types are coded
                enemies.add(new Enemy(10, Math.random() * mapWidth, Math.random() * mapHeight));
                System.out.println("Enemy spawned");
                difficultyValue += 3;
                roomDifficulty -= 3;
            }
        }
    }

    void removeDeadEnemies() {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            if (!enemies.get(i).alive()) {
                difficultyValue -= 3;
                enemies.remove(i);
            }
        }
    }

    static BufferedImage loadImage(String filename) {
        BufferedImage img = null;
        try {
            img = ImageIO.read(new File(filename));
        } catch (IOException e) {
            System.out.println(e.toString());
            JOptionPane.showMessageDialog(null, "An image failed to load: " + filename, "Error", JOptionPane.ERROR_MESSAGE);
        }
        return img;
    }

    class DrawPanel extends JPanel {
        int panW, panH;
        Color wall = new Color(199, 93, 72);
        Color floor = new Color(138, 65, 51);
        Color heavy = new Color(255, 255, 255, 100);

        DrawPanel() {
            panW = 800;
            panH = 500;
            this.setPreferredSize(new Dimension(panW, panH));
            this.setBackground(Color.BLACK);
        }

        @Override
        public void paintComponent(Graphics g) {
            panW = this.getWidth();
            panH = this.getHeight();

            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Keep AttackAnimations in sync with the player's screen-center position
            player.attackAnimations.setPlayerPosition(this.getWidth() / 2, this.getHeight() / 2);

            tilesX = ((int) this.getWidth() / tileSize) + 1;
            tilesY = ((int) this.getHeight() / tileSize) + 1;
            for (int i = 0; i <= tilesY; i++) {
                for (int j = 0; j <= tilesX; j++) {
                    if (!((int) player.x - (tilesX / 2) + j < 0
                            || (int) player.y - (tilesY / 2) + i < 0
                            || player.y - (tilesY / 2) + i >= mapHeight
                            || player.x - (tilesX / 2) + j >= mapWidth)) {
                        switch (mapArray[(int) player.y - (tilesY / 2) + i][(int) player.x - (tilesX / 2) + j]) {
                            case 0:
                                break;
                            case 1:
                                g.setColor(floor);
                                g.fillRect((int) ((j - player.x % 1) * tileSize), (int) ((i - player.y % 1) * tileSize), tileSize, tileSize);
                                break;
                            case 2:
                                g.setColor(wall);
                                g.fillRect((int) ((j - player.x % 1) * tileSize), (int) ((i - player.y % 1) * tileSize), tileSize, tileSize);
                            default:
                                break;
                        }
                    }
                }
                if (player.attackHoldCounter > 300) {
                    g.setColor(heavy);
                    g.fillOval(this.getWidth() / 2, this.getHeight() / 2, 100, 100);
                }
                g.drawImage(playerSprite, this.getWidth() / 2 - 50, this.getHeight() / 2 - 50, 100, 100, null);

                // Draw active attack animations
                player.attackAnimations.drawStab(g2d);
                player.attackAnimations.drawSwing(g2d, player.facingAngle);

                // Draw all projectiles (rapier triangles and spinning disc squares)
                for (Projectile projectile : player.projectiles) {
                    int screenX = (int) ((projectile.x - player.x + tilesX / 2.0) * tileSize);
                    int screenY = (int) ((projectile.y - player.y + tilesY / 2.0) * tileSize);
                    if (projectile.isDisc) {
                        AffineTransform old2 = g2d.getTransform();
                        g2d.translate(screenX, screenY);
                        g2d.rotate(projectile.rotation);
                        g2d.setColor(Color.ORANGE);
                        g2d.fillRect(-20, -20, 40, 40);
                        g2d.setTransform(old2);
                    } else {
                        g.setColor(Color.red);
                        Polygon p = new Polygon();
                        p.addPoint(screenX, screenY);
                        p.addPoint((int) (screenX + (-projectile.vy) * 25), (int) (screenY + projectile.vx * 25));
                        p.addPoint((int) (screenX + projectile.vy * 25), (int) (screenY + (-projectile.vx) * 25));
                        g.drawPolygon(p);
                    }
                }

                g.setColor(Color.green);
                for (Enemy enemy : enemies) {
                    g.fillOval((int) ((enemy.x - player.x + tilesX / 2) * tileSize), (int) ((enemy.y - player.y + (tilesY / 2)) * tileSize), 50, 50);
                }

                this.setPreferredSize(new Dimension(panW, panH));
            }
        }
    }
}
