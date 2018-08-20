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
    
    public static final int LONG_LENGTH = 8;    // storage size in bytes of a longword

    public static final int BUFFER_SIZE = 65536; // the measurement data block
    public static final int PARAM_SIZE  = (4 * LONG_LENGTH); // 4 params
    public static final int PARAM_OFFSET = BUFFER_SIZE;

    public static byte mem[] = new byte[BUFFER_SIZE+32];
    public static int prev_location = 0;
    
    /**
     * Holds the current cost measured by the configured instrumentation mode, e.g., by counting every bytecode
     * jump instruction (see {@link edu.cmu.sv.kelinci.instrumentor.Options.InstrumentationMode}).
     */
    public static long instrCost = 0;
    
    /**
     * Clears the current measurements.
     */
    public static void clear() {
        for (int i = 0; i < BUFFER_SIZE + PARAM_SIZE; i++)
            mem[i] = 0;
        instrCost = 0L;
    }

    /**
     * appends a long word value to the end of the data block
     */
    public static void appendLong(int offset, long value) {
        if (offset < 0 || offset > 3) {
            return;
        }
        offset = PARAM_OFFSET + (offset * LONG_LENGTH);
        for (int i = 0; i < LONG_LENGTH; i++) {
            mem[offset + i] = (byte) ((value >> i * LONG_LENGTH) & 255);
        }
    }

    // check if a line has any non-zero entries in it
    private static boolean testline(int offset) {
        for(int ix = 0; ix < 16; ix++) {
            if (mem[offset+ix] != 0) {
                return true;
            }
        }
        return false;
    }

    // print a line of the measurement data as a hex string
    private static void printline(int offset) {
        System.out.print(String.format("%08X", offset) + ": ");

        for(int ix = 0; ix < 16; ix++) {
            System.out.print(String.format("%02X", mem[offset+ix]));
        }
        System.out.println("");
    }

    // print one of the 4 long word entries that is stored at the end of the data block
    private static void printlong(int select) {
        String name = "runtime ";
        switch (select) {
            case 1: name = "max_heap";  break;
            case 2: name = "instr   ";  break;
            case 3: name = "user    ";  break;
            default:
                select = 0;
                break;
        }

        // adjust array offset for leading 65k block preceeding it
        int offset = PARAM_OFFSET + (select * LONG_LENGTH);

        System.out.print(name + ": ");
        for(int ix = 0; ix < LONG_LENGTH; ix++) {
            System.out.print(String.format("%02X", mem[offset+ix]));
        }
        System.out.println("");
    }

    public static void printtest() {
        int count = 0;
        // print the 1st 20 rows of entries that contain non-zero values
        for(int offset = 0; offset < BUFFER_SIZE && count < 20; offset += 16) {
            if (testline(offset)) {
                printline(offset);
                count++;
            }
        }
        printlong(0);
        printlong(1);
        printlong(2);
        printlong(3);
    }

    /**
     * Prints to stdout any cell that contains a non-zero value.
     */
    public static void print() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (mem[i] != 0) {
                System.out.println(i + " -> " + mem[i]);
            }
        }
    }
}
