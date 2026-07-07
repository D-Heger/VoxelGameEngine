/**
 * The unit of work that generates a single chunk.
 *
 * <p>{@link de.heger.voxelengine.world.generation.tasks.ChunkGenerationTask}
 * wraps everything needed to build one chunk off the main thread &mdash; the
 * target position and the generator to run &mdash; into a task the thread pool
 * can pick up and execute. Bundling it as a task keeps generation cancellable,
 * schedulable, and easy to reason about in isolation.</p>
 */
package de.heger.voxelengine.world.generation.tasks;
