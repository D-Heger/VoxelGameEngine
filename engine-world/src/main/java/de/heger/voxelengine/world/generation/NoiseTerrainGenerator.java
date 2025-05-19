package de.heger.voxelengine.world.generation;

import de.heger.voxelengine.core.noise.FastNoiseLite;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;

import java.util.Random;

/**
 * Generates terrain using FastNoiseLite to create a heightmap with domain warping and detail noise.
 * The terrain consists of stone, topped with a variable layer of dirt,
 * and a single layer of grass on the surface. Air is placed above the grass layer.
 */
public class NoiseTerrainGenerator implements TerrainGenerator {

    private final int baseHeight; // Base Y level for the terrain surface before noise.
    private final int maxHeightVariation; // Max additional height noise can add.
    private static final int MIN_DIRT_LAYERS = 4;
    private static final int MAX_DIRT_LAYERS = 8;

    private final short stoneId;
    private final short dirtId;
    private final short grassId;
    private final short airId;
    private final int initialSeed; // Store the initial seed for ThreadLocal

    // Main terrain noise parameters
    private final float frequency;
    private final FastNoiseLite.NoiseType noiseType;
    private final FastNoiseLite.FractalType fractalType;
    private final int octaves;
    private final float lacunarity;
    private final float gain;

    // Domain Warp noise parameters
    private final float warpFrequency;
    private final FastNoiseLite.DomainWarpType warpDomainWarpType;
    private final FastNoiseLite.FractalType warpFractalType;
    private final int warpOctaves;
    private final float warpLacunarity;
    private final float warpGain;
    private final float warpAmplitude;

    // Detail noise parameters
    private final float detailFrequency;
    private final FastNoiseLite.NoiseType detailNoiseType;
    private final FastNoiseLite.FractalType detailFractalType;
    private final int detailOctaves;
    private final float detailLacunarity;
    private final float detailGain;
    private final float detailAmplitude;

    private final ThreadLocal<Random> threadLocalRandom;
    private final ThreadLocal<FastNoiseLite> threadLocalNoise; // Added ThreadLocal for FastNoiseLite
    private final ThreadLocal<FastNoiseLite> threadLocalDomainWarper;
    private final ThreadLocal<FastNoiseLite> threadLocalDetailNoise;

    // Seed offsets for different noise instances
    private static final int DOMAIN_WARP_SEED_OFFSET = 1;
    private static final int DETAIL_NOISE_SEED_OFFSET = 2;

    /**
     * Constructs a new NoiseTerrainGenerator with default settings.
     */
    public NoiseTerrainGenerator() {
        this(1337, 50, 50, 
             0.005f, FastNoiseLite.NoiseType.OpenSimplex2, FastNoiseLite.FractalType.FBm, 3, 2.0f, 0.5f, // Main noise
             0.01f, FastNoiseLite.DomainWarpType.OpenSimplex2, FastNoiseLite.FractalType.None, 1, 2.0f, 0.5f, 20.0f, // Domain warp
             0.02f, FastNoiseLite.NoiseType.OpenSimplex2, FastNoiseLite.FractalType.FBm, 2, 2.0f, 0.5f, 0.3f // Detail noise
        );
    }

    /**
     * Constructs a new NoiseTerrainGenerator with a specific seed and default other parameters.
     * @param seed The seed for the noise generator.
     */
    public NoiseTerrainGenerator(int seed) {
        this(seed, 50, 50, 
             0.005f, FastNoiseLite.NoiseType.OpenSimplex2, FastNoiseLite.FractalType.FBm, 3, 2.0f, 0.5f, // Main noise
             0.01f, FastNoiseLite.DomainWarpType.OpenSimplex2, FastNoiseLite.FractalType.None, 1, 2.0f, 0.5f, 20.0f, // Domain warp
             0.02f, FastNoiseLite.NoiseType.OpenSimplex2, FastNoiseLite.FractalType.FBm, 2, 2.0f, 0.5f, 0.3f // Detail noise
        );
    }

    /**
     * Constructs a new NoiseTerrainGenerator with a specific seed, base and max height variation, and default other parameters.
     * @param seed The seed for the noise generator.
     * @param baseHeight The base height of the terrain.
     * @param maxHeightVariation The maximum height variation of the terrain.
     */
    public NoiseTerrainGenerator(int seed, int baseHeight, int maxHeightVariation) {
        this(seed, baseHeight, maxHeightVariation, 
             0.005f, FastNoiseLite.NoiseType.OpenSimplex2, FastNoiseLite.FractalType.FBm, 3, 2.0f, 0.5f, // Main noise
             0.01f, FastNoiseLite.DomainWarpType.OpenSimplex2, FastNoiseLite.FractalType.None, 1, 2.0f, 0.5f, 20.0f, // Domain warp
             0.02f, FastNoiseLite.NoiseType.OpenSimplex2, FastNoiseLite.FractalType.FBm, 2, 2.0f, 0.5f, 0.3f // Detail noise
        );
    } 

    /**
     * Constructs a new NoiseTerrainGenerator with detailed parameters.
     */
    public NoiseTerrainGenerator(int seed, int baseHeight, int maxHeightVariation, 
                                 float frequency, FastNoiseLite.NoiseType noiseType, FastNoiseLite.FractalType fractalType, int octaves, float lacunarity, float gain,
                                 float warpFrequency, FastNoiseLite.DomainWarpType warpDomainWarpType, FastNoiseLite.FractalType warpFractalType, int warpOctaves, float warpLacunarity, float warpGain, float warpAmplitude,
                                 float detailFrequency, FastNoiseLite.NoiseType detailNoiseType, FastNoiseLite.FractalType detailFractalType, int detailOctaves, float detailLacunarity, float detailGain, float detailAmplitude) {
        this.initialSeed = seed;
        this.baseHeight = baseHeight;
        this.maxHeightVariation = maxHeightVariation;

        // Main noise params
        this.frequency = frequency;
        this.noiseType = noiseType;
        this.fractalType = fractalType;
        this.octaves = octaves;
        this.lacunarity = lacunarity;
        this.gain = gain;

        // Domain warp params
        this.warpFrequency = warpFrequency;
        this.warpDomainWarpType = warpDomainWarpType;
        this.warpFractalType = warpFractalType;
        this.warpOctaves = warpOctaves;
        this.warpLacunarity = warpLacunarity;
        this.warpGain = warpGain;
        this.warpAmplitude = warpAmplitude;

        // Detail noise params
        this.detailFrequency = detailFrequency;
        this.detailNoiseType = detailNoiseType;
        this.detailFractalType = detailFractalType;
        this.detailOctaves = detailOctaves;
        this.detailLacunarity = detailLacunarity;
        this.detailGain = detailGain;
        this.detailAmplitude = detailAmplitude;

        this.threadLocalRandom = ThreadLocal.withInitial(() -> new Random(this.initialSeed));

        this.threadLocalNoise = ThreadLocal.withInitial(() -> {
            FastNoiseLite fnl = new FastNoiseLite(this.initialSeed);
            fnl.SetNoiseType(this.noiseType);
            fnl.SetFrequency(this.frequency);
            fnl.SetFractalType(this.fractalType);
            fnl.SetFractalOctaves(this.octaves);
            fnl.SetFractalLacunarity(this.lacunarity);
            fnl.SetFractalGain(this.gain);
            return fnl;
        });

        this.threadLocalDomainWarper = ThreadLocal.withInitial(() -> {
            FastNoiseLite fnl = new FastNoiseLite(this.initialSeed + DOMAIN_WARP_SEED_OFFSET);
            fnl.SetFrequency(this.warpFrequency);
            fnl.SetDomainWarpType(this.warpDomainWarpType);
            fnl.SetDomainWarpAmp(this.warpAmplitude);
            
            fnl.SetFractalType(this.warpFractalType);
            fnl.SetFractalOctaves(this.warpOctaves);
            fnl.SetFractalLacunarity(this.warpLacunarity);
            fnl.SetFractalGain(this.warpGain);
            return fnl;
        });

        this.threadLocalDetailNoise = ThreadLocal.withInitial(() -> {
            FastNoiseLite fnl = new FastNoiseLite(this.initialSeed + DETAIL_NOISE_SEED_OFFSET);
            fnl.SetNoiseType(this.detailNoiseType);
            fnl.SetFrequency(this.detailFrequency);
            fnl.SetFractalType(this.detailFractalType);
            fnl.SetFractalOctaves(this.detailOctaves);
            fnl.SetFractalLacunarity(this.detailLacunarity);
            fnl.SetFractalGain(this.detailGain);
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

        FastNoiseLite mainNoise = threadLocalNoise.get();
        FastNoiseLite domainWarper = threadLocalDomainWarper.get();
        FastNoiseLite detailNoiseGen = threadLocalDetailNoise.get();
        Random localRandom = threadLocalRandom.get();

        for (int localX = 0; localX < Chunk.SIZE_X; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE_Z; localZ++) {
                float worldX = chunkWorldXOffset + localX;
                float worldZ = chunkWorldZOffset + localZ;

                // Create a coordinate object for domain warping
                FastNoiseLite.Vector2 coords = new FastNoiseLite.Vector2(worldX, worldZ);
                domainWarper.DomainWarp(coords); // Apply domain warp in-place

                // Primary terrain noise (using warped coordinates)
                float primaryNoiseValue = mainNoise.GetNoise(coords.x, coords.y);

                // Detail noise (also using warped coordinates)
                float detailNoiseValue = detailNoiseGen.GetNoise(coords.x, coords.y);

                // Combine noise: primary + (detail * amplitude)
                float finalNoiseValue = primaryNoiseValue + (detailNoiseValue * detailAmplitude);

                // Normalize finalNoiseValue. Range is roughly: [-1 - abs(detailAmplitude), 1 + abs(detailAmplitude)]
                float normalizedNoise;
                float effectiveDetailAmplitude = Math.abs(detailAmplitude);
                if (effectiveDetailAmplitude == 0) { 
                     normalizedNoise = (primaryNoiseValue + 1.0f) / 2.0f;
                } else {
                    float rMin = -1.0f - effectiveDetailAmplitude;
                    float rMax = 1.0f + effectiveDetailAmplitude;
                    // Denominator is (rMax - rMin) = 2.0f * (1.0f + effectiveDetailAmplitude)
                    // This denominator should not be zero if effectiveDetailAmplitude >= 0
                    normalizedNoise = (finalNoiseValue - rMin) / (rMax - rMin) ; 
                }
                 // Clamp normalizedNoise to [0,1] just in case of floating point inaccuracies or extreme noise values
                normalizedNoise = Math.max(0.0f, Math.min(1.0f, normalizedNoise));

                int surfaceHeight = (int) (normalizedNoise * this.maxHeightVariation + this.baseHeight);

                int dirtLayers = MIN_DIRT_LAYERS + localRandom.nextInt(MAX_DIRT_LAYERS - MIN_DIRT_LAYERS + 1);
                int dirtTopY = surfaceHeight - 1; 
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
                    } else { 
                        blockToSet = airId;
                    }
                    chunk.setBlock(localX, localY, localZ, blockToSet);
                }
            }
        }
    }
}
