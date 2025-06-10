package de.heger.voxelengine.renderer.management;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.culling.AABB;
import de.heger.voxelengine.renderer.mesh.ChunkMesh;
import de.heger.voxelengine.renderer.mesh.ChunkMeshBuilder;
import de.heger.voxelengine.renderer.mesh.MeshData;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.ChunkMeshState;
import de.heger.voxelengine.world.chunk.ChunkPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages the lifecycle of chunk meshes, including asynchronous building,
 * caching, and cleanup.
 */
public class ChunkMeshManager {
    private static final LoggerFacade logger = LoggerFacade.get(ChunkMeshManager.class);

    private static final int MESH_BUILDER_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    private final Map<ChunkPos, Map<String, ChunkMesh>> activeChunkMeshes = new ConcurrentHashMap<>();
    private final Map<ChunkPos, AABB> chunkAABBCache = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Future<?>> pendingMeshTasks = new ConcurrentHashMap<>();

    private final ExecutorService meshBuilderExecutor;
    private final BlockingQueue<CompletedMeshData> completedMeshDataQueue;

    private final ChunkManager chunkManager;
    private final BlockRegistry blockRegistry;

    /**
     * Helper class to store result from mesh building threads.
     */
    private static class CompletedMeshData {
        final ChunkPos chunkPos;
        final Map<String, MeshData> meshDataMap;
        final boolean isEmpty;

        CompletedMeshData(ChunkPos chunkPos, Map<String, MeshData> meshDataMap) {
            this.chunkPos = chunkPos;
            this.meshDataMap = meshDataMap;
            boolean trulyEmpty = true;
            if (meshDataMap != null) {
                for (MeshData md : meshDataMap.values()) {
                    if (!md.isEmpty()) {
                        trulyEmpty = false;
                        break;
                    }
                }
            }
            this.isEmpty = trulyEmpty;
        }

        /**
         * Releases all buffers from the contained MeshData objects back to the pool.
         */
        void cleanup() {
            if (meshDataMap != null) {
                for (MeshData md : meshDataMap.values()) {
                    md.cleanup();
                }
            }
        }
    }

    public ChunkMeshManager(ChunkManager chunkManager, BlockRegistry blockRegistry) {
        this.chunkManager = chunkManager;
        this.blockRegistry = blockRegistry;
        this.meshBuilderExecutor = Executors.newFixedThreadPool(MESH_BUILDER_THREADS, r -> {
            Thread t = new Thread(r);
            t.setName("MeshBuilderThread-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        this.completedMeshDataQueue = new LinkedBlockingQueue<>();
    }

    /**
     * Processes mesh data that has been completed by background worker threads.
     * This should be called once per frame on the main thread.
     */
    public void processCompletedMeshData() {
        CompletedMeshData completedData;
        while ((completedData = completedMeshDataQueue.poll()) != null) {
            try {
                ChunkPos chunkPos = completedData.chunkPos;
                Chunk chunk = chunkManager.getChunk(chunkPos);

                if (chunk == null) {
                    logger.warn("Completed mesh data for chunk {} but chunk is no longer loaded. Discarding.", chunkPos);
                    continue;
                }

                synchronized (chunk) {
                    if (chunk.getMeshState() == ChunkMeshState.NEEDS_REBUILD) {
                        logger.debug("Chunk {} is marked NEEDS_REBUILD. Discarding completed mesh data as it's stale.",
                                chunkPos);
                        continue;
                    }

                    cleanupMeshesForChunk(chunkPos); // Clean up any old GL meshes

                    if (completedData.isEmpty) {
                        chunk.setMeshState(ChunkMeshState.EMPTY);
                        logger.debug("Processed completed EMPTY mesh data for chunk {}. State set to EMPTY.", chunkPos);
                        continue;
                    }

                    chunk.setMeshState(ChunkMeshState.BUILDING);
                    Map<String, ChunkMesh> newMeshesForChunk = new HashMap<>();
                    if (completedData.meshDataMap != null) {
                        for (Map.Entry<String, MeshData> entry : completedData.meshDataMap.entrySet()) {
                            MeshData md = entry.getValue();
                            if (!md.isEmpty()) {
                                ChunkMesh newMesh = new ChunkMesh(md.getVertexBuffer(), md.getIndexBuffer());
                                if (!newMesh.isEmpty()) {
                                    newMeshesForChunk.put(entry.getKey(), newMesh);
                                }
                            }
                        }
                    }

                    if (!newMeshesForChunk.isEmpty()) {
                        activeChunkMeshes.put(chunkPos, newMeshesForChunk);
                        chunk.setMeshState(ChunkMeshState.UP_TO_DATE);
                        logger.debug("Created {} new submeshes for chunk {}. State: UP_TO_DATE", newMeshesForChunk.size(),
                                chunkPos);
                    } else {
                        chunk.setMeshState(ChunkMeshState.EMPTY);
                        logger.debug("No renderable geometry created for chunk {} after mesh data processing. State: EMPTY",
                                chunkPos);
                    }
                }
            } finally {
                // Ensure buffers are always released back to the pool
                if (completedData != null) {
                    completedData.cleanup();
                }
            }
        }
    }

    /**
     * Checks the state of a chunk and submits a mesh build task if necessary.
     * 
     * @param chunk The chunk to check.
     */
    public void ensureMeshForChunk(Chunk chunk) {
        if (chunk == null)
            return;

        ChunkPos chunkPos = chunk.getPosition();

        ChunkMeshState currentMeshState = chunk.getMeshState();
        Map<String, ChunkMesh> meshesForChunk = activeChunkMeshes.get(chunkPos);

        boolean needsRebuild = currentMeshState == ChunkMeshState.NEEDS_REBUILD;
        boolean isUpToDateButMissing = (currentMeshState == ChunkMeshState.UP_TO_DATE
                || currentMeshState == ChunkMeshState.EMPTY)
                && (meshesForChunk == null || meshesForChunk.isEmpty())
                && currentMeshState != ChunkMeshState.EMPTY;

        if ((needsRebuild || isUpToDateButMissing) && !pendingMeshTasks.containsKey(chunkPos)) {
            if (meshesForChunk != null) {
                cleanupMeshesForChunk(chunkPos);
            }

            logger.debug("Submitting mesh data generation task for chunk {} (State: {}, MissingMeshes: {})...",
                    chunkPos, currentMeshState, isUpToDateButMissing);
            chunk.setMeshState(ChunkMeshState.BUILDING_DATA);

            Future<?> task = meshBuilderExecutor.submit(() -> {
                try {
                    // Also pre-compute AABB in background thread as a safety measure
                    // This ensures AABB is available even if the main thread computation was
                    // skipped
                    AABB chunkAABB = AABB.fromChunk(chunk);
                    chunkAABBCache.put(chunk.getPosition(), chunkAABB);

                    Map<String, MeshData> generatedData = ChunkMeshBuilder.generateMeshDataByTexture(chunk,
                            chunkManager, blockRegistry);
                    completedMeshDataQueue.put(new CompletedMeshData(chunk.getPosition(), generatedData));
                } catch (InterruptedException e) {
                    logger.warn("Mesh building thread for chunk {} interrupted.", chunk.getPosition());
                    Thread.currentThread().interrupt();
                    synchronized (chunk) {
                        chunk.setMeshState(ChunkMeshState.NEEDS_REBUILD);
                    }
                } catch (Exception e) {
                    logger.error("Error building mesh data for chunk {}", chunk.getPosition(), e);
                    synchronized (chunk) {
                        chunk.setMeshState(ChunkMeshState.NEEDS_REBUILD);
                    }
                } finally {
                    pendingMeshTasks.remove(chunk.getPosition());
                }
            });
            pendingMeshTasks.put(chunkPos, task);
        }
    }

    /**
     * Evicts meshes and other cached data for chunks that are no longer loaded or
     * need a rebuild.
     */
    public void evictStaleMeshes() {
        activeChunkMeshes.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            Chunk chunk = chunkManager.getChunk(pos);

            boolean shouldEvict = (chunk == null) || (chunk.getMeshState() == ChunkMeshState.NEEDS_REBUILD);

            if (shouldEvict) {
                logger.debug("Evicting meshes for chunk {}.", pos);
                cleanupMeshesForChunk(pos);
                chunkAABBCache.remove(pos);

                Future<?> pendingTask = pendingMeshTasks.remove(pos);
                if (pendingTask != null && !pendingTask.isDone()) {
                    pendingTask.cancel(true);
                    logger.debug("Cancelled pending mesh build task for evicted chunk {}.", pos);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Retrieves the map of active meshes for a given chunk.
     * 
     * @param chunkPos The position of the chunk.
     * @return A map of texture names to {@link ChunkMesh} objects, or null if no
     *         meshes exist.
     */
    public Map<String, ChunkMesh> getMeshesForChunk(ChunkPos chunkPos) {
        return activeChunkMeshes.get(chunkPos);
    }

    /**
     * Gets the AABB for a chunk from the cache. The AABB should have been
     * pre-computed
     * when the chunk was processed by ensureMeshForChunk().
     * 
     * @param chunk The chunk.
     * @return The {@link AABB} for the chunk, or a fallback AABB if not found in
     *         cache.
     */
    public AABB getAABBForChunk(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null when getting AABB.");
        }

        ChunkPos chunkPos = chunk.getPosition();
        AABB cachedAABB = chunkAABBCache.get(chunkPos);

        if (cachedAABB == null) {
            // Fallback: compute AABB if not found in cache (should be rare)
            // This can happen if the chunk was just loaded and ensureMeshForChunk hasn't
            // been called yet
            logger.warn("AABB not found in cache for chunk {}, computing fallback.", chunkPos);
            cachedAABB = AABB.fromChunk(chunk);
            chunkAABBCache.put(chunkPos, cachedAABB);
        }

        return cachedAABB;
    }

    /**
     * Releases all renderer-specific resources associated with a chunk.
     * 
     * @param chunkPos The position of the chunk whose resources are to be released.
     */
    public void releaseChunkResources(ChunkPos chunkPos) {
        cleanupMeshesForChunk(chunkPos);
        chunkAABBCache.remove(chunkPos);
        logger.debug("Released resources for chunk {}.", chunkPos);
    }

    private void cleanupMeshesForChunk(ChunkPos chunkPos) {
        Map<String, ChunkMesh> meshes = activeChunkMeshes.remove(chunkPos);
        if (meshes != null) {
            logger.debug("Cleaning up {} sub-meshes for chunk {}.", meshes.size(), chunkPos);
            for (ChunkMesh mesh : meshes.values()) {
                mesh.cleanup();
            }
        }
    }

    /**
     * @return The total number of active sub-meshes across all chunks.
     */
    public int getActiveMeshCount() {
        return activeChunkMeshes.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Shuts down the mesh builder executor and cleans up all cached meshes.
     */
    public void cleanup() {
        logger.info("Cleaning up ChunkMeshManager resources...");
        shutdownExecutor();
        for (ChunkPos pos : activeChunkMeshes.keySet()) {
            cleanupMeshesForChunk(pos);
        }
        activeChunkMeshes.clear();
        chunkAABBCache.clear();
        logger.info("ChunkMeshManager cleanup complete.");
    }

    private void shutdownExecutor() {
        if (meshBuilderExecutor != null) {
            logger.debug("Shutting down mesh builder executor...");
            meshBuilderExecutor.shutdown();
            try {
                if (!meshBuilderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    meshBuilderExecutor.shutdownNow();
                    if (!meshBuilderExecutor.awaitTermination(5, TimeUnit.SECONDS))
                        logger.error("Mesh builder executor did not terminate.");
                }
            } catch (InterruptedException ie) {
                meshBuilderExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.debug("Mesh builder executor shut down.");
        }
    }

    /**
     * Pre-computes and caches the AABB for a chunk to avoid computation during
     * rendering.
     * 
     * @param chunks The chunks for which to pre-compute the AABB.
     */
    public void precomputeAABBForChunk(Collection<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (chunk != null && !chunkAABBCache.containsKey(chunk.getPosition())) {
                AABB aabb = AABB.fromChunk(chunk);
                chunkAABBCache.put(chunk.getPosition(), aabb);
                logger.debug("Pre-computed AABB for chunk {}", chunk.getPosition());
            }
        }
    }
}