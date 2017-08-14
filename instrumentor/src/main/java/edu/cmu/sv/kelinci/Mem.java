package edu.cmu.sv.kelinci;

/**
 * Class to record branching, analogous to the shared memory in AFL.
 * 
 * Because we measure inside a particular target method, we need
 * a way to start/stop measuring. Therefore, the array can be cleared.
 * 
 * @author rodykers
 *
 */
public class Mem {
	
	public static final int SIZE = 65536;
	public static byte mem[] = new byte[SIZE];
	public static int prev_location = 0;
	
	/**
	 * Clears the current measurements.
	 */
	public static void clear() {
		for (int i = 0; i < SIZE; i++)
			mem[i] = 0;
	}
	
	/**
	 * Prints to stdout any cell that contains a non-zero value.
	 */
	public static void print() {
		for (int i = 0; i < SIZE; i++) {
			if (mem[i] != 0) {
				System.out.println(i + " -> " + mem[i]);
			}
		}
	}
}
