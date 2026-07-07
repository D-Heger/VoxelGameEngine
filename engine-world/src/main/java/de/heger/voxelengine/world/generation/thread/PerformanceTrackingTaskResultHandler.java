package de.heger.voxelengine.world.generation.thread;

import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.generation.tasks.ChunkGenerationTask;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A {@link TaskResultHandler} that measures how long chunk generation takes.
 *
 * <p>It keeps a rolling window of the most recent generation times so the
 * engine can report an average (for the debug overlay and tuning). It can wrap
 * another handler, so timing can be layered on top of, for example, logging
 * without either concern knowing about the other (a decorator pattern).</p>
 */
public class PerformanceTrackingTaskResultHandler implements TaskResultHandler {

    private static final int MAX_SAMPLES = 50; // Store last 50 samples for averaging
    private final Deque<Long> recentGenerationTimes = new ConcurrentLinkedDeque<>();
    private final TaskResultHandler wrappedHandler; // Optional: to chain functionality

    public PerformanceTrackingTaskResultHandler(TaskResultHandler wrappedHandler) {
        this.wrappedHandler = wrappedHandler;
    }

    public PerformanceTrackingTaskResultHandler() {
        this.wrappedHandler = null;
    }

    @Override
    public void onTaskCompleted(ChunkGenerationTask task, Chunk chunk, long durationMillis) {
        recentGenerationTimes.addLast(durationMillis);
        if (recentGenerationTimes.size() > MAX_SAMPLES) {
            recentGenerationTimes.pollFirst();
        }

        if (wrappedHandler != null) {
            wrappedHandler.onTaskCompleted(task, chunk, durationMillis);
        }
    }

    @Override
    public void onTaskFailed(ChunkGenerationTask task, Exception e) {
        // Optionally track failure rates or times if needed in the future
        if (wrappedHandler != null) {
            wrappedHandler.onTaskFailed(task, e);
        }
    }

    public double getAverageGenerationTimeMillis() {
        if (recentGenerationTimes.isEmpty()) {
            return 0.0;
        }
        
        // Create a temporary list from the deque for stable iteration
        // This is a simple way to get a reasonably consistent sum for averaging.
        Long[] timesArray = recentGenerationTimes.toArray(new Long[0]);
        
        long sum = 0;
        int count = 0;
        for (Long time : timesArray) {
            if (time != null) { // ConcurrentLinkedDeque does not permit null elements
                sum += time;
                count++;
            }
        }
        return (count > 0) ? (double) sum / count : 0.0;
    }

    public int getSampleCount() {
        return recentGenerationTimes.size();
    }
} 