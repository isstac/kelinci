import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class DriverKelinci
{    
  public static void main(final String[] args) {

    if (args.length != 1) {
      System.err.println("Expects file name as parameter");
      return;
    }

    try {
      File imageFile = new File(args[0]);
      BufferedImage image = ImageIO.read(imageFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
    	
    System.out.println("Done.");
  }
}
