import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;

import javax.swing.*;
public class map implements KeyListener{
        JFrame window;
        DrawPanel panel;
        Timer timer;
		int mapWidth=15,mapHeight=9;
		int[][] mapArray = new int[mapHeight][mapWidth];
		double playerX =1,playerY=1;
		int[][] visibleMap = new int[7][7];
		
		
		



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
        window.add(panel);
        window.setVisible(true);
		try {
			in = new FileReader(mapFile);
			readFile = new BufferedReader(in);
			for (int i=0;i<mapHeight;i++){
				readinvalue= readFile.readLine();
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

		
timer = new Timer(100, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateMap();
				panel.repaint();
				
				
			}
		});
		timer.start();
        }
@Override
public void keyPressed(KeyEvent e){
    switch (e.getKeyCode()) {
		case KeyEvent.VK_W:
			playerY-=.1;
			break;
		case KeyEvent.VK_A:
			playerX-=.1;
			break;
		case KeyEvent.VK_S:
			playerY+=.1;
			break;
		case KeyEvent.VK_D:
			playerX+=.1;
			break;
	
		default:
			break;
	}
}
public void updateMap(){
	for(int i=0;i<7;i++){
		for(int j=0;j<7;j++){
			if((int)playerX-2+j<0||(int)playerX+2+j>mapWidth||playerY-2+i<0||playerY+2+i>mapHeight){
				visibleMap[i][j]=0;
			}else{
				visibleMap[i][j]=mapArray[(int)playerY-2+i][(int) playerX-2+j];
			}
			System.out.print(visibleMap[i][j]);
			}
			System.out.println();
		}

	}

public void keyTyped(KeyEvent e) {}
	public void keyReleased(KeyEvent e) {}

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
			
			for(int i =0;i<7;i++){
				for (int j=0;j<7;j++){
					switch (visibleMap[i][j]) {
						case 0:
							break;
						case 1:
							g.setColor(floor);
							g.fillRect((int)((j-playerX%1)*panW/5),(int)((i-playerY%1)*panH/5),panW/5+1,panH/5+1);
							break;
						case 2:
							g.setColor(wall);
							g.fillRect((int)((j-playerX%1)*panW/5),(int)((i-playerY%1)*panH/5),panW/5+1,panH/+1);
					
						default:
							break;
					
					
				}
			}
		}
			
			
			
			this.setPreferredSize(new Dimension(panW, panH));
		}
    
}
}


