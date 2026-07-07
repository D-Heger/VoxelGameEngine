/**
 * The background machinery that runs chunk generation without stalling the game.
 *
 * <p>Generating terrain is expensive, so it happens on a dedicated pool of worker
 * threads instead of the render loop.
 * {@link de.heger.voxelengine.world.generation.thread.WorldThreadPool} owns those
 * workers, {@link de.heger.voxelengine.world.generation.thread.GenerationTaskQueue}
 * feeds them work, and the various
 * {@link de.heger.voxelengine.world.generation.thread.TaskResultHandler}
 * implementations decide what happens when a task finishes &mdash; whether that is
 * logging the outcome or recording how long it took for the performance overlay.</p>
 */
package de.heger.voxelengine.world.generation.thread;
