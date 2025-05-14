package de.heger.voxelengine.world.generation;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.core.utils.Validate;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.ChunkState;

/**
 * Service class responsible for orchestrating the creation and population of new {@link Chunk} instances.
 * It uses a {@link TerrainGenerator} to fill chunks with block data and the {@link ChunkManager}
 * to store and manage these chunks.
 */
public class ChunkGenerator {

    private static final LoggerFacade LOGGER = LoggerFacade.get(ChunkGenerator.class);

    private final TerrainGenerator terrainGenerator;
    private final ChunkManager chunkManager;

    /**
     * Constructs a new ChunkGenerator with the specified terrain generator.
     *
     * @param terrainGenerator The terrain generator to use for populating chunks. Must not be null.
     */
    public ChunkGenerator(TerrainGenerator terrainGenerator) {
        Validate.notNull(terrainGenerator, "TerrainGenerator cannot be null");
        this.terrainGenerator = terrainGenerator;
        this.chunkManager = ChunkManager.getInstance();
        LOGGER.info("ChunkGenerator initialized with TerrainGenerator: {}", terrainGenerator.getClass().getSimpleName());
    }

    /**
     * Generates a new chunk at the specified chunk position if it doesn't already exist in the ChunkManager.
     * The generated chunk is populated using the configured {@link TerrainGenerator}, its state is set to
     * {@link ChunkState#GENERATED}, and it is then added to the {@link ChunkManager}.
     *
     * @param chunkPos The position of the chunk to generate. Must not be null.
     * @return The generated {@link Chunk}, or the existing chunk if one was already loaded at that position,
     *         or {@code null} if the chunk position is already managed but the chunk is null (should not happen).
     */
    public Chunk generateChunk(ChunkPos chunkPos) {
        Validate.notNull(chunkPos, "ChunkPos cannot be null");

        if (chunkManager.containsChunk(chunkPos)) {
            LOGGER.debug("Chunk at {} already managed. Returning existing chunk.", chunkPos);
            return chunkManager.getChunk(chunkPos);
        }

        LOGGER.debug("Generating new chunk at {}", chunkPos);
        Chunk newChunk = new Chunk(chunkPos);

        terrainGenerator.generateChunkData(newChunk);
        newChunk.setState(ChunkState.GENERATED);
        // TODO: Set neighbors after surrounding chunks are generated/loaded. For now, this is handled by ChunkManager if needed.

        chunkManager.addChunk(newChunk);
        LOGGER.debug("Generated and added chunk {} to ChunkManager. Current loaded chunks: {}", chunkPos, chunkManager.getLoadedChunkCount());

        return newChunk;
    }

    /**
     * Gets the terrain generator used by this chunk generator.
     * @return The {@link TerrainGenerator}.
     */
    public TerrainGenerator getTerrainGenerator() {
        return terrainGenerator;
    }
}
