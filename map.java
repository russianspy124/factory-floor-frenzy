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

import javax.swing.*;
public class map implements KeyListener{
        JFrame window;
        DrawPanel panel;
        Timer timer;
		int mapWidth=15,mapHeight=9,tileSize=100,tilesX,tilesY;
		int[][] mapArray = new int[mapHeight][mapWidth];
		double playerX =4,playerY=4;
		boolean WPressed=false,APressed=false,SPressed=false,DPressed=false;
		BufferedImage player = loadImage("factory-floor-frenzy\\player.png");
		

		
		// int[][] visibleMap = new int[7][7];
		
		
		



    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new map();
			}
    });
}
    


map(){
     window = new JFrame("MAP");
        window.setSize(200,200);
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
		try {
			in = new FileReader(mapFile);
			readFile = new BufferedReader(in);
			for (int i=0;i<mapHeight;i++){
				readinvalue= readFile.readLine();
				System.out.println(readinvalue);
				for(int j=0;j<mapWidth;j++){
					mapArray[i][j]=Integer.parseInt(readinvalue.substring(j,j+1));
				}
			
			
		
		}
		readFile.close();
		in.close();
		System.out.println("mapArray created");
		} catch (Exception e) {
			System.out.println("An error has occured loading the map from file");
		}


		
timer = new Timer(10, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				move();
				panel.repaint();
				

				
				
			}
		});
		timer.start();
        }
@Override
public void keyPressed(KeyEvent e){
	  int k = e.getKeyCode();
		if (k==KeyEvent.VK_W){
			WPressed=true;
		// 	if(mapArray[(int)playerX][(int)(playerY-.5)]==1){
		// playerY-=.1;
			// }
		}
		if (k==KeyEvent.VK_A){
			APressed=true;
			// playerX-=.1;
		}
			
		if (k==KeyEvent.VK_S){
			SPressed=true;
			// playerY+=.1;
		}
			
		if (k==KeyEvent.VK_D){
			DPressed=true;
			// playerX+=.1;
}
	
}

public void keyTyped(KeyEvent e) {}

	public void keyReleased(KeyEvent e) {
		int k = e.getKeyCode();
		if (k==KeyEvent.VK_W){
			WPressed=false;
		}
		if (k==KeyEvent.VK_A){
			APressed=false;
			
		}
			
		if (k==KeyEvent.VK_S){
			SPressed=false;
			
		}
			
		if (k==KeyEvent.VK_D){
			DPressed=false;
			
}
	}

 void move(){
	if(WPressed){
		if(mapArray[(int)(playerY-.3)][(int)(playerX)]==1){
			playerY-=.1;
			}
		}
	if(APressed){
		if(mapArray[(int)(playerY)][(int)(playerX-.9)]==1){
			playerX-=.1;
			}
	}
	if(SPressed){
		if(mapArray[(int)(playerY+.1)][(int)(playerX)]==1){
			playerY+=.1;
			}
		
	}
	if(DPressed){
		if(mapArray[(int)(playerY)][(int)(playerX+.1)]==1){
			playerX+=.1;
			}
	}
	
 }
 static BufferedImage loadImage(String filename) {
	BufferedImage img = null;
	try{
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

 class DrawPanel extends JPanel {
            int panW, panH;
			Color wall = new Color(199, 93, 72);
			Color floor = new Color(138, 65, 51);
			
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
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			tilesX=((int)this.getWidth()/tileSize)+1; //Determines how many tiles can fit across the screen
			tilesY=((int)this.getHeight()/tileSize)+1;
			for(int i=0;i<=tilesY;i++){//Draws only as many tiles as are visible on screen
		for(int j=0;j<=tilesX;j++){
			//||(int)playerX+(tilesX/2)+j>mapWidth
			//||playerY+(tilesY/2)+i>mapHeight
			if(!((int)playerX-(tilesX/2)+j<0||(int)playerY-(tilesY/2)+i<0||playerY-(tilesY/2)+i>mapHeight||playerX-(tilesX/2)+j>mapWidth)){//checks to see if the index in the array is in bounds before trying to draw
				switch (mapArray[(int)playerY-(tilesY/2)+i][(int) playerX-(tilesX/2)+j]) {
						case 0:
							break;
						case 1:
							g.setColor(floor);
							g.fillRect((int)((j-playerX%1)*tileSize),(int)((i-playerY%1)*tileSize),tileSize,tileSize);
							break;
						case 2:
							g.setColor(wall);
							g.fillRect((int)((j-playerX%1)*tileSize),(int)((i-playerY%1)*tileSize),tileSize,tileSize);
					
						default:
							break;
			}}}
			g.drawImage(player,this.getWidth()/2-50,this.getHeight()/2-50,100,100,null);
			// for(int i =0;i<7;i++){
			// 	for (int j=0;j<7;j++){
					// switch (visibleMap[i][j]) {
					// 	case 0:
					// 		break;
					// 	case 1:
					// 		g.setColor(floor);
					// 		g.fillRect((int)((j-playerX%1)*panW/5),(int)((i-playerY%1)*panH/5),panW/5+1,panH/5+1);
					// 		break;
					// 	case 2:
					// 		g.setColor(wall);
					// 		g.fillRect((int)((j-playerX%1)*panW/5),(int)((i-playerY%1)*panH/5),panW/5+1,panH/+1);
					
					// 	default:
					// 		break;
					
					
				// }}}
			this.setPreferredSize(new Dimension(panW, panH));
		}

	}
    
}
}


