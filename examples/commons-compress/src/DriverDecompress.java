package driver;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import org.apache.commons.io.input.BoundedInputStream;

public class DriverDecompress {
	public static void main(String args[]) {
		if (args.length != 1) {
			System.err.println("Usage: Driver <inputfile>");
			return;
		}

		try (FileInputStream fis = new FileInputStream(args[0])) {

			// bound bzip2 archive size to 250 bytes
			BoundedInputStream bis = new BoundedInputStream(fis, 250);

			// decompress
			BZip2CompressorInputStream in = new BZip2CompressorInputStream(bis);

			int b;
			int i = 0;
			while (((b = fis.read()) != -1) && (i < 250) )  { // read 250 bytes max
				// should we do something with the bytes read from the stream here?
				i++;
			}

		} catch (IOException e) {
			System.err.println("Error reading input file");
			e.printStackTrace();
		}

		System.out.println("Done.");
	}
}
