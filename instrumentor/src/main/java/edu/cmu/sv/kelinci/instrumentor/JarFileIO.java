package edu.cmu.sv.kelinci.instrumentor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;

/**
 * @author rodykers
 *
 */
public class JarFileIO {

	private File inputJar, outputJar;
	private Path tempDir;
	
	/**
	 * Start from a copy of the JAR to include manifest and all other resources.
	 */
	private void createJar() {
		String in = Options.v().getRawInput();
		String out = Options.v().getOutput();
		if (!in.endsWith(".jar") || !out.endsWith(".jar")) {
			throw new RuntimeException("Attempting to create output JAR but file name does not have .jar extension");
		}
		try {
			inputJar = new File(in);
			outputJar = new File(out);
			FileUtils.copyFile(inputJar, outputJar);
			createDirInJar("edu/cmu/sv/kelinci/instrumentor/examples");
		} catch (IOException e) {
			throw new RuntimeException("Error creating JAR file: " + out + ". File exists.");
		}
	}

	public void addFileToJar(String file, byte[] bytes) {
		try {
			
			Path tmpFile = Paths.get(tempDir.toString()+"/"+file);
			Files.createDirectories(tmpFile.getParent());
			OutputStream stream = Files.newOutputStream(tmpFile, StandardOpenOption.CREATE);
			stream.write(bytes);
			stream.close();
			String command = "jar uf " + outputJar + " -C " + tempDir.toString() + " " + file;
			Process p = Runtime.getRuntime().exec(command);
			p.waitFor();
			if (p.exitValue() == 0)
				System.out.println("File written to JAR: " + file);
			else 
				System.out.println("Error adding file to JAR: " + file);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException("Error writing file " + file + " to JAR " + outputJar);
		}
	}
	
	public void createDirInJar(String dir) {
		try {
			Path tmpFile = Paths.get(tempDir.toString()+"/"+dir);
			Files.createDirectories(tmpFile);
			Runtime.getRuntime().exec("jar uf " + outputJar + " -C " + tempDir.toString() + " " + tmpFile);
			System.out.println("Directory created in JAR: " + dir);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error creating directory " + dir + " in JAR " + outputJar);
		}
	}

	/**
	 * Extracts all class file names from a JAR file (possibly nested with more JARs).
	 * 
	 * @param file The name of the file.
	 * @param classes Class names will be stored in here.
	 */
	public static void extractJar(String file, HashSet<String> classes) {
		try {
			// open JAR file
			JarFile jarFile = new JarFile(file);
			Enumeration<JarEntry> entries = jarFile.entries();

			// iterate JAR entries
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String entryName = entry.getName();

				if (entryName.endsWith(".class")) {
					classes.add(entryName);
				} else if (entryName.endsWith(".jar")) {
					// load nested JAR
					extractJar(entryName, classes);
				}
			}

			// close JAR file
			jarFile.close();

		} catch (IOException e) {
			throw new RuntimeException("Error reading from JAR file: " + file);
		}
	}
	
	/**
	 * Singleton
	 */

	private static JarFileIO instance;

	public static void resetInstance() {
		instance = null;
	}

	public static JarFileIO v() {
		if (null == instance) {
			instance = new JarFileIO();
		}
		return instance;
	}

	private JarFileIO() {
		try {
			tempDir = Files.createTempDirectory("KelinciJar");
			createJar();
		} catch (IOException e) {
			throw new RuntimeException("Error creating temp dir");
		}
	}
}
