package de.heger.voxelengine.world.generation;

import de.heger.voxelengine.world.chunk.Chunk;

/**
 * Interface for terrain generation algorithms.
 * Implementations of this interface are responsible for populating a {@link Chunk}
 * with block data based on its position and the generation algorithm.
 */
@FunctionalInterface
public interface TerrainGenerator {

    /**
     * Populates the given chunk with block data according to the terrain generation algorithm.
     * The implementation should use {@link Chunk#setBlock(int, int, int, short)} to set block IDs.
     * The chunk's position can be obtained via {@link Chunk#getPosition()}.
     * Block ids can be looked up through the {@link de.heger.voxelengine.world.block.BlockRegistry} singleton.
     *
     * @param chunk The chunk to be populated. Must not be null.
     */
    void generateChunkData(Chunk chunk);
}
