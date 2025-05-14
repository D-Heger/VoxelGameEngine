package de.heger.voxelengine.world.generation;

import de.heger.voxelengine.core.noise.FastNoiseLite;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;

import java.util.Random;

/**
 * Generates terrain using FastNoiseLite to create a heightmap.
 * The terrain consists of stone, topped with a variable layer of dirt,
 * and a single layer of grass on the surface. Air is placed above the grass layer.
 */
public class NoiseTerrainGenerator implements TerrainGenerator {

    private static final int BASE_HEIGHT = 50; // Base Y level for the terrain surface before noise.
    private static final int MAX_HEIGHT_VARIATION = 15; // Max additional height noise can add.
    private static final int MIN_DIRT_LAYERS = 4;
    private static final int MAX_DIRT_LAYERS = 8;

    private final short stoneId;
    private final short dirtId;
    private final short grassId;
    private final short airId;
    private final int initialSeed; // Store the initial seed for ThreadLocal

    private final ThreadLocal<Random> threadLocalRandom;
    private final ThreadLocal<FastNoiseLite> threadLocalNoise; // Added ThreadLocal for FastNoiseLite

    /**
     * Constructs a new NoiseTerrainGenerator.
     * Initializes FastNoiseLite and retrieves necessary block IDs from the BlockRegistry.
     */
    public NoiseTerrainGenerator() {
        this(1337); // Default seed
    }

    /**
     * Constructs a new NoiseTerrainGenerator with a specific seed.
     * Initializes FastNoiseLite and retrieves necessary block IDs from the BlockRegistry.
     * @param seed The seed for the noise generator.
     */
    public NoiseTerrainGenerator(int seed) {
        this.initialSeed = seed; // Store the seed
        this.threadLocalRandom = ThreadLocal.withInitial(() -> new Random(this.initialSeed));

        // Initialize ThreadLocal FastNoiseLite
        this.threadLocalNoise = ThreadLocal.withInitial(() -> {
            FastNoiseLite fnl = new FastNoiseLite(this.initialSeed);
            fnl.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
            fnl.SetFrequency(0.005f);
            return fnl;
        });

        BlockRegistry registry = BlockRegistry.getInstance();
        this.stoneId = registry.getId("core:block/stone");
        this.dirtId = registry.getId("core:block/dirt");
        this.grassId = registry.getId("core:block/grass");
        this.airId = BlockRegistry.AIR.getId();
    }

    @Override
    public void generateChunkData(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPosition();
        int chunkWorldXOffset = chunkPos.x * Chunk.SIZE_X;
        int chunkWorldYOffset = chunkPos.y * Chunk.SIZE_Y;
        int chunkWorldZOffset = chunkPos.z * Chunk.SIZE_Z;

        for (int localX = 0; localX < Chunk.SIZE_X; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_Z; localZ++) {
                float worldX = chunkWorldXOffset + localX;
                float worldZ = chunkWorldZOffset + localZ;

                // Get thread-local noise instance
                FastNoiseLite localNoise = threadLocalNoise.get();

                // Get noise value (range -1 to 1)
                float noiseValue = localNoise.GetNoise(worldX, worldZ);

                // Calculate surface height for this column
                // Scale noise to 0-1 range, then apply variation and base height
                int surfaceHeight = (int) (((noiseValue + 1) / 2.0f) * MAX_HEIGHT_VARIATION + BASE_HEIGHT);

                // Get thread-local random instance
                Random localRandom = threadLocalRandom.get();

                // Determine number of dirt layers for this column
                int dirtLayers = MIN_DIRT_LAYERS + localRandom.nextInt(MAX_DIRT_LAYERS - MIN_DIRT_LAYERS + 1);
                int dirtTopY = surfaceHeight - 1; // Grass is at surfaceHeight
                int stoneTopY = dirtTopY - dirtLayers;

                for (int localY = 0; localY < Chunk.SIZE_Y; localY++) {
                    int worldY = chunkWorldYOffset + localY;
                    short blockToSet;

                    if (worldY == surfaceHeight) {
                        blockToSet = grassId;
                    } else if (worldY > stoneTopY && worldY <= dirtTopY) {
                        blockToSet = dirtId;
                    } else if (worldY <= stoneTopY) {
                        blockToSet = stoneId;
                    } else { // worldY > surfaceHeight
                        blockToSet = airId;
                    }
                    chunk.setBlock(localX, localY, localZ, blockToSet);
                }
            }
        }
    }
}
