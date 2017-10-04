package driver;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.awt.image.BufferedImage;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.formats.jpeg.JpegImageParser;
import org.apache.commons.imaging.common.bytesource.ByteSourceFile;

public class Driver {
	public static void main(String args[]) {
		if (args.length != 1) {
			System.err.println("Usage: Driver <inputfile>");
			return;
		}

		try {
			final File file = new File (args[0]);
			JpegImageParser p = new JpegImageParser();
			BufferedImage image = p.getBufferedImage(new ByteSourceFile(file), new HashMap<>());
		} catch (IOException | ImageReadException e) {
			System.err.println("Error reading image");
			e.printStackTrace();
		}

		System.out.println("Done.");
	}
}
