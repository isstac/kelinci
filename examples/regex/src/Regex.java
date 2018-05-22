import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import regexjdk8.*;

/**
 * Loads a file and a regex and matches the regex to the file.
 *
 * @author Rody Kersten.
 */
public class Regex {

    static String regex = null;
    static String text = null;

    private static void readRegex(String file) {
        try {
            regex = new String(Files.readAllBytes(Paths.get(file)));

	    /* Remove whitespace at beginning and end */
	    regex = regex.trim();
	    
	    /* Limit to 10 characters */
	    if (!file.startsWith("resources") && regex.length() > 10)
                regex = regex.substring(0, 10);

	} catch (IOException e) {
            System.err.println("Error reading regex file: " + file);
	}
    }

    private static void readText(String file) {
        try {
            text = new String(Files.readAllBytes(Paths.get(file)));

	    /* Limit to 100 characters */
	    if (!file.startsWith("resources") && text.length() > 10)
                text = text.substring(0, 100);
	} catch (IOException e) {
            System.err.println("Error reading text file: "+ file);
	}
    }


    public static void main(String[] args) {
	if (args.length != 2) {
		System.out.println("Usage: Regex <regexfile> <textfile>");
		return;
	}

	readRegex(args[0]);
	readText(args[1]);
	if (regex == null || text == null)
            return;

	//System.out.println("Read text file: " + text);
	System.out.println("Read regex: " + regex);

	/* now perform the matching */
	try {
	    boolean match = Pattern.matches(regex, text);
	    System.out.println("Matches: " + match);
	} catch (PatternSyntaxException e) {
            System.err.println("Invalid pattern: " + regex);
	}
    }
}
