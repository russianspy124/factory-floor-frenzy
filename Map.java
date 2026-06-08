import java.awt.*;
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
    int mapWidth = 15, mapHeight = 9, tileSize = 100, tilesX, tilesY, attackCooldown, attackHoldCounter = 0;
    int weaponchoice = 0; //0 - rapier, 1 - scythe, 2 - disc
    int playerDirection =0; //0 down, 1 right, 2 up, 3 left
    int difficultyValue=0,stageDifficulty=50, stageCount=0, difficultyMax=30; //all used in wave spawning
    int iFrames=0;
    int[][] mapArray = new int[mapHeight][mapWidth];
    double playerX = 4, playerY = 4;
    boolean WPressed = false, APressed = false, SPressed = false, DPressed = false, attackHeld;
    BufferedImage playerSprite = loadImage("playerSprite.png");
    ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    ArrayList<Projectile> projectiles = new ArrayList<Projectile>();
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


        timer = new Timer(10, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                move();
                moveEnemies();
                checkEnemyCollision();
                if (attackCooldown > 0) {
                    attackCooldown--;
                }

                if (attackHeld) {
                    attackHoldCounter++;
                }
                checkProjectileHits();
                checkProjectileLifespan();
                moveProjectiles();
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
    pauseWindow.setSize(300, 220);
    pauseWindow.setLocationRelativeTo(window);
    pauseWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    
    JPanel pausePanel = new JPanel();

    JLabel label1 = new JLabel(
        "                                  Resume Game - P ",
        SwingConstants.CENTER
    );

    JLabel label2 = new JLabel(
        "                                  Exit Game - O",
        SwingConstants.CENTER
    );
    pausePanel.setLayout(new BoxLayout(pausePanel,BoxLayout.Y_AXIS));
    pausePanel.add(new JLabel(" "));
    pausePanel.add(new JLabel(" "));
    pausePanel.add(new JLabel(" "));


    pausePanel.add(new JLabel("                                  Score:   " + (String.valueOf(difficultyValue-30))));
    pausePanel.add(label1);
    pausePanel.add(label2);
    pauseWindow.add(pausePanel);

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
        //0 down, 1 right, 2 up, 3 left
        if (k == KeyEvent.VK_W) {
            WPressed = true;
            playerDirection=2;
        }
        if (k == KeyEvent.VK_A) {
            APressed = true;
            playerDirection=3;

        }

        if (k == KeyEvent.VK_S) {
            SPressed = true;
            playerDirection=0;

        }

        if (k == KeyEvent.VK_D) {
            DPressed = true;
            playerDirection=1;
        }
        //Spawns Enemies for testing
        if (k == KeyEvent.VK_8) {
            for (int i = 0; i < 1; i++) {
                enemies.add(new Enemy(0,10, 0.075, Math.random() * mapWidth, Math.random() * mapHeight));
            }
        }
        if (k == KeyEvent.VK_J) {
//            Attack
            if (attackCooldown <= 0) {
                attackHeld = true;
            }
        }
        if (k == KeyEvent.VK_K) {
//            TODO: Dash

        }
        if (k == KeyEvent.VK_L) {
            // TODO: switches Weapon,  0 - rapier, 1 - scythe, 2 - disc
            // 	weaponchoice++;
            // 	weaponchoice=weaponchoice%3;

        }
        if (k == KeyEvent.VK_U) {
//            TODO: Pickup

        }
        if (k == KeyEvent.VK_I) {
//            TODO: Unbinded

        }
        if (k == KeyEvent.VK_O) {
//            TODO: Escape

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
        if (k == KeyEvent.VK_V && attackHeld) {
            if (attackCooldown <= 0) {
                attack(attackHoldCounter > 300);
                attackHeld = false;
                attackHoldCounter = 0;
            }
        }
    }

    
    
    void move() { //checks collisions then moves player
        if (WPressed) {
            if (mapArray[(int) (playerY)][(int) (playerX)] == 1) {
                playerY -= .1;
            }
        }
        if (APressed) {
            if (mapArray[(int) (playerY + .3)][(int) (playerX - .4)] == 1) {
                playerX -= .1;
            }
        }
        if (SPressed) {
            if (mapArray[(int) (playerY + 1.1)][(int) (playerX)] == 1) {
                playerY += .1;
            }

        }
        if (DPressed) {
            if (mapArray[(int) (playerY + .3)][(int) (playerX + 0.8)] == 1) {
                playerX += .1;
            }
        }

    }

    void moveEnemies() {
        for (Enemy enemy : enemies) {
            enemy.move(playerX, playerY, enemies);
        }
    }
    
    void checkEnemyCollision(){
        if(iFrames>0){
            iFrames--;
        }else{
             for (Enemy enemy : enemies) {
            if (enemy.dist<=0.2){
                iFrames+=50;
                player.takeDamage((int)enemy.damage);
            }
        }
        }
       
    }
    void spawnEnemies(){
        if (difficultyValue<15){//once there are fewer than a certain amount of enemies in a room, will spawn next wave
            while(difficultyValue<difficultyMax&&stageDifficulty>0){//spawns wave until room runs out of enemies to spawn or max difficulty is reached
                switch ((int)Math.random()*5){
                    case 1:
                        enemies.add(new Enemy(1,25, 0.04, Math.random() * mapWidth, Math.random() * mapHeight));
                        System.out.println("Tank spawned");
                        difficultyValue+=5;
                        stageDifficulty-=5;
                        break;
                    default:
                     enemies.add(new Enemy(0,10, 0.075, Math.random() * mapWidth, Math.random() * mapHeight));
                     System.out.println("Enemy spawned");
                     difficultyValue+=3;
                     stageDifficulty-=3;

                }
                
                
            } if (difficultyValue<3){
                stageDifficulty++; //increases number of enemies that will spawn in following stage
                stageDifficulty=(int)50*(stageCount+2/2);
                difficultyMax+=10; //increases enemies that can spawn in a wave

            }
        }
    }

    void removeDeadEnemies() {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            if (!enemies.get(i).alive()) {
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
        //DEBUG
        //if (img == null) System.out.println("null");
        //else System.out.printf("w=%d, h=%d%n",img.getWidth(), img.getHeight());
        return img;
    }

    void attack(boolean heavy) { //0 - rapier, 1 - scythe, 2 - disc
        if (enemies.isEmpty()) {
            return;
        }
        Enemy closest = enemies.get(0);
        double closestdist = 100;
        System.out.println("attacked");
        for (Enemy enemy : enemies) {
            if (enemy.dist < closestdist) {
                closestdist = enemy.dist;
                closest = enemy;
            }
        }
        if (weaponchoice == 0) {//rapier
            projectiles.add(new Projectile((closest.x - playerX) / closestdist, (closest.y - playerY) / closestdist));
            attackCooldown = 100;

        }
        if (weaponchoice == 1) {//scythe

        }
        if (weaponchoice == 2) {//disc

        }

    }

    void moveProjectiles() {
        for (Projectile projectile : projectiles) {
            System.out.println("projectile moved");
            projectile.extend();
            // System.out.println(projectile.lifespan);
        }
    }

    void checkProjectileLifespan() {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile projectile = projectiles.get(i);
            if (projectile.lifespan <= 0) {
                projectiles.remove(i);
            }
        }
    }

    void checkProjectileHits() {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile projectile = projectiles.get(i);
            double projWorldX = playerX + projectile.x / 4.0;
            double projWorldY = playerY + projectile.y / 4.0;

            for (Enemy enemy : enemies) {
                double dx = projWorldX - enemy.x;
                double dy = projWorldY - enemy.y;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < 0.5) {
                    enemy.takeDamage(10);
                    projectiles.remove(i);
                    break;
                }
            }
        }
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
            tilesX = ((int) this.getWidth() / tileSize) + 1; //Determines how many tiles can fit across the screen
            tilesY = ((int) this.getHeight() / tileSize) + 1;
            for (int i = 0; i <= tilesY; i++) {//Draws only as many tiles as are visible on screen
                for (int j = 0; j <= tilesX; j++) {
                    //||(int)playerX+(tilesX/2)+j>mapWidth
                    //||playerY+(tilesY/2)+i>mapHeight
                    if (!((int) playerX - (tilesX / 2) + j < 0 || (int) playerY - (tilesY / 2) + i < 0 || playerY - (tilesY / 2) + i > mapHeight || playerX - (tilesX / 2) + j > mapWidth)) {//checks to see if the index in the array is in bounds before trying to draw
                        switch (mapArray[(int) playerY - (tilesY / 2) + i][(int) playerX - (tilesX / 2) + j]) {
                            case 0:
                                break;
                            case 1:
                                g.setColor(floor);
                                g.fillRect((int) ((j - playerX % 1) * tileSize), (int) ((i - playerY % 1) * tileSize), tileSize, tileSize);
                                break;
                            case 2:
                                g.setColor(wall);
                                g.fillRect((int) ((j - playerX % 1) * tileSize), (int) ((i - playerY % 1) * tileSize), tileSize, tileSize);

                            default:
                                break;
                        }
                    }
                }
                if (attackHoldCounter > 300) {
                    g.setColor(heavy);
                    g.fillOval(this.getWidth() / 2, this.getHeight() / 2, 100, 100);
                }
                g.drawImage(playerSprite, this.getWidth() / 2, this.getHeight() / 2,this.getWidth()/2+80,this.getHeight() / 2 + 105,playerDirection*80,0 ,playerDirection*80+80, 105,  null);
                
                g.setColor(Color.red);
                for (Projectile projectile : projectiles) {
                    Polygon p = new Polygon();
                    p.addPoint(this.getWidth() / 2, this.getHeight() / 2);
                    p.addPoint((int) ((projectile.x - projectile.vy) * 25) + this.getWidth() / 2, (int) ((projectile.y + projectile.vx) * 25) + this.getHeight() / 2);
                    p.addPoint((int) ((projectile.x + projectile.vy) * 25) + this.getWidth() / 2, (int) ((projectile.y - projectile.vx) * 25) + this.getHeight() / 2);
                    g.drawPolygon(p);
                }

                g.setColor(Color.green);
                for (Enemy enemy : enemies) {
                    g.fillOval((int) ((enemy.x - playerX + tilesX / 2) * tileSize), (int) ((enemy.y - playerY + (tilesY / 2)) * tileSize), 50, 50);
                }
                g.setColor(Color.lightGray);
                g.fillRect(10,panH-200,200, 200);
                g.setColor(Color.gray);
                g.fillRect(20, panH-160, 150, 150);

                g.setColor(Color.green);
                g.fillRect(20,panH-190,(int)((player.hp/player.maxHp*150)),20);
                




                this.setPreferredSize(new Dimension(panW, panH));
            }

        }

    }
}


