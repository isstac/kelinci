package edu.cmu.sv.kelinci.instrumentor;

import edu.cmu.sv.kelinci.Mem;

/**
 * @author rodykers
 * 
 * Class to run the ASMifier on to generate ASM code for the instrumentation trampoline.
 * 
 * To run the ASMifier, use the following command in the 'instrumenter' dir (edit ASM location if needed):
 * java -cp /usr/share/java/asm.jar:/usr/share/java/asm-util.jar:build/classes/main/ \
 *   org.objectweb.asm.util.ASMifier edu.cmu.sv.kelinci.instrumentor.AnnotationTest
 *
 */
public class AnnotationTest {
	
	/**
	 * Note that the generated ASM code will provide guidance,
	 * but cannot be directly copy-pasted, as the 'id' will be a 
	 * compile time random that cannot be represented here.
	 */
	public static void trampoline(int id) {
		Mem.mem[id^Mem.prev_location]++;
		Mem.prev_location = id >> 1;
		Mem.instrCost++;
	}
}
