package edu.cmu.sv.kelinci;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 * TODO add "local" mode that prevents sending input files
 * TODO Option to replace System.exit() with RuntimeException?
 * TODO put in the cloud
 * 
 * @author rodykers
 *
 */
class Kelinci {

	public static final int PORT = 7007;
    private static final int maxQueue = 10;
    private static Queue<FuzzRequest> requestQueue = new ConcurrentLinkedQueue<>();

    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_TIMEOUT = 1;
    public static final byte STATUS_CRASH = 2;
    public static final byte STATUS_QUEUE_FULL = 3;
    public static final byte STATUS_COMM_ERROR = 4;
    public static final byte STATUS_DONE = 5;
    
    public static final byte APPLICATION_TIMEOUT = 10; // in seconds
        
    private static Method targetMain;
    private static String targetArgs[];
	
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
    	
    	try (ServerSocket ss = new ServerSocket(PORT)) {
    		System.out.println("Server listening on port " + PORT);

    		while (true) {
    			Socket s = ss.accept();
    			System.out.println("Connection established.");
    			
    			boolean status = false;
    			if (requestQueue.size() < maxQueue) {
    				status = requestQueue.offer(new FuzzRequest(s));
    				System.out.println("Request added to queue: " + status);
    			} 
    			if (!status) {
    				System.out.println("Queue full.");
    				OutputStream os = s.getOutputStream();
    				os.write(STATUS_QUEUE_FULL);
        			os.flush();
    				s.shutdownOutput();
                    s.shutdownInput();
                    s.close();
        			System.out.println("Connection closed.");
    			}
    		}
    		} catch (Exception e) {
    			System.out.println("Exception in request server");
    			e.printStackTrace();
            	System.exit(1);
    		}
    }
    
    /**
     * Writes the provided input to a file, then calls main()
     * Replaces @@ by the tmp file name.
     * 
     * @param input
     */
    private static long runApplication(byte input[]) {
		Mem.clear();
		long runtime = -1L;
		File tmpfile;
		try {
			tmpfile = File.createTempFile("kelinci-input", "");
			FileOutputStream stream = new FileOutputStream(tmpfile);
			stream.write(input);
			stream.close();
			String[] args = Arrays.copyOf(targetArgs, targetArgs.length);
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("@@")) {
					args[i] = tmpfile.getAbsolutePath();
				}
			}
			long pre = System.nanoTime();
			targetMain.invoke(null, (Object) args);
			runtime = System.nanoTime() - pre;
			tmpfile.delete();
		} catch (IOException ioe) {
			throw new RuntimeException("Error creating tmp file");
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			throw new RuntimeException("Error invoking target main method");
		}
		return runtime;
    }
    
    /**
     * Runner thread for the target application.
     *
     */
    private static class ApplicationCall implements Callable<Long> {
    	byte input[];
    	
    	ApplicationCall(byte input[]) {
    		this.input = input;
    	}
    	
        @Override
        public Long call() throws Exception {
            return runApplication(input);
        }
    }

    /**
     * Method to run in a thread handling one request from the queue at a time.
     * Kelinci starts NUM_FUZZER_THREADS of these.
     */
    private static void doFuzzerRuns() {
    	System.out.println("Fuzzer runs handler thread started.");
    	while (true) {
    		try {
    			FuzzRequest request = requestQueue.poll();
    			if (request != null) {
    				System.out.println("Handling request 1 of " + (requestQueue.size()+1));

    				InputStream is = request.clientSocket.getInputStream();
    				OutputStream os = request.clientSocket.getOutputStream();

    				// read the size of the input file (integer)
    				int filesize = is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
    				System.out.println("File size = " + filesize);

    				// read the input file
    				byte input[] = new byte[filesize];
    				int read = 0;
    				while (read < filesize) {			
    					if (is.available() > 0) {
    						input[read++] = (byte) is.read();
    					} else {
    						System.err.println("No input available from stream, strangely");
    						System.err.println("Appending a 0");
    						input[read++] = 0;
    					}
    				}

    				// run app with input
    				ExecutorService executor = Executors.newSingleThreadExecutor();
    				Future<Long> future = executor.submit(new ApplicationCall(input));

    				byte result = STATUS_CRASH;
    				try {
    					System.out.println("Started..");
    					future.get(APPLICATION_TIMEOUT, TimeUnit.SECONDS);
    					result = STATUS_SUCCESS;
    					System.out.println("Result: " + result);
    					System.out.println("Finished!");
    				} catch (TimeoutException te) {
    					future.cancel(true);
    					System.out.println("Time-out!");
    					result = STATUS_TIMEOUT;
    				} catch (Exception e) {
    					future.cancel(true);
    					if (e.getCause() instanceof RuntimeException) {
    						System.out.println("RuntimeException thrown!");
    					} else if (e.getCause() instanceof Error) {
    						System.out.println("Error thrown!");
    					} else {
    						System.out.println("Uncaught throwable!");
    					}
    					e.printStackTrace();
    				}
    				executor.shutdownNow();

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
    				System.out.println("Connection closed.");

    			} else {
    				// if no request, close your eyes for a bit
    				Thread.sleep(100);
    			}
    		} catch (SocketException se) {
    			// Connection was reset, most probably means AFL process was killed.
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
		 * grab -threads option and store command-line parameters for fuzzing runs.
		 */
		if (args.length < 1) {
			System.err.println("Usage: java edu.cmu.sv.kelinci.Kelinci [-threads N] package.ExampleMain <args>");
			return;
		}
		
		int numThreads = -1; // default
		int offset = 0;
		if (args.length > 1 && args[0].equals("-threads")) {
			numThreads = Integer.parseInt(args[1]);
			targetArgs = Arrays.copyOfRange(args, 3, args.length);
			offset += 2;
		} else {
			targetArgs = Arrays.copyOfRange(args, 1, args.length);
		}
		
		String mainClass = args[offset];
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
		 * If we need to write a JAR, put instrumented classes
		 */
		
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
         * Handle requests for fuzzer runs in multiple threads.
         * The number of threads can be specified by the user with the -threads option.
         * By default, the number of threads is equal to the number of available processors - 1.
         */
        if (numThreads <= 0)
        	numThreads = Runtime.getRuntime().availableProcessors() - 1; // -1 for server thread
        Thread fuzzerRuns[] = new Thread[numThreads];
        for (int thread = 0; thread < numThreads; thread++) {
        	fuzzerRuns[thread] = new Thread(new Runnable() {
        		@Override
        		public void run() {
        			doFuzzerRuns();
        		}
        	});
        	fuzzerRuns[thread].start();
        }
	}
}
