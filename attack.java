import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.*;
public class attack{
    public static void main(String[] args) {
        JFrame window = new JFrame();
        window.setSize(200,200);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        window.add(panel);
        window.setVisible(true);
        
        
    }
    public class myKeyAdapter extends KeyAdapter{
        @Override
        public void keyTyped(KeyEvent e){
                System.out.println("User typed: " + KeyEvent.getKeyText(e.getKeyCode()));
        }
    
}
}
