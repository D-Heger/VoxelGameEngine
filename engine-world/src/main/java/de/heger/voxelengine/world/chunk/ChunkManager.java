package de.heger.voxelengine.world.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import de.heger.voxelengine.core.utils.Validate;

/**
 * Manages loaded chunks by their positions in the world.
 * <p>
 * This class provides thread-safe access to add, retrieve, and remove chunks.
 * It uses a singleton pattern for global access and an internal FastUtil map
 * to store chunks keyed by their ChunkPos.
 * </p>
 */
public class ChunkManager {
    /** Singleton instance of the chunk manager. */
    private static final ChunkManager INSTANCE = new ChunkManager();

    /** Internal storage for chunks. */
    private final Object2ObjectOpenHashMap<ChunkPos, Chunk> chunks = new Object2ObjectOpenHashMap<>();

    /**
     * Private constructor for singleton pattern.
     */
    private ChunkManager() {
    }

    /**
     * Returns the singleton instance of the ChunkManager.
     *
     * @return the global ChunkManager instance
     */
    public static ChunkManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a chunk to the storage. If a chunk at the same position already exists,
     * it will be replaced.
     *
     * @param chunk the chunk to add (must not be null)
     * @throws NullPointerException if chunk is null
     */
    public synchronized void addChunk(Chunk chunk) {
        Validate.notNull(chunk, "Chunk cannot be null");
        chunks.put(chunk.getPosition(), chunk);
    }

    /**
     * Retrieves the chunk at the given position.
     *
     * @param position the chunk position (must not be null)
     * @return the chunk, or null if no chunk is loaded at that position
     * @throws NullPointerException if position is null
     */
    public synchronized Chunk getChunk(ChunkPos position) {
        Validate.notNull(position, "ChunkPos cannot be null");
        return chunks.get(position);
    }

    /**
     * Removes the chunk at the specified position.
     *
     * @param position the chunk position (must not be null)
     * @throws NullPointerException if position is null
     */
    public synchronized void removeChunk(ChunkPos position) {
        Validate.notNull(position, "ChunkPos cannot be null");
        chunks.remove(position);
    }

    /**
     * Checks whether a chunk is loaded at the specified position.
     *
     * @param position the chunk position (must not be null)
     * @return true if a chunk exists, false otherwise
     * @throws NullPointerException if position is null
     */
    public synchronized boolean containsChunk(ChunkPos position) {
        Validate.notNull(position, "ChunkPos cannot be null");
        return chunks.containsKey(position);
    }

    /**
     * Returns the number of currently loaded chunks.
     *
     * @return the count of loaded chunks
     */
    public synchronized int getLoadedChunkCount() {
        return chunks.size();
    }

    /**
     * Returns a snapshot of all loaded chunks.
     *
     * @return an unmodifiable collection of all loaded chunks
     */
    public synchronized Collection<Chunk> getAllLoadedChunks() {
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(chunks.values()));
    }
}
