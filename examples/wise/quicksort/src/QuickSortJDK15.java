import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Rody Kersten
 **/
public class QuickSortJDK15 {

    public static void main(String[] args) {
	if (args.length != 1) {
		System.out.println("Expects file name as parameter");
		return;
	}


	int N = 64;
        int a[] = new int[N];

	try (FileInputStream fis = new FileInputStream(args[0])) {
		int b;
		int i = 0;
		while (((b = fis.read()) != -1) && (i < N) )  {
			a[i] = b;
			i++;
		}
	} catch (IOException e) {
		System.err.println("Error reading input");
		e.printStackTrace();
		return;
	}

	System.out.println("Read int array: " + java.util.Arrays.toString(a));
        Arrays.sort(a);
	System.out.println("Sorted: " + java.util.Arrays.toString(a));
    }

}
