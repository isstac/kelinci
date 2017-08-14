package edu.cmu.sv.kelinci;

/**
 * @author Rody Kersten
 */
public class AFLFeedbackInstrumented {

	// 2 paths
	public void doIf(int n) {
		instr(7631);
		if (n == 2) {
			instr(6410);
			throw new RuntimeException("Crash!");
		}
		instr(8514);
	}

	// since n <= 9, 9 paths
	public void doFor(int n) {
		instr(874);
		for (int i = 0; i < n; i++) {
			instr(1474);
			int y = 0;
		}
		instr(3261);
	}

	// 3 paths
	public int doLookupSwitch(int n) {
		instr(52);
		int z;

		// lookup switch
		switch(n) {
		case 2: instr(1345); z = 4; break;
		case 4: instr(32); z = 7; break;
		default: instr(376); z = -1;
		}
		
		return z;
	}

	// 4 paths
	public int doTableSwitch(int n) {
		instr(9746);
		int z;

		// table switch
		switch(n) {
		case 1: instr(314); z = 1; break;
		case 2: instr(731); z = 2; break;
		case 3: instr(28); z = 3; break;
		default: instr(8319); z = -1;
		}
		
		return z;
	}

	private static void instr(int id) {
		Mem.mem[id^Mem.prev_location]++;
		Mem.prev_location = id >> 1;
	}
}
