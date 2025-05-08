package de.heger.voxelengine.world.generation;

import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;

/**
 * A simple terrain generator that creates a flat world with distinct layers.
 * The layers consist of stone, topped with dirt, and a single layer of grass on the surface.
 * Air is placed above the grass layer.
 *
 * The world structure is defined by:
 * - {@link #GRASS_SURFACE_Y}: The Y-level where grass blocks are placed.
 * - {@link #DIRT_LAYERS}: The number of dirt layers beneath the grass.
 * - Stone fills all space below the dirt layers.
 * - Air fills all space above the grass layer.
 */
public class FlatTerrainGenerator implements TerrainGenerator {

    // Define the Y-level for the surface grass blocks.
    public static final int GRASS_SURFACE_Y = 63;
    // Define the number of dirt layers beneath the grass.
    public static final int DIRT_LAYERS = 3;
    // Calculate the Y-level where dirt starts (inclusive, top layer of dirt is GRASS_SURFACE_Y - 1).
    // The first layer of dirt (bottom-most) will be at GRASS_SURFACE_Y - DIRT_LAYERS.
    public static final int DIRT_START_Y = GRASS_SURFACE_Y - DIRT_LAYERS;


    private final short stoneId;
    private final short dirtId;
    private final short grassId;
    private final short airId;

    /**
     * Constructs a new FlatTerrainGenerator.
     * It retrieves necessary block IDs from the {@link BlockRegistry}.
     * Ensure the BlockRegistry is initialized before creating an instance of this generator.
     */
    public FlatTerrainGenerator() {
        BlockRegistry registry = BlockRegistry.getInstance();
        this.stoneId = registry.getId("core:block/stone");
        this.dirtId = registry.getId("core:block/dirt");
        this.grassId = registry.getId("core:block/grass");
        this.airId = BlockRegistry.AIR.getId(); // Assuming AIR block ID is 0
    }

    @Override
    public void generateChunkData(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPosition();
        int chunkWorldYOffset = chunkPos.y * Chunk.SIZE_Y;

        for (int localX = 0; localX < Chunk.SIZE_X; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_Z; localZ++) {
                for (int localY = 0; localY < Chunk.SIZE_Y; localY++) {
                    int worldY = chunkWorldYOffset + localY;
                    short blockToSet;

                    if (worldY == GRASS_SURFACE_Y) {
                        blockToSet = grassId;
                    } else if (worldY >= DIRT_START_Y && worldY < GRASS_SURFACE_Y) {
                        blockToSet = dirtId;
                    } else if (worldY < DIRT_START_Y) {
                        blockToSet = stoneId;
                    } else { // worldY > GRASS_SURFACE_Y
                        blockToSet = airId;
                    }
                    chunk.setBlock(localX, localY, localZ, blockToSet);
                }
            }
        }
    }
}
