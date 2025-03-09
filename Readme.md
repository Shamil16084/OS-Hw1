# Readers-Writers Assignment with Load Balancing

## Overview
This project is a Java implementation of the readers-writers problem that balances load across three file replicas. The program demonstrates:
- **Concurrent Reading:** Multiple reader threads can access the replicas at the same time (when no writer is active).
- **Writer Priority:** When a writer is waiting or writing, no new reader can start.
- **Load Balancing:** Readers choose the replica with the fewest active readers (if tied, one is selected randomly), ensuring even distribution.
- **Logging:** All read and write operations, along with system status (active readers per replica and writer state), are logged to `log.txt`.

## Files
- **ReadersWritersAssignment.java:**  
  The main Java source file that contains the entire implementation.
- **replica0.txt, replica1.txt, replica2.txt:**  
  These files represent the three file replicas. They are created/updated during the program execution.
- **log.txt:**  
  Log file where all events (reader and writer operations) are recorded.

## Prerequisites
- **Java Development Kit (JDK) 8 or higher:**  
  Ensure the JDK is installed and the `javac` and `java` commands are available in your system's PATH.
- A command line terminal or an IDE that supports Java.

## How to Compile and Run

### Compile the Program
1. Open a terminal or command prompt.
2. Navigate to the directory containing the `ReadersWritersAssignment.java` file.
3. Compile the program using the command:
   ```bash
   javac ReadersWritersAssignment.java
   ```
   This will compile the code and generate the corresponding `.class` files.

### Run the Program
1. In the same directory, run the program using:
   ```bash
   java ReadersWritersAssignment
   ```
2. The program will:
   - Create (or update) the three replica files (`replica0.txt`, `replica1.txt`, and `replica2.txt`).
   - Run a writer thread that updates all three files.
   - Spawn reader threads for a fixed duration (e.g., 15 seconds), with each reader load-balanced across the replicas.
   - Log every operation in `log.txt`.

3. After execution, open the `log.txt` file to view detailed logs of all reader and writer operations.

## Program Behavior
- **Reader Threads:**  
  - Spawn at random intervals.
  - Choose the replica with the fewest active readers.
  - Read from the selected file concurrently with other readers.
  
- **Writer Thread:**  
  - Periodically updates all three replicas simultaneously.
  - Blocks readers while performing write operations.
  
- **Logging:**  
  - Every read/write operation logs details including:
    - Which replica was accessed.
    - The content read or written.
    - Current status of active readers per replica and writer activity.

## Customization
- **Simulation Parameters:**  
  - Modify `MAX_WRITES` in the code to change the number of writes.
  - Adjust sleep durations to simulate different read/write times.
  - Change the simulation period for spawning readers if desired.
  

## Contact
For any questions or issues, please contact Shamil at sabbasov16084@ada.edu.az
```
Written and implemented by Shamil Abbasov, 09.03.2025