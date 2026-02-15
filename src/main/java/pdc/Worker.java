package pdc;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * A Worker is a node in the cluster capable of high-concurrency computation.
 * 
 * Responsibilities:
 * - Connect to Master via custom protocol
 * - Register with the cluster
 * - Execute received matrix computation tasks
 * - Return results to Master
 * - Respond to heartbeat probes
 */
public class Worker implements Runnable {
    private static final String STUDENT_ID = System.getenv("STUDENT_ID") != null 
        ? System.getenv("STUDENT_ID") 
        : "student-001";

    private final String workerId;
    private final String masterHost;
    private final int masterPort;
    private volatile boolean running = false;
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final Object writeLock = new Object();
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(4);
    private final BlockingQueue<Message> incomingTasks = new LinkedBlockingQueue<>();

    public Worker(String workerId, String masterHost, int masterPort) {
        this.workerId = workerId;
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    /**
     * Joins the cluster by connecting to Master and registering
     */
    public void joinCluster() throws IOException {
        System.out.println("[Worker " + workerId + "] Connecting to master at " + 
            masterHost + ":" + masterPort);

        socket = new Socket(masterHost, masterPort);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        // Send registration message (thread-safe)
        Message regMsg = new Message("REGISTER_WORKER", workerId, new byte[0]);
        synchronized (writeLock) {
            dos.write(regMsg.pack());
            dos.flush();
        }

        // Wait for acknowledgment
        Message ackMsg = Message.readFromStream(dis);
        if (ackMsg != null && ackMsg.type.equals("WORKER_ACK")) {
            System.out.println("[Worker " + workerId + "] Successfully registered with master");
            running = true;
        } else {
            throw new IOException("Failed to register with master");
        }
    }

    /**
     * Main event loop - listen for messages from Master
     */
    @Override
    public void run() {
        System.out.println("[Worker " + workerId + "] Starting listener");

        try {
            while (running) {
                try {
                    Message msg = Message.readFromStream(dis);

                    if (msg == null) {
                        // Connection closed
                        System.out.println("[Worker " + workerId + "] Connection closed by master");
                        break;
                    }

                    System.out.println("[Worker " + workerId + "] Received: " + msg.type);

                    if (msg.type.equals("RPC_REQUEST")) {
                        // Queue task for execution
                        incomingTasks.offer(msg);
                    } else if (msg.type.equals("HEARTBEAT")) {
                        // Respond to heartbeat (thread-safe)
                        Message hbAck = new Message("HEARTBEAT", workerId, new byte[0]);
                        synchronized (writeLock) {
                            dos.write(hbAck.pack());
                            dos.flush();
                        }
                    }
                } catch (EOFException e) {
                    System.out.println("[Worker " + workerId + "] Master closed connection");
                    break;
                } catch (IOException e) {
                    System.err.println("[Worker " + workerId + "] Error reading message: " + 
                        e.getMessage());
                    break;
                }
            }
        } finally {
            running = false;
            cleanup();
        }
    }

    /**
     * Process incoming tasks (runs in task executor thread)
     */
    public void executeTask(Message taskMsg) {
        try {
            String payload = new String(taskMsg.payload, StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|");

            if (parts.length < 3) {
                System.err.println("[Worker " + workerId + "] Invalid task payload");
                return;
            }

            String taskId = parts[0];
            int[][] matrixA = parseMatrix(parts[1]);
            int[][] matrixB = parseMatrix(parts[2]);

            System.out.println("[Worker " + workerId + "] Executing task: " + taskId);

            // Execute matrix multiplication
            int[][] result = multiplyMatrices(matrixA, matrixB);

            // Send result back to master (thread-safe)
            String resultPayload = taskId + "|" + matrixToString(result);
            Message resultMsg = new Message("TASK_COMPLETE", workerId,
                resultPayload.getBytes(StandardCharsets.UTF_8));

            synchronized (writeLock) {
                dos.write(resultMsg.pack());
                dos.flush();
            }

            System.out.println("[Worker " + workerId + "] Task completed: " + taskId);

        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error executing task: " + 
                e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Multiply two matrices
     */
    private int[][] multiplyMatrices(int[][] a, int[][] b) {
        int m = a.length;      // rows in A
        int n = a[0].length;   // cols in A = rows in B
        int p = b[0].length;   // cols in B

        int[][] result = new int[m][p];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < p; j++) {
                long sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += (long) a[i][k] * b[k][j];
                }
                result[i][j] = (int) sum; // Potential overflow, but matches spec
            }
        }

        return result;
    }

    /**
     * Convert matrix to string representation
     */
    private String matrixToString(int[][] matrix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                sb.append(matrix[i][j]);
                if (j < matrix[i].length - 1) sb.append(",");
            }
            if (i < matrix.length - 1) sb.append(";");
        }
        return sb.toString();
    }

    /**
     * Parse matrix from string representation
     */
    private int[][] parseMatrix(String str) {
        if (str == null || str.isEmpty()) {
            return new int[0][0];
        }

        String[] rows = str.split(";");
        int[][] matrix = new int[rows.length][];

        for (int i = 0; i < rows.length; i++) {
            String[] cols = rows[i].split(",");
            matrix[i] = new int[cols.length];
            for (int j = 0; j < cols.length; j++) {
                try {
                    matrix[i][j] = Integer.parseInt(cols[j].trim());
                } catch (NumberFormatException e) {
                    matrix[i][j] = 0;
                }
            }
        }

        return matrix;
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        taskExecutor.shutdownNow();
    }

    /**
     * Start processing tasks from the queue
     */
    private void startTaskProcessor() {
        taskExecutor.submit(() -> {
            while (running) {
                try {
                    Message task = incomingTasks.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        executeTask(task);
                    }
                } catch (InterruptedException ignored) {
                    // Continue
                }
            }
        });
    }

    /**
     * Shutdown the worker
     */
    public void shutdown() {
        running = false;
        cleanup();
    }

    /**
     * Main entry point for worker process
     */
    public static void main(String[] args) throws Exception {
        String workerId = System.getenv("WORKER_ID");
        if (workerId == null) {
            workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String masterHost = System.getenv("MASTER_HOST");
        if (masterHost == null) {
            masterHost = "localhost";
        }

        int masterPort = 9999;
        String portEnv = System.getenv("MASTER_PORT");
        if (portEnv != null) {
            try {
                masterPort = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                System.err.println("Invalid MASTER_PORT: " + portEnv);
            }
        }

        System.out.println("Starting Worker: " + workerId);

        Worker worker = new Worker(workerId, masterHost, masterPort);

        try {
            worker.joinCluster();
            worker.startTaskProcessor();
            worker.run();
        } catch (Exception e) {
            System.err.println("Worker initialization failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            worker.shutdown();
        }
    }
}
