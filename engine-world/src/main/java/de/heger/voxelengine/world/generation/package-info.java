/**
 * Deciding what blocks fill a freshly created chunk.
 *
 * <p>A {@link de.heger.voxelengine.world.generation.TerrainGenerator} is handed
 * an empty chunk and fills it in. The engine ships two:
 * {@link de.heger.voxelengine.world.generation.FlatTerrainGenerator} for a simple
 * flat world, and {@link de.heger.voxelengine.world.generation.NoiseTerrainGenerator}
 * which samples Perlin-style noise to raise hills and carve valleys.
 * {@link de.heger.voxelengine.world.generation.ChunkGenerator} ties a generator
 * to a chunk request.</p>
 *
 * <p>Generation is CPU-heavy, so it does not happen on the render thread. The
 * {@link de.heger.voxelengine.world.generation.service} and
 * {@link de.heger.voxelengine.world.generation.thread} sub-packages move that
 * work onto a background pool and hand finished chunks back safely.</p>
 */
package de.heger.voxelengine.world.generation;
