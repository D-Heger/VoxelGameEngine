package de.heger.voxelengine.world.generation.thread;

import de.heger.voxelengine.core.logging.LoggerFacade;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a ThreadPoolExecutor for executing world-related tasks asynchronously.
 * This class provides factory methods for creating configured thread pools,
 * methods for submitting tasks, and lifecycle management for the underlying executor service.
 */
public class WorldThreadPool {

    private static final LoggerFacade LOGGER = LoggerFacade.get(WorldThreadPool.class);

    private final ThreadPoolExecutor executorService;
    private final String poolName;

    /**
     * Constructs a new WorldThreadPool with the specified configuration.
     *
     * @param poolName          The name of the thread pool, used for thread naming and logging.
     * @param corePoolSize      The number of threads to keep in the pool, even if they are idle.
     * @param maximumPoolSize   The maximum number of threads to allow in the pool.
     * @param keepAliveTime     When the number of threads is greater than the core,
     *                          this is the maximum time that excess idle threads will wait
     *                          for new tasks before terminating.
     * @param unit              The time unit for the {@code keepAliveTime} argument.
     * @param workQueue         The queue to use for holding tasks before they are executed.
     *                          This queue will hold only the {@code Runnable} tasks submitted
     *                          by the {@code execute} method.
     * @param threadFactory     The factory to use when the executor creates a new thread.
     * @param handler           The handler to use when execution is blocked because the thread
     *                          bounds and queue capacities are reached.
     */
    public WorldThreadPool(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        this.poolName = poolName;
        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                handler
        );
        LOGGER.info("WorldThreadPool '{}' initialized with corePoolSize={}, maxPoolSize={}, keepAliveTime={} {}.",
                poolName, corePoolSize, maximumPoolSize, keepAliveTime, unit);
    }

    /**
     * Creates a new WorldThreadPool with default settings, suitable for general purpose background tasks.
     * The number of threads is determined based on the number of available processors,
     * typically N-1 (where N is processor count), with a minimum of 1.
     * It uses a {@link LinkedBlockingQueue} for tasks and a {@link NamedThreadFactory}.
     *
     * @param poolName The name for the thread pool, used for thread naming and logging.
     * @return A new WorldThreadPool instance with default configuration.
     */
    public static WorldThreadPool createDefault(String poolName) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        // Default to N-1 threads, but at least 1. This is a heuristic and can be tuned.
        int coreThreads = Math.max(1, availableProcessors > 1 ? availableProcessors - 1 : 1);
        // Cap threads for typical generation tasks. For a small number of cores, use a smaller fraction.
        if (availableProcessors > 4) {
            coreThreads = Math.min(coreThreads, Math.max(1,availableProcessors - 2)); // e.g., for 8 cores, use 6, for 6 cores use 4
        } else if (availableProcessors > 2) {
             coreThreads = Math.min(coreThreads, Math.max(1, availableProcessors -1)); // for 3 or 4 cores, use 2 or 3 respectively.
        } else {
            coreThreads = 1; // for 1 or 2 cores, use 1 thread
        }

        // Using a fixed-size pool (corePoolSize == maximumPoolSize) with an unbounded queue
        // means all tasks queue up if core threads are busy. This is a common pattern for background processing.
        int maxThreads = coreThreads;

        return new WorldThreadPool(
                poolName,
                coreThreads,
                maxThreads,
                60L, TimeUnit.SECONDS, // keepAliveTime for non-core threads (not applicable here as core=max)
                new LinkedBlockingQueue<>(), // Unbounded queue
                new NamedThreadFactory(poolName),
                new DefaultRejectedExecutionHandler(poolName)
        );
    }

    /**
     * Submits a Runnable task for execution and returns a Future representing that task.
     *
     * @param task The task to submit.
     * @return A Future representing pending completion of the task.
     */
    public Future<?> submit(Runnable task) {
        return executorService.submit(task);
    }

    /**
     * Submits a value-returning task for execution and returns a Future
     * representing the pending results of the task.
     *
     * @param task The task to submit.
     * @param <T> The type of the task's result.
     * @return A Future representing pending completion of the task.
     */
    public <T> Future<T> submit(Callable<T> task) {
        return executorService.submit(task);
    }

    /**
     * Executes the given command at some time in the future.
     * The command may execute in a new thread, in a pooled thread, or in the calling thread,
     * at the discretion of the {@code Executor} implementation.
     *
     * @param command The runnable task.
     */
    public void execute(Runnable command) {
        executorService.execute(command);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are
     * executed, but no new tasks will be accepted. Invocation has no
     * additional effect if already shut down.
     * This method does not wait for previously submitted tasks to complete
     * execution. Use {@link #awaitTermination} to do that.
     * It will wait for a period for tasks to complete, then attempt a forceful shutdown if they don't.
     */
    public void shutdown() {
        LOGGER.info("Shutting down WorldThreadPool '{}'...", poolName);
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                LOGGER.warn("WorldThreadPool '{}' did not terminate in 60 seconds. Forcing shutdown...", poolName);
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOGGER.error("WorldThreadPool '{}' did not terminate even after forcing.", poolName);
                }
            }
        } catch (InterruptedException ie) {
            LOGGER.warn("Shutdown of WorldThreadPool '{}' interrupted. Forcing shutdown...", poolName, ie);
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        LOGGER.info("WorldThreadPool '{}' has been shut down.", poolName);
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks that were
     * awaiting execution. 
     * This method does not wait for actively executing tasks to terminate. Use
     * {@link #awaitTermination} to do that.
     */
    public void shutdownNow() {
        LOGGER.info("Attempting immediate shutdown of WorldThreadPool '{}'...", poolName);
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                 LOGGER.warn("WorldThreadPool '{}' did not terminate within 5s after shutdownNow().", poolName);
            }
        } catch (InterruptedException e) {
             LOGGER.warn("Interrupted while awaiting termination after shutdownNow() for '{}'.", poolName, e);
             Thread.currentThread().interrupt();
        }
        LOGGER.info("WorldThreadPool '{}' shutdownNow completed.", poolName);
    }

    /**
     * Returns true if all tasks have completed following shut down.
     * Note that isTerminated is never true unless either shutdown or
     * shutdownNow was called first.
     *
     * @return true if all tasks have completed following shut down.
     */
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    /**
     * Returns true if this executor has been shut down.
     *
     * @return true if this executor has been shut down.
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    /**
     * Returns the approximate number of threads that are actively executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        return executorService.getActiveCount();
    }

    /**
     * Returns the name of this thread pool.
     *
     * @return The pool name.
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * Starts all core threads, causing them to idly wait for work.
     * This overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return The number of threads that were started.
     */
    public int prestartAllCoreThreads() {
        if (executorService != null) {
            return executorService.prestartAllCoreThreads();
        }
        return 0;
    }

    /**
     * A ThreadFactory that creates named threads for easier debugging and monitoring.
     * Threads created by this factory will have names like "poolName-thread-X"
     * and an UncaughtExceptionHandler that logs exceptions.
     */
    public static class NamedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        /**
         * Constructs a NamedThreadFactory.
         * @param poolName The base name for threads created by this factory.
         */
        public NamedThreadFactory(String poolName) {
            this.group = Thread.currentThread().getThreadGroup();
            this.namePrefix = poolName + "-thread-";
        }

        /**
         * Constructs a new {@code Thread}. Implementations may also initialize
         * priority, name, daemon status, {@code ThreadGroup}, etc.
         *
         * @param r a runnable to be executed by new thread instance
         * @return constructed thread, or {@code null} if the request to
         *         create a thread is rejected
         */
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon()) {
                t.setDaemon(false); // Ensure threads are not daemons by default
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            // Optional: Set an UncaughtExceptionHandler for threads created by this factory
            t.setUncaughtExceptionHandler((thread, throwable) ->
                LOGGER.error("Uncaught exception in thread '{}':", thread.getName(), throwable)
            );
            LOGGER.debug("Created new thread: {}", t.getName());
            return t;
        }
    }

    /**
     * A default RejectedExecutionHandler that logs the rejection and then
     * throws a RejectedExecutionException.
     */
    public static class DefaultRejectedExecutionHandler implements RejectedExecutionHandler {
        private final String poolName;
        /**
         * Constructs a DefaultRejectedExecutionHandler.
         * @param poolName The name of the pool to include in log messages.
         */
        public DefaultRejectedExecutionHandler(String poolName) {
            this.poolName = poolName;
        }

        /**
         * Method that may be invoked by a {@link ThreadPoolExecutor} when
         * {@link ThreadPoolExecutor#execute execute} cannot accept a task.
         * This may occur when no more threads or queue slots are available
         * because their bounds would be exceeded, or upon shutdown of the
         * Executor.
         *
         * @param r the runnable task requested to be executed
         * @param executor the executor attempting to execute this task
         * @throws RejectedExecutionException always.
         */
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            LOGGER.error("Task {} rejected from WorldThreadPool '{}'. Executor state: activeCount={}, poolSize={}, queueSize={}, isShutdown={}, isTerminating={}",
                r.toString(),
                poolName,
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size(),
                executor.isShutdown(),
                executor.isTerminating());
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + poolName + " " + executor.toString());
        }
    }
} 