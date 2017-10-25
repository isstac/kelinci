package edu.cmu.sv.kelinci;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 
 * @author rodykers
 *
 */
class Kelinci {

	private static final int maxQueue = 10;
	private static Queue<FuzzRequest> requestQueue = new ConcurrentLinkedQueue<>();

	public static final byte STATUS_SUCCESS = 0;
	public static final byte STATUS_TIMEOUT = 1;
	public static final byte STATUS_CRASH = 2;
	public static final byte STATUS_QUEUE_FULL = 3;
	public static final byte STATUS_COMM_ERROR = 4;
	public static final byte STATUS_DONE = 5;

	public static final long DEFAULT_TIMEOUT = 300000L; // in milliseconds
	private static long timeout;
	
	public static final int DEFAULT_VERBOSITY = 2;
	private static int verbosity;
	
	public static final int DEFAULT_PORT = 7007;
	private static int port;
	
	public static final byte DEFAULT_MODE = 0;
	public static final byte LOCAL_MODE = 1;

	private static Method targetMain;
	private static String targetArgs[];

	private static File tmpfile;

	private static class FuzzRequest {
		Socket clientSocket;

		FuzzRequest(Socket clientSocket) {
			this.clientSocket = clientSocket;
		}
	}

	/**
	 * Method to run in a thread to accept requests coming
	 * in over TCP and put them in a queue.
	 */
	private static void runServer() {

		try (ServerSocket ss = new ServerSocket(port)) {
			if (verbosity > 1)
				System.out.println("Server listening on port " + port);

			while (true) {
				Socket s = ss.accept();
				if (verbosity > 1)
					System.out.println("Connection established.");

				boolean status = false;
				if (requestQueue.size() < maxQueue) {
					status = requestQueue.offer(new FuzzRequest(s));
					if (verbosity > 1)
						System.out.println("Request added to queue: " + status);
				} 
				if (!status) {
					if (verbosity > 1)
						System.out.println("Queue full.");
					OutputStream os = s.getOutputStream();
					os.write(STATUS_QUEUE_FULL);
					os.flush();
					s.shutdownOutput();
					s.shutdownInput();
					s.close();
					if (verbosity > 1)
						System.out.println("Connection closed.");
				}
			}
		} catch (BindException be) {
			System.err.println("Unable to bind to port " + port);
			System.exit(1);
		} catch (Exception e) {
			System.err.println("Exception in request server");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Calls main() with the provided file name,replaces @@ by the file name.
	 */
	private static long runApplication(String filename) {
		long runtime = -1L;

		String[] args = Arrays.copyOf(targetArgs, targetArgs.length);
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("@@")) {
				args[i] = filename;
			}
		}
		long pre = System.nanoTime();
		try {
			targetMain.invoke(null, (Object) args);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			throw new RuntimeException("Error invoking target main method");
		}
		runtime = System.nanoTime() - pre;
		return runtime;
	}

	/**
	 * Writes the provided input to a file, then calls main().
	 * Replaces @@ by the tmp file name.
	 * 
	 * @param input The file contents as a byte array.
	 */
	private static long runApplication(byte input[]) {
		try (FileOutputStream stream = new FileOutputStream(tmpfile)) {			
			stream.write(input);
			stream.close();
			return runApplication(tmpfile.getAbsolutePath());
		} catch (IOException ioe) {
			throw new RuntimeException("Error writing to tmp file");
		}
	}

	/**
	 * Runner thread for the target application.
	 *
	 */
	private static class ApplicationCall implements Callable<Long> {
		byte input[];
		String path;

		ApplicationCall(byte input[]) {
			this.input = input;
		}
		
		ApplicationCall(String path) {
			this.path = path;
		}

		@Override
		public Long call() throws Exception {
			if (path != null)
				return runApplication(path);
			return runApplication(input);
		}
	}

	/**
	 * Method to run in a thread handling one request from the queue at a time.
	 * 
	 * LOCAL_MODE means you only send over a path to the input file.
	 * DEFAULT_MODE means the actual bytes of the file are sent.
	 */
	private static void doFuzzerRuns() {
		if (verbosity > 1) 
			System.out.println("Fuzzer runs handler thread started.");
		
		while (true) {
			try {
				FuzzRequest request = requestQueue.poll();
				if (request != null) {
					if (verbosity > 1)
						System.out.println("Handling request 1 of " + (requestQueue.size()+1));

					InputStream is = request.clientSocket.getInputStream();
					OutputStream os = request.clientSocket.getOutputStream();

					Mem.clear();
					byte result = STATUS_CRASH;
					ApplicationCall appCall = null;

					// read the mode (local or default)
					byte mode = (byte) is.read();
					
					/* LOCAL MODE */
					if (mode == LOCAL_MODE) {
						if (verbosity > 1) 
							System.out.println("Handling request in LOCAL MODE.");
						
						// read the length of the path (integer)
						int pathlen = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
						if (verbosity > 2)
							System.out.println("Path len = " + pathlen);
						
						if (pathlen < 0) {
							if (verbosity > 1)
								System.err.println("Failed to read path length");
							result = STATUS_COMM_ERROR;
						} else {
						
							// read the path
							byte input[] = new byte[pathlen];
							int read = 0;
							while (read < pathlen) {			
								if (is.available() > 0) {
									input[read++] = (byte) is.read();
								} else {
									if (verbosity > 1) {
										System.err.println("No input available from stream, strangely, breaking.");
										result = STATUS_COMM_ERROR;
										break;
									}
								}
							}
							String path = new String(input);
							if (verbosity > 1)
								System.out.println("Received path: " + path);
							
							appCall = new ApplicationCall(path);
						}
						
					/* DEFAULT MODE */
					} else {
						if (verbosity > 1) 
							System.out.println("Handling request in DEFAULT MODE.");
						
						// read the size of the input file (integer)
						int filesize = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
						if (verbosity > 2)
							System.out.println("File size = " + filesize);

						if (filesize < 0) {
							if (verbosity > 1)
								System.err.println("Failed to read file size");
							result = STATUS_COMM_ERROR;
						} else {

							// read the input file
							byte input[] = new byte[filesize];
							int read = 0;
							while (read < filesize) {			
								if (is.available() > 0) {
									input[read++] = (byte) is.read();
								} else {
									if (verbosity > 1) {
										System.err.println("No input available from stream, strangely");
										System.err.println("Appending a 0");
									}
									input[read++] = 0;
								}
							}
							
							appCall = new ApplicationCall(input);
						}
					}
					
					if (result != STATUS_COMM_ERROR && appCall != null) {

						// run app with input
						ExecutorService executor = Executors.newSingleThreadExecutor();
						Future<Long> future = executor.submit(appCall);

						try {
							if (verbosity > 1)
								System.out.println("Started...");
							future.get(timeout, TimeUnit.MILLISECONDS);
							result = STATUS_SUCCESS;
							if (verbosity > 1)
								System.out.println("Finished!");
						} catch (TimeoutException te) {
							future.cancel(true);
							if (verbosity > 1) 
								System.out.println("Time-out!");
							result = STATUS_TIMEOUT;
						} catch (Throwable e) {
							future.cancel(true);
							if (e.getCause() instanceof RuntimeException) {
								if (verbosity > 1) 
									System.out.println("RuntimeException thrown!");
							} else if (e.getCause() instanceof Error) {
								if (verbosity > 1) 
									System.out.println("Error thrown!");
							} else {
								if (verbosity > 1) 
									System.out.println("Uncaught throwable!");
							}
							e.printStackTrace();
						}
						executor.shutdownNow();
					}
					
					if (verbosity > 1)
						System.out.println("Result: " + result);
					
					if (verbosity > 2)
						Mem.print();

					// send back status
					os.write(result);

					// send back "shared memory" over TCP
					os.write(Mem.mem, 0, Mem.mem.length);

					// close connection
					os.flush();
					request.clientSocket.shutdownOutput();
					request.clientSocket.shutdownInput();
					request.clientSocket.setSoLinger(true, 100000);
					request.clientSocket.close();
					if (verbosity > 1) 
						System.out.println("Connection closed.");

				} else {
					// if no request, close your eyes for a bit
					Thread.sleep(100);
				}
			} catch (SocketException se) {
				// Connection was reset, most probably means AFL process was killed.
				if (verbosity > 1) 
					System.out.println("Connection reset.");
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Exception running fuzzed input");
			}
		}
	}

	public static void main(String args[]) {

		/**
		 * Parse command line parameters: load the main class,
		 * grab -port option and store command-line parameters for fuzzing runs.
		 */
		if (args.length < 1) {
			System.err.println("Usage: java edu.cmu.sv.kelinci.Kelinci [-v N] [-p N] [-t N] package.ExampleMain <args>");
			return;
		}

		port = DEFAULT_PORT;
		timeout = DEFAULT_TIMEOUT;
		verbosity = DEFAULT_VERBOSITY;
		
		int curArg = 0;
		while (args.length > curArg) {
			if (args[curArg].equals("-p") || args[curArg].equals("-port")) {
				port = Integer.parseInt(args[curArg+1]);
				curArg += 2;
			} else if (args[curArg].equals("-v") || args[curArg].equals("-verbosity")) {
				verbosity = Integer.parseInt(args[curArg+1]);
				curArg += 2;
			} else if (args[curArg].equals("-t") || args[curArg].equals("-timeout")) {
				timeout = Long.parseLong(args[curArg+1]);
				curArg += 2;
			} else {
				break;
			}
		}
		String mainClass = args[curArg];
		targetArgs = Arrays.copyOfRange(args, curArg+1, args.length);
		
		/**
		 * Check if at least one of the target parameters is @@
		 */
		boolean present = false;
		for (int i = 0; i < targetArgs.length; i++) {
			if (targetArgs[i].equals("@@")) {
				present = true;
				break;
			}
		}
		if (!present) {
			System.err.println("Error: none of the target application parameters is @@");
			System.exit(1);
		}
		
		/**
		 * Redirect target program output to /dev/null if requested.
		 */
		if (verbosity <= 0) {
			PrintStream nullStream = new PrintStream(new NullOutputStream());
			System.setOut(nullStream);
			System.setErr(nullStream);
		}

		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		try {
			Class<?> target = classloader.loadClass(mainClass);
			targetMain = target.getMethod("main", String[].class);
		} catch (ClassNotFoundException e) {
			System.err.println("Main class not found: " + mainClass);
			return;
		} catch (NoSuchMethodException e) {
			System.err.println("No main method found in class: " + mainClass);
			return;
		} catch (SecurityException e) {
			System.err.println("Main method in class not accessible: " + mainClass);
			return;
		}

		/**
		 * Create the tmp file to serve as input file to the program.
		 */
		try {
			tmpfile = File.createTempFile("kelinci-input", "");
			tmpfile.deleteOnExit();
		} catch (IOException ioe) {
			throw new RuntimeException("Error creating tmp file");
		}

		/**
		 * Start the server thread
		 */
		Thread server = new Thread(new Runnable() {
			@Override
			public void run() {
				runServer();
			}
		});
		server.start();

		/**
		 * Handle requests for fuzzer runs in separate thread.
		 */
		Thread fuzzerRuns = new Thread(new Runnable() {
			@Override
			public void run() {
				doFuzzerRuns();
			}
		});
		fuzzerRuns.start();
	}
	
	/**
	 * Stream to /dev/null. Used to redirect output of target program.
	 * 
	 * I know something like this is also in Apache Commons IO, but if I include it here, 
	 * we don't need any libs on the classpath when running the Kelinci server.
	 * 
	 * @author rodykers
	 *
	 */
	private static class NullOutputStream extends ByteArrayOutputStream {

	    @Override
	    public void write(int b) {}

	    @Override
	    public void write(byte[] b, int off, int len) {}

	    @Override
	    public void writeTo(OutputStream out) throws IOException {}
	  }
}
