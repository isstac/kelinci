package edu.cmu.sv.kelinci.instrumentor;

import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.Label;

import edu.cmu.sv.kelinci.Mem;

import java.util.HashSet;
import java.util.Random;

/**
 * @author rodykers
 *
 * Adds AFL-like instrumentation to branches.
 * 
 * Uses the ASM MethodVisitor to instrument the start of methods,
 * the location immediately after a branch (else case), as well as
 * all labels. 
 * 
 * There are also methods in MethodVisitor that we could override
 * to instrument tableswitch, lookupswitch and try-catch. But as those
 * jump to labels in any case (including default), instrumenting the
 * labels only is enough.
 */
public class MethodTransformer extends MethodVisitor {
	
	private HashSet<Integer> ids;
	Random r;
	
	public MethodTransformer(MethodVisitor mv) {
		super(ASM5, mv);

		ids = new HashSet<>();
		r = new Random();
	}
	
	/**
	 * Best effort to generate a random id that is not already in use.
	 */
	private int getNewLocationId() {
		int id;
		int tries = 0;
		do {
			id = r.nextInt(Mem.SIZE);
			tries++;
		} while (tries <= 10 && ids.contains(id));
		ids.add(id);
		return id;
	}
	
	/**
	 * Instrument a program location, AFL style. Each location gets a 
	 * compile time random ID, hopefully unique, but maybe not.
	 * 
	 * Instrumentation is the bytecode translation of this:
	 * 
	 * 		Mem.mem[id^Mem.prev_location]++;
	 *		Mem.prev_location = id >> 1;
	 *
	 */
	private void instrumentLocation() {
		Integer id = getNewLocationId();
		mv.visitFieldInsn(GETSTATIC, "edu/cmu/sv/kelinci/Mem", "mem", "[B");
		mv.visitLdcInsn(id);
		mv.visitFieldInsn(GETSTATIC, "edu/cmu/sv/kelinci/Mem", "prev_location", "I");
		mv.visitInsn(IXOR);
		mv.visitInsn(DUP2);
		mv.visitInsn(BALOAD);
		mv.visitInsn(ICONST_1);
		mv.visitInsn(IADD);
		mv.visitInsn(I2B);
		mv.visitInsn(BASTORE);
		mv.visitIntInsn(SIPUSH, (id >> 1));
		mv.visitFieldInsn(PUTSTATIC, "edu/cmu/sv/kelinci/Mem", "prev_location", "I");
	}

	@Override
	public void visitCode() {
		mv.visitCode();
		
		/**
		 *  Add instrumentation at start of method.
		 */
		instrumentLocation();
	}
	
	@Override
	public void visitJumpInsn(int opcode, Label label) {
		mv.visitJumpInsn(opcode, label);
		
		/**
		 *  Add instrumentation after the jump.
		 *  Instrumentation for the if-branch is handled by visitLabel().
		 */
		instrumentLocation();
	}
	
	@Override
	public void visitLabel(Label label) {
		mv.visitLabel(label);
		
		/**
		 * Since there is a label, we most probably (surely?) jump to this location. Instrument.
		 */
		instrumentLocation();
	}
}
