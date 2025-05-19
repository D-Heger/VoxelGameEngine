package de.heger.voxelengine.world.generation.tasks;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.ChunkState;
import de.heger.voxelengine.world.chunk.Direction;
import de.heger.voxelengine.world.generation.TerrainGenerator;
import de.heger.voxelengine.world.generation.thread.TaskResultHandler;

import java.util.Objects;

/**
 * A Runnable task that encapsulates the logic for generating a single chunk.
 * This task will be executed by a worker thread from the WorldThreadPool.
 * It holds a ChunkPos to identify which chunk to generate and a priority
 * to allow ordering in a PriorityBlockingQueue.
 */
public class ChunkGenerationTask implements Runnable, Comparable<ChunkGenerationTask> {

    private static final LoggerFacade LOGGER = LoggerFacade.get(ChunkGenerationTask.class);

    private final ChunkPos chunkPos;
    private final int priority; // Lower values mean higher priority
    private final TerrainGenerator terrainGenerator;
    private final TaskResultHandler resultHandler;

    /**
     * Constructs a new ChunkGenerationTask.
     *
     * @param chunkPos The position of the chunk to be generated.
     * @param priority The priority of this task. Lower values indicate higher priority.
     * @param terrainGenerator The terrain generator to use for populating the chunk.
     * @param resultHandler The handler to be notified of task completion or failure. May be null.
     */
    public ChunkGenerationTask(ChunkPos chunkPos, int priority, TerrainGenerator terrainGenerator, TaskResultHandler resultHandler) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos cannot be null");
        this.priority = priority;
        this.terrainGenerator = Objects.requireNonNull(terrainGenerator, "terrainGenerator cannot be null");
        this.resultHandler = resultHandler;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("ChunkGenTask-" + chunkPos.x + "_" + chunkPos.y + "_" + chunkPos.z);
        LOGGER.debug("Starting generation for chunk {} with priority {}.", chunkPos, priority);
        Chunk newChunk = null;
        long startTime = System.currentTimeMillis(); // Record start time

        try {
            // It's possible the ChunkGenerationService already checked this,
            // but ChunkManager.addChunk also handles overwriting if necessary.
            // For safety, we could add a check here too, though it might be redundant
            // depending on ChunkGenerationService's implementation.
            // If ChunkManager.getInstance().containsChunk(chunkPos) then log and return.
            // For now, we assume ChunkManager handles duplicates gracefully or service prevents them.

            newChunk = new Chunk(chunkPos);
            terrainGenerator.generateChunkData(newChunk);
            newChunk.setState(ChunkState.GENERATED);

            // --- Set Neighbors (P3-T8) ---
            ChunkManager chunkManager = ChunkManager.getInstance();
            for (Direction direction : Direction.values()) {
                Vec3i offset = direction.getOffset();
                ChunkPos neighborPos = new ChunkPos(
                        newChunk.getPosition().x + offset.x,
                        newChunk.getPosition().y + offset.y,
                        newChunk.getPosition().z + offset.z
                );

                Chunk neighborChunk = chunkManager.getChunk(neighborPos); // Synchronized call

                if (neighborChunk != null) {
                    // Set neighbor for the newly generated chunk
                    newChunk.setNeighbor(direction, neighborChunk); // Synchronized call

                    // Set the newly generated chunk as a neighbor for the existing neighbor
                    neighborChunk.setNeighbor(direction.getOpposite(), newChunk); // Synchronized call
                    LOGGER.trace("Set neighbor link between {} ({}) and {} ({})",
                                 newChunk.getPosition(), direction,
                                 neighborChunk.getPosition(), direction.getOpposite());
                }
            }
            // --- End Set Neighbors ---

            // The ChunkManager is synchronized, so adding the chunk is thread-safe.
            // ChunkManager.getInstance().addChunk(newChunk); // Removed: This is now handled by the TaskResultHandler

            long durationMillis = System.currentTimeMillis() - startTime; // Calculate duration
            LOGGER.debug("Successfully generated chunk {}. Priority: {}. Duration: {}ms. Notifying result handler.",
                    chunkPos, priority, durationMillis); // Adjusted log message

            if (resultHandler != null) {
                resultHandler.onTaskCompleted(this, newChunk, durationMillis); // Pass duration
            }

        } catch (Exception e) {
            LOGGER.error("Error generating chunk {}: {}", chunkPos, e.getMessage(), e);
            if (resultHandler != null) {
                resultHandler.onTaskFailed(this, e);
            }
            // Depending on the error handling strategy, we might want to:
            // - Retry the task.
            // - Mark this chunk position as "failed to generate" to avoid repeated attempts.
            // - Notify a central error handling system.
            // For now, we just log the error.
        } finally {
            // Clean up thread name if it was set temporarily, or ensure thread pool handles it.
            // Thread.currentThread().setName("WorkerThread-Idle"); // Or similar
        }
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public int getPriority() {
        return priority;
    }

    public TerrainGenerator getTerrainGenerator() {
        return terrainGenerator;
    }

    public TaskResultHandler getResultHandler() {
        return resultHandler;
    }

    @Override
    public int compareTo(ChunkGenerationTask other) {
        return Integer.compare(this.priority, other.priority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkGenerationTask that = (ChunkGenerationTask) o;
        // Priority, terrainGenerator, and resultHandler are not part of equality,
        // tasks for the same position are considered equal for queue management.
        return Objects.equals(chunkPos, that.chunkPos);
    }

    @Override
    public int hashCode() {
        // Priority, terrainGenerator, and resultHandler are not part of the hash code.
        return Objects.hash(chunkPos);
    }

    @Override
    public String toString() {
        return "ChunkGenerationTask{" +
                "chunkPos=" + chunkPos +
                ", priority=" + priority +
                // ", terrainGenerator=" + terrainGenerator.getClass().getSimpleName() + // Could be verbose
                '}';
    }
} 