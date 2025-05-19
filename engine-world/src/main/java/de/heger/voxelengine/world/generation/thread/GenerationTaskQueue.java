package de.heger.voxelengine.world.generation.thread;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.world.generation.tasks.ChunkGenerationTask;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe priority queue for managing {@link ChunkGenerationTask} instances.
 * Tasks are ordered based on their priority, with lower numerical priority values
 * indicating higher urgency.
 * This queue can have a maximum capacity to prevent unbounded growth.
 */
public class GenerationTaskQueue {

    private static final LoggerFacade LOGGER = LoggerFacade.get(GenerationTaskQueue.class);

    private final PriorityBlockingQueue<ChunkGenerationTask> queue;
    private final int maxCapacity;

    /**
     * Constructs a new GenerationTaskQueue with a default initial capacity for the
     * underlying PriorityBlockingQueue and a specified maximum capacity.
     *
     * @param maxCapacity The maximum number of tasks this queue can hold. Must be positive.
     */
    public GenerationTaskQueue(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Max capacity must be positive.");
        }
        this.queue = new PriorityBlockingQueue<>(); // Default initial capacity of 11
        this.maxCapacity = maxCapacity;
        LOGGER.info("GenerationTaskQueue initialized with max capacity: {}", maxCapacity);
    }

    /**
     * Adds a chunk generation task to the queue if the queue is not at its maximum capacity.
     * If the task is null or the queue is full, the task is not added.
     *
     * @param task The task to add. Must not be null.
     * @return {@code true} if the task was added, {@code false} otherwise (e.g., queue is full).
     */
    public boolean addTask(ChunkGenerationTask task) {
        if (task == null) {
            LOGGER.warn("Attempted to add a null task to the queue.");
            return false;
        }
        if (queue.size() >= maxCapacity) {
            LOGGER.warn("GenerationTaskQueue is at maximum capacity ({}). Cannot add task: {}", maxCapacity, task);
            return false;
        }
        // PriorityBlockingQueue does not allow null elements, offer handles this.
        boolean added = queue.offer(task);
        if (added) {
            LOGGER.debug("Added task to queue: {}. Current size: {}", task, queue.size());
        } else {
            // Should not happen with PriorityBlockingQueue unless there's an unexpected issue.
            LOGGER.error("Failed to add task to queue: {}. This is unexpected.", task);
        }
        return added;
    }

    /**
     * Retrieves and removes the highest-priority task from this queue,
     * waiting up to the specified time if necessary for an element to become available.
     *
     * @param timeout How long to wait before giving up, in units of {@code unit}.
     * @param unit    A {@code TimeUnit} determining how to interpret the {@code timeout} parameter.
     * @return The head of this queue, or {@code null} if the specified waiting time elapses
     *         before an element is available.
     * @throws InterruptedException if interrupted while waiting.
     */
    public ChunkGenerationTask pollTask(long timeout, TimeUnit unit) throws InterruptedException {
        ChunkGenerationTask task = queue.poll(timeout, unit);
        if (task != null) {
            LOGGER.debug("Polled task from queue: {}. Remaining size: {}", task, queue.size());
        }
        return task;
    }

    /**
     * Retrieves and removes the highest-priority task from this queue, waiting if necessary
     * until an element becomes available.
     *
     * @return The head of this queue.
     * @throws InterruptedException if interrupted while waiting.
     */
    public ChunkGenerationTask takeTask() throws InterruptedException {
        ChunkGenerationTask task = queue.take();
        LOGGER.debug("Took task from queue: {}. Remaining size: {}", task, queue.size());
        return task;
    }

    /**
     * Retrieves, but does not remove, the highest-priority task of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @return The head of this queue, or {@code null} if this queue is empty.
     */
    public ChunkGenerationTask peekTask() {
        return queue.peek();
    }

    /**
     * Removes a single instance of the specified element from this queue, if it is present.
     * More formally, removes an element {@code e} such that {@code task.equals(e)}.
     * This operation iterates over the queue, so it can be O(n).
     *
     * @param task The task to remove.
     * @return {@code true} if the task was removed, {@code false} otherwise.
     */
    public boolean removeTask(ChunkGenerationTask task) {
        if (task == null) return false;
        boolean removed = queue.remove(task);
        if (removed) {
            LOGGER.debug("Removed task from queue: {}. Current size: {}", task, queue.size());
        }
        return removed;
    }

    /**
     * Returns the number of tasks currently in the queue.
     *
     * @return The current size of the queue.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns {@code true} if this queue contains no tasks.
     *
     * @return {@code true} if this queue is empty, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Removes all tasks from the queue.
     */
    public void clear() {
        queue.clear();
        LOGGER.info("GenerationTaskQueue cleared. Size is now 0.");
    }

    /**
     * Checks if the queue is at its maximum capacity.
     *
     * @return {@code true} if the number of tasks in the queue has reached maxCapacity,
     *         {@code false} otherwise.
     */
    public boolean isAtCapacity() {
        return queue.size() >= maxCapacity;
    }

    /**
     * Gets the maximum capacity of this queue.
     *
     * @return The maximum capacity.
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Returns the underlying {@link PriorityBlockingQueue} that backs this task queue.
     * This is intended for use by systems like a {@link ThreadPoolExecutor} that need direct
     * access to the blocking queue.
     *
     * @return The underlying PriorityBlockingQueue instance.
     */
    public PriorityBlockingQueue<ChunkGenerationTask> getUnderlyingQueue() {
        return queue;
    }
} 