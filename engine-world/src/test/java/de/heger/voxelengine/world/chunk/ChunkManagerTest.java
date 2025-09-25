package de.heger.voxelengine.world.chunk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import java.util.List; // Import List
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChunkManager}.
 */
class ChunkManagerTest {
    private ChunkManager manager;

    @BeforeEach
    void setUp() {
        manager = ChunkManager.getInstance();
        // Clear any existing chunks by removing known positions from previous tests
        // This ensures test isolation for the singleton
        // Create a copy to avoid ConcurrentModificationException if getAllLoadedChunks returns a live view
        Collection<Chunk> loadedChunks = List.copyOf(manager.getAllLoadedChunks());
        for (Chunk chunk : loadedChunks) {
            if (chunk != null && chunk.getPosition() != null) {
                 manager.removeChunk(chunk.getPosition());
            }
        }
         // Double check it's empty
        assertEquals(0, manager.getLoadedChunkCount(), "ChunkManager should be empty before test execution.");
    }

    @Test
    void testAddGetRemoveContains() {
        ChunkPos pos = new ChunkPos(1, 2, 3);
        Chunk chunk = new Chunk(pos);

        assertFalse(manager.containsChunk(pos));
        assertNull(manager.getChunk(pos));
        assertEquals(0, manager.getLoadedChunkCount()); // Start empty

        manager.addChunk(chunk);
        assertTrue(manager.containsChunk(pos));
        assertSame(chunk, manager.getChunk(pos));
        assertEquals(1, manager.getLoadedChunkCount());

        Collection<Chunk> all = manager.getAllLoadedChunks();
        assertEquals(1, all.size());
        assertTrue(all.contains(chunk));

        manager.removeChunk(pos);
        assertFalse(manager.containsChunk(pos));
        assertNull(manager.getChunk(pos));
        assertEquals(0, manager.getLoadedChunkCount());
    }


    @Test
    void testConcurrentAccess() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        int opsPerThread = 100;
        // Use a thread factory for better naming/debugging if needed
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1); // To start all threads simultaneously
        CountDownLatch endLatch = new CountDownLatch(threadCount);   // To wait for all threads to finish

        Future<?>[] futures = new Future<?>[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t; // Effectively final for lambda
            Callable<Void> task = () -> {
                startLatch.await(); // Wait for the signal to start
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        // Generate a unique position for this thread and iteration
                        ChunkPos pos = new ChunkPos(threadId, i, i);
                        Chunk chunk = new Chunk(pos);

                        // Add the chunk
                        manager.addChunk(chunk);
                        assertTrue(manager.containsChunk(pos),
                                   "Thread " + threadId + " failed: Chunk should exist after adding: " + pos);

                        // Optional: Simulate some work or delay to increase contention
                        // Thread.sleep(ThreadLocalRandom.current().nextInt(1, 3));

                        // Remove the chunk
                        manager.removeChunk(pos);
                        assertFalse(manager.containsChunk(pos),
                                    "Thread " + threadId + " failed: Chunk should not exist after removing: " + pos);
                    }
                } catch (Exception e) {
                    // Log exception to help diagnose failures during execution
                    System.err.println("Exception in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                    throw e; // Re-throw to ensure the future fails
                } finally {
                    endLatch.countDown(); // Signal that this thread has completed
                }
                return null;
            };
            futures[t] = exec.submit(task);
        }

        startLatch.countDown(); // Signal all threads to start execution
        boolean finishedInTime = endLatch.await(30, TimeUnit.SECONDS); // Wait for all threads to complete

        assertTrue(finishedInTime, "Test timed out waiting for threads to finish.");

        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS), "Executor service did not terminate cleanly.");

        // Verify that all futures completed without throwing exceptions
        for (int i = 0; i < threadCount; i++) {
            try {
                // Check for exceptions thrown by the tasks
                futures[i].get(1, TimeUnit.MILLISECONDS); // Short timeout, they should already be done
            } catch (TimeoutException e) {
                fail("Future " + i + " timed out unexpectedly after completion.");
            } catch (ExecutionException e) {
                fail("Future " + i + " threw an exception during execution: " + e.getCause());
            }
        }

        // Final check: After all concurrent add/remove operations, the manager should be empty
        assertEquals(0, manager.getLoadedChunkCount(),
                     "Chunk manager should be empty after all concurrent operations.");
    }
}
