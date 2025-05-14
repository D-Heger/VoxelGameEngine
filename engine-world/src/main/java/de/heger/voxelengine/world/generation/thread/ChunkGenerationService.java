package de.heger.voxelengine.world.generation.thread;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.generation.TerrainGenerator;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing asynchronous chunk generation.
 * It coordinates a thread pool and a prioritized task queue to generate chunks
 * based on requests.
 */
public class ChunkGenerationService {

    private static final LoggerFacade LOGGER = LoggerFacade.get(ChunkGenerationService.class);

    private final WorldThreadPool threadPool;
    private final GenerationTaskQueue taskQueue; // This is the queue the threadPool will use
    private final TerrainGenerator defaultTerrainGenerator;
    private final TaskResultHandler defaultTaskResultHandler;

    // To prevent re-queueing tasks already in the GenerationTaskQueue or being processed
    private final Set<ChunkPos> submittedTaskPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Constructs a new ChunkGenerationService.
     *
     * @param defaultTerrainGenerator The default terrain generator to use for chunk population.
     * @param defaultTaskResultHandler The default handler for task completion/failure notifications.
     * @param corePoolSize The core number of worker threads.
     * @param maxPoolSize The maximum number of worker threads.
     * @param keepAliveSeconds The keep-alive time for idle threads beyond the core size.
     * @param queueCapacity The maximum capacity of the generation task queue.
     */
    public ChunkGenerationService(
            TerrainGenerator defaultTerrainGenerator,
            TaskResultHandler defaultTaskResultHandler,
            int corePoolSize, int maxPoolSize, int keepAliveSeconds,
            int queueCapacity
    ) {
        this.defaultTerrainGenerator = Objects.requireNonNull(defaultTerrainGenerator, "defaultTerrainGenerator cannot be null");
        this.defaultTaskResultHandler = Objects.requireNonNull(defaultTaskResultHandler, "defaultTaskResultHandler cannot be null");

        this.taskQueue = new GenerationTaskQueue(queueCapacity);

        // The WorldThreadPool will use the underlying queue from GenerationTaskQueue.
        // ChunkGenerationTask is Runnable, so PriorityBlockingQueue<ChunkGenerationTask> can be cast.
        @SuppressWarnings("unchecked") // Safe cast due to ChunkGenerationTask implementing Runnable
        BlockingQueue<Runnable> workQueue = (BlockingQueue<Runnable>)(Object)this.taskQueue.getUnderlyingQueue();

        this.threadPool = new WorldThreadPool(
                "ChunkGenSvcPool",
                corePoolSize, maxPoolSize,
                keepAliveSeconds, TimeUnit.SECONDS,
                workQueue,
                new WorldThreadPool.NamedThreadFactory("ChunkGenSvcPool"),
                new WorldThreadPool.DefaultRejectedExecutionHandler("ChunkGenSvcPool")
        );
        LOGGER.info("ChunkGenerationService initialized with {}-{} threads, queue capacity {}, default terrain generator: {}, default result handler: {}.",
                corePoolSize, maxPoolSize, queueCapacity,
                defaultTerrainGenerator.getClass().getSimpleName(),
                defaultTaskResultHandler.getClass().getSimpleName());

        // Attempt to prestart core threads
        if (this.threadPool != null) {
            int startedThreads = this.threadPool.prestartAllCoreThreads();
            LOGGER.info("Attempted to prestart core threads for pool '{}'. Threads reportedly started: {}", 
                        this.threadPool.getPoolName(), startedThreads);
            if (startedThreads == 0 && corePoolSize > 0) {
                LOGGER.warn("Failed to prestart any core threads for pool '{}', though corePoolSize is {}. Generation tasks may not run.", 
                            this.threadPool.getPoolName(), corePoolSize);
            }
        } else {
            LOGGER.error("WorldThreadPool is null after initialization in ChunkGenerationService. Cannot prestart threads.");
        }
    }

    /**
     * Requests the generation of a chunk at the specified position with default priority.
     * Uses the service's default terrain generator and task result handler.
     *
     * @param chunkPos The position of the chunk to generate.
     * @param priority The priority for the generation task.
     * @return {@code true} if the generation request was successfully queued, {@code false} otherwise
     *         (e.g., chunk already exists, already queued, or queue is full).
     */
    public boolean requestChunkGeneration(ChunkPos chunkPos, int priority) {
        return requestChunkGeneration(chunkPos, priority, this.defaultTerrainGenerator, this.defaultTaskResultHandler);
    }

    /**
     * Requests the generation of a chunk at the specified position with specified parameters.
     *
     * @param chunkPos The position of the chunk to generate.
     * @param priority The priority for the generation task.
     * @param terrainGenerator The specific terrain generator to use for this chunk.
     * @param resultHandler The specific result handler for this task.
     * @return {@code true} if the generation request was successfully queued, {@code false} otherwise.
     */
    public boolean requestChunkGeneration(ChunkPos chunkPos, int priority,
                                       TerrainGenerator terrainGenerator,
                                       TaskResultHandler resultHandler) {
        Objects.requireNonNull(chunkPos, "chunkPos cannot be null");
        Objects.requireNonNull(terrainGenerator, "terrainGenerator cannot be null");
        Objects.requireNonNull(resultHandler, "resultHandler cannot be null");

        // Check if chunk already exists in ChunkManager
        if (ChunkManager.getInstance().containsChunk(chunkPos)) {
            LOGGER.trace("Chunk {} already loaded by ChunkManager, skipping generation request.", chunkPos);
            return false;
        }

        // Atomically add to submittedTaskPositions and check if it was already present.
        // If add returns false, it means the element was already in the set.
        if (!submittedTaskPositions.add(chunkPos)) {
            LOGGER.trace("Chunk {} generation already requested or actively being processed, skipping.", chunkPos);
            return false;
        }

        // Wrap the provided result handler to include cleanup logic for submittedTaskPositions
        ServiceInternalResultHandler internalHandler = new ServiceInternalResultHandler(resultHandler, chunkPos);
        ChunkGenerationTask task = new ChunkGenerationTask(chunkPos, priority, terrainGenerator, internalHandler);

        boolean addedToQueue = taskQueue.addTask(task);
        if (!addedToQueue) {
            // If task couldn't be added to the queue (e.g., full), remove from tracking set.
            submittedTaskPositions.remove(chunkPos);
            LOGGER.warn("Failed to add chunk generation task for {} to GenerationTaskQueue (it might be full). Task not submitted.", chunkPos);
            return false;
        }

        LOGGER.debug("Requested chunk generation for {}. Task added to GenerationTaskQueue.", chunkPos);
        // The WorldThreadPool's ThreadPoolExecutor will automatically pick up tasks from the taskQueue.
        return true;
    }

    /**
     * Initiates an orderly shutdown of the chunk generation service.
     * Previously submitted tasks may be executed, but no new tasks will be accepted by the queue
     * once the underlying thread pool starts shutting down.
     */
    public void shutdown() {
        LOGGER.info("Shutting down ChunkGenerationService...");
        threadPool.shutdown(); // Initiates shutdown of the ThreadPoolExecutor

        int remainingTasksInQueue = taskQueue.size();
        if (remainingTasksInQueue > 0) {
            LOGGER.warn("{} tasks were still in GenerationTaskQueue during shutdown. These tasks will be cleared and not processed.", remainingTasksInQueue);
            taskQueue.clear(); // Clear tasks that were not picked up by the pool before shutdown
        }
        submittedTaskPositions.clear(); // Clear the tracking set
        LOGGER.info("ChunkGenerationService shutdown initiated. Waiting for active tasks to complete...");
        // To wait for completion, one might call threadPool.awaitTermination if WorldThreadPool exposes it,
        // or rely on the GameLoop's general shutdown sequence.
    }

    /**
     * Gets the number of tasks currently pending in the generation queue.
     * @return The number of pending tasks.
     */
    public int getPendingTaskCount() {
        return taskQueue.size();
    }

    /**
     * Gets the approximate number of threads that are actively executing tasks.
     * @return The number of active worker threads.
     */
    public int getActiveWorkerCount() {
        return threadPool.getActiveCount();
    }

    /**
     * Inner class to wrap the user-provided TaskResultHandler.
     * This ensures that submittedTaskPositions is cleaned up regardless of task outcome.
     */
    private class ServiceInternalResultHandler implements TaskResultHandler {
        private final TaskResultHandler originalHandler;
        private final ChunkPos taskChunkPos; // Store ChunkPos to ensure correct removal

        ServiceInternalResultHandler(TaskResultHandler originalHandler, ChunkPos taskChunkPos) {
            this.originalHandler = Objects.requireNonNull(originalHandler, "originalHandler cannot be null");
            this.taskChunkPos = Objects.requireNonNull(taskChunkPos, "taskChunkPos cannot be null for internal handler");
        }

        @Override
        public void onTaskCompleted(ChunkGenerationTask task, Chunk chunk, long durationMillis) {
            submittedTaskPositions.remove(this.taskChunkPos);
            LOGGER.trace("Task for chunk {} completed. Removed from tracking. Notifying original handler.", this.taskChunkPos);
            originalHandler.onTaskCompleted(task, chunk, durationMillis);
        }

        @Override
        public void onTaskFailed(ChunkGenerationTask task, Exception exception) {
            submittedTaskPositions.remove(this.taskChunkPos);
            LOGGER.trace("Task for chunk {} failed. Removed from tracking. Notifying original handler.", this.taskChunkPos, exception);
            originalHandler.onTaskFailed(task, exception);
        }
    }
} 