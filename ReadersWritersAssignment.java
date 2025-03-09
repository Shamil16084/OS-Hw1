import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ReadersWritersAssignment {

    // FileReplica represents one file replica on disk.
    public static class FileReplica {
        private final String filePath;
        private final int replicaId;

        public FileReplica(int id, String filePath, String initialContent) {
            this.replicaId = id;
            this.filePath = filePath;
            // Write initial content to the file.
            try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
                pw.println(initialContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Reads the entire content of the file.
        // The "synchronized" is not used  so that multiple readers
        // can read concurrently from the same file.
        public String read() {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return sb.toString().trim();
        }

        // Writes new content to the file (overwriting previous content).
        //The higher-level lock prevents concurrent access,  this method remains unsynchronized.
        public void write(String newContent) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
                pw.println(newContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public int getReplicaId() {
            return replicaId;
        }

        public String getFilePath() {
            return filePath;
        }
    }

    // Logger writes log messages to a file ("log.txt").
    public static class Logger {
        private static PrintWriter writer;
        static {
            try {
                writer = new PrintWriter(new FileWriter("log.txt", true), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public synchronized static void log(String message) {
            writer.println(message);
        }
    }

    // ReadersWritersLock implements writer-priority and load balancing across three replicas.
    public static class ReadersWritersLock {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition okToRead = lock.newCondition();
        private final Condition okToWrite = lock.newCondition();
        private int waitingWriters = 0;
        private boolean writerActive = false;
        // Track number of readers per replica.
        private int[] replicaReaders = new int[3];
        private final Random random = new Random();

        // Called by a reader to start reading.
        // Returns the index of the chosen replica.
        public int acquireRead() {
            lock.lock();
            try {
                // Wait if a writer is active or waiting.
                while (writerActive || waitingWriters > 0) {
                    okToRead.await();
                }
                // Determine the minimum number of readers among the replicas.
                int min = replicaReaders[0];
                for (int i = 1; i < replicaReaders.length; i++) {
                    if (replicaReaders[i] < min) {
                        min = replicaReaders[i];
                    }
                }
                // Collect all replica indices with the minimum count.
                List<Integer> candidates = new ArrayList<>();
                for (int i = 0; i < replicaReaders.length; i++) {
                    if (replicaReaders[i] == min) {
                        candidates.add(i);
                    }
                }
                // Randomly select one of the candidates.
                int chosenReplica = candidates.get(random.nextInt(candidates.size()));
                replicaReaders[chosenReplica]++;
                return chosenReplica;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            } finally {
                lock.unlock();
            }
        }

        // Called by a reader after finishing reading.
        public void releaseRead(int replica) {
            lock.lock();
            try {
                replicaReaders[replica]--;
                if (getTotalReaders() == 0) {
                    okToWrite.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        // Called by the writer to obtain exclusive access.
        public void acquireWrite() {
            lock.lock();
            try {
                waitingWriters++;
                while (getTotalReaders() > 0 || writerActive) {
                    okToWrite.await();
                }
                waitingWriters--;
                writerActive = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }

        // Called by the writer after finishing writing.
        public void releaseWrite() {
            lock.lock();
            try {
                writerActive = false;
                okToRead.signalAll();
                okToWrite.signal();
            } finally {
                lock.unlock();
            }
        }

        // Returns the total number of readers currently reading.
        public int getTotalReaders() {
            int sum = 0;
            for (int count : replicaReaders) {
                sum += count;
            }
            return sum;
        }

        public int[] getReplicaReaders() {
            lock.lock();
            try {
                return replicaReaders.clone();
            } finally {
                lock.unlock();
            }
        }

        // Returns true if a writer is active.
        public boolean isWriterActive() {
            lock.lock();
            try {
                return writerActive;
            } finally {
                lock.unlock();
            }
        }
    }

    // Reader thread that reads from one of the replicas.
    public static class Reader implements Runnable {
        private final int readerId;
        private final ReadersWritersLock rwLock;
        private final FileReplica[] replicas;

        public Reader(int readerId, ReadersWritersLock rwLock, FileReplica[] replicas) {
            this.readerId = readerId;
            this.rwLock = rwLock;
            this.replicas = replicas;
        }

        @Override
        public void run() {
            int chosenReplica = rwLock.acquireRead();
            if (chosenReplica == -1)
                return;
            Logger.log("Reader " + readerId + " started reading from Replica " +
                       chosenReplica + " (" + replicas[chosenReplica].getFilePath() + ") " + getStatus());
            // Read content from the chosen file.
            String content = replicas[chosenReplica].read();
            try {
                // Simulate a read duration. During this time, other readers might also be reading since we are not using syncronized.
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Logger.log("Reader " + readerId + " finished reading from Replica " +
                       chosenReplica + " (" + replicas[chosenReplica].getFilePath() + ") with content: \"" +
                       content + "\" " + getStatus());
            rwLock.releaseRead(chosenReplica);
        }

        private String getStatus() {
            int[] readers = rwLock.getReplicaReaders();
            boolean writerActive = rwLock.isWriterActive();
            return "Status: Readers per replica " + Arrays.toString(readers) + ", Writer active: " + writerActive;
        }
    }

    // Writer thread that updates all three file replicas simultaneously.
    public static class Writer implements Runnable {
        private final ReadersWritersLock rwLock;
        private final FileReplica[] replicas;
        private int writeCount = 0;
        private final int maxWrites;

        public Writer(ReadersWritersLock rwLock, FileReplica[] replicas, int maxWrites) {
            this.rwLock = rwLock;
            this.replicas = replicas;
            this.maxWrites = maxWrites;
        }

        @Override
        public void run() {
            while (writeCount < maxWrites) {
                try {
                    // Sleep for a random duration before writing.
                    Thread.sleep((long) (Math.random() * 1000 + 500));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                rwLock.acquireWrite();
                Logger.log("Writer started writing " + getStatus());
                writeCount++;
                String newContent = "Write #" + writeCount + " at " + System.currentTimeMillis();
                // Update all three replicas (files) with the new content.
                for (FileReplica replica : replicas) {
                    replica.write(newContent);
                }
                try {
                    Thread.sleep(200); // Simulate write duration.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Logger.log("Writer finished writing " + getStatus() + " with new content: \"" +
                           newContent + "\"");
                rwLock.releaseWrite();
            }
            Logger.log("Writer completed " + maxWrites + " writes. Terminating writer thread.");
        }

        private String getStatus() {
            int[] readers = rwLock.getReplicaReaders();
            boolean writerActive = rwLock.isWriterActive();
            return "Status: Readers per replica " + Arrays.toString(readers) + ", Writer active: " + writerActive;
        }
    }

    public static void main(String[] args) {
        ReadersWritersLock rwLock = new ReadersWritersLock();

        // Create three file replicas on disk.
        FileReplica[] replicas = new FileReplica[3];
        for (int i = 0; i < replicas.length; i++) {
            String filePath = "replica" + i + ".txt";
            replicas[i] = new FileReplica(i, filePath, "Initial content in replica " + i);
        }

        final int MAX_WRITES = 10; // Limit writer iterations. I used it in order to terminate program, otherwise it can go endless
        Thread writerThread = new Thread(new Writer(rwLock, replicas, MAX_WRITES));
        writerThread.start();

        // Spawn reader threads for a fixed simulation time (e.g 15 seconds).It can also be modified and can go indefinetly.
        long simulationTime = 15000; // 15 seconds.
        long startTime = System.currentTimeMillis();
        int readerId = 0;
        List<Thread> readerThreads = new ArrayList<>();

        while (System.currentTimeMillis() - startTime < simulationTime) {
            try {
                Thread.sleep((long) (Math.random() * 300 + 200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Thread readerThread = new Thread(new Reader(readerId++, rwLock, replicas));
            readerThreads.add(readerThread);
            readerThread.start();
        }

        // Wait for the writer to finish.
        try {
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Wait for all reader threads to complete.
        for (Thread t : readerThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Logger.log("Simulation finished.");
    }
}
