package edu.cmu.sv.kelinci.instrumentor.examples;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import edu.cmu.sv.kelinci.Mem;

/**
 * Just a program with some branches, so lots of different "behaviors".
 * 
 * @author Rody Kersten
 */
public class AFLFeedback {

	// 2 paths
	public void doIf(int n) {
		if (n == 2) {
			throw new RuntimeException("Crash!");
		}
	}

	// since n <= 9, 10 paths
	public void doFor(int n) {
		for (int i = 0; i < n; i++) {
			int y = 0;
		}
	}

	// 3 paths
	public int doLookupSwitch(int n) {
		int z;

		// lookup switch
		switch(n) {
		case 2:  z = 4; break;
		case 4: z = 7; break;
		default: z = -1;
		}
		
		return z;
	}

	// 4 paths
	public int doTableSwitch(int n) {
		int z;

		// table switch
		switch(n) {
		case 1: z = 1; break;
		case 2: z = 2; break;
		case 3: z = 3; break;
		default: z = -1;
		}
		
		return z;
	}
	
	public static void main(String args[]) {

		AFLFeedback x = new AFLFeedback();
		try (FileInputStream stream = new FileInputStream(args[0])) {
			x.doIf(stream.read()-'0');
			x.doFor(stream.read()-'0');
			x.doLookupSwitch(stream.read()-'0');
			x.doTableSwitch(stream.read()-'0');
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Main done.");
		Mem.print();
	}
}
