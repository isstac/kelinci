package edu.cmu.sv.kelinci.instrumentor;

import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.kohsuke.args4j.Option;

/**
 * Options for the Kelinci Instrumentor.
 * Currently -i and -o flags to specify input and output, respectively,
 * and -threads to specify the number of runner threads.
 * 
 * @author rodykers
 *
 */
public class Options {

	@Option(name = "-i", usage = "Specify input file/dir", required = true)
	private String input;
	private HashSet<String> inputClasses;
	
	public String getRawInput() {
		return input;
	}
	
	public HashSet<String> getInput() {
		if (inputClasses == null) {
			inputClasses = new HashSet<>();
			if (input.endsWith(".class")) {
				// single class file, has to be a relative path from a directory on the class path
				inputClasses.add(input);
			} else if (input.endsWith(".jar")) {
				// JAR file
				JarFileIO.extractJar(input, inputClasses);
				addToClassPath(input);
			} else {
				// directory
				System.out.println("Loading dir: " + input);
				loadDirectory(input, inputClasses);
				addToClassPath(input);
			}
		}
		return inputClasses;
	}
	
	/*
	 * Add an element to the class path. Can be either a directory or a JAR.
	 */
	private static void addToClassPath(String url) {
		try {
			File file = new File(url);
			Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
			method.setAccessible(true);
		    method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{file.toURI().toURL()});
		} catch (Exception e) {
			throw new RuntimeException("Error adding location to class path: " + url);
		}
	    
	}

	private void loadDirectory(String input, HashSet<String> inputClasses) {
		final int dirprefix;
		if (input.endsWith("/"))
			dirprefix = input.length();
		else
			dirprefix = input.length()+1;
		
		try {
			Files.walk(Paths.get(input)).filter(Files::isRegularFile).forEach(filePath -> {
				String name = filePath.toString();
				System.out.println("Found file " + name);
				if (name.endsWith(".class")) {
					inputClasses.add(name.substring(dirprefix));
				}

			});
		} catch (IOException e) {
			throw new RuntimeException("Error reading from directory: " + input);
		}
	}
	
	@Option(name = "-o", usage = "Specificy output file/dir", required = true)
	private String output;
	
	public String getOutput() {
		return output;
	}
	
	public boolean outputJar() {
		boolean outjar = output.endsWith(".jar");
		if (outjar && !input.endsWith(".jar"))
			throw new RuntimeException("Cannot output JAR if the input is not a JAR");
		return output.endsWith(".jar");
	}
	
	
	/**
	 * Singleton
	 */

	private static Options options;

	public static void resetInstance() {
		options = null;
	}

	public static Options v() {
		if (null == options) {
			options = new Options();
		}
		return options;
	}

	private Options() {
	}

}

