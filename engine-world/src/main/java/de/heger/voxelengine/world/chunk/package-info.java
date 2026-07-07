/**
 * The heart of the world model: chunks, their coordinates, and their lifecycle.
 *
 * <p>The world is not one giant array &mdash; it is a grid of fixed-size
 * {@link de.heger.voxelengine.world.chunk.Chunk}s (16&times;16&times;16 blocks
 * each). {@link de.heger.voxelengine.world.chunk.ChunkManager} keeps track of
 * which chunks exist, loads the ones near the player, unloads the ones that drift
 * away, and links neighbours together so meshing can see across chunk borders.</p>
 *
 * <p>Supporting cast:</p>
 * <ul>
 *   <li>{@link de.heger.voxelengine.world.chunk.ChunkPos} &mdash; a chunk's
 *       position in the coarse chunk grid.</li>
 *   <li>{@link de.heger.voxelengine.world.chunk.CoordinateUtils} &mdash; the math
 *       for converting between world, chunk, and local block coordinates.</li>
 *   <li>{@link de.heger.voxelengine.world.chunk.Direction} &mdash; the six axis
 *       directions used when looking at neighbouring blocks and chunks.</li>
 *   <li>{@link de.heger.voxelengine.world.chunk.ChunkState} and
 *       {@link de.heger.voxelengine.world.chunk.ChunkMeshState} &mdash; where a
 *       chunk is in its load/generate/mesh journey.</li>
 * </ul>
 */
package de.heger.voxelengine.world.chunk;
