/**
 * The coordination layer that turns "I need this chunk" into a finished chunk.
 *
 * <p>{@link de.heger.voxelengine.world.generation.service.ChunkGenerationService}
 * is the front door for the rest of the engine: the chunk manager asks it to
 * generate a chunk, and it schedules the work on the background thread pool and
 * delivers the result once ready. Keeping this behind a service interface means
 * callers never have to think about threads, queues, or task handlers.</p>
 */
package de.heger.voxelengine.world.generation.service;
