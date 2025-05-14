package de.heger.voxelengine.world.generation.thread;

import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;

/**
 * Interface for handling the results of asynchronous chunk generation tasks.
 * Implementations can define how to react to successfully generated chunks
 * or tasks that failed.
 */
public interface TaskResultHandler {

    /**
     * Called when a chunk generation task completes successfully.
     *
     * @param task The completed ChunkGenerationTask.
     * @param chunk The chunk that was generated. Note that the chunk is already
     *              added to ChunkManager by the task itself. This parameter provides
     *              a direct reference if needed by the handler.
     * @param durationMillis The time taken for the task to complete, in milliseconds.
     */
    void onTaskCompleted(ChunkGenerationTask task, Chunk chunk, long durationMillis);

    /**
     * Called when a chunk generation task fails.
     *
     * @param task      The ChunkGenerationTask that failed.
     * @param exception The exception that caused the failure.
     */
    void onTaskFailed(ChunkGenerationTask task, Exception exception);
} 