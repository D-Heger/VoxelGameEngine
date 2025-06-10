package de.heger.voxelengine.renderer.culling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import org.joml.Vector3f;

/**
 * Handles occlusion culling for chunks.
 * This class implements a simple distance-based occlusion culling strategy.
 * It sorts chunks by distance from the camera and identifies potentially occluded chunks.
 */
public class OcclusionCuller {

    private static final LoggerFacade logger = LoggerFacade.get(OcclusionCuller.class);
    
    // Defines which chunks are considered opaque for occlusion culling
    private final Set<ChunkPos> completelyOpaqueChunks = new HashSet<>();
    
    // Maximum distance at which a chunk can occlude another chunk
    private static final float MAX_OCCLUSION_DISTANCE = 128.0f;
    private static final float MAX_OCCLUSION_DISTANCE_SQUARED = MAX_OCCLUSION_DISTANCE * MAX_OCCLUSION_DISTANCE;
    
    // OPTIMIZATION: Reusable collections to reduce allocations
    private final List<ChunkWithDistance> reusableChunkList = new ArrayList<>();
    private final Set<Chunk> reusableVisibleChunks = new HashSet<>();
    private final Set<ChunkPos> reusablePotentialOccluders = new HashSet<>();
    private final List<ChunkWithDistance> chunkWithDistancePool = new ArrayList<>();
    private int poolIndex = 0;
    
    /**
     * Filters the input collection of chunks to remove those that are likely
     * fully occluded by other chunks from the given viewpoint.
     * 
     * @param chunks The chunks to filter (all chunks that are within the frustum).
     * @param cameraPosition The current camera position.
     * @param cameraDirection The camera's view direction vector.
     * @param occludedChunkCounter A consumer to report the number of chunks culled by occlusion.
     * @return A collection of chunks that are likely visible (not occluded).
     */
    public Collection<Chunk> filterOccludedChunks(
            Collection<Chunk> chunks,
            Vector3f cameraPosition,
            Vector3f cameraDirection,
            IntConsumer occludedChunkCounter) {
        
        if (chunks == null || chunks.size() <= 1) {
            if (occludedChunkCounter != null) occludedChunkCounter.accept(0);
            return chunks; // Nothing to cull if there's 0 or 1 chunk
        }
        
        // OPTIMIZATION: Reuse collections instead of creating new ones
        poolIndex = 0; // Reset the pool for this frame
        reusableChunkList.clear();
        reusableVisibleChunks.clear();
        reusablePotentialOccluders.clear();
        
        // Sort chunks by distance from camera (nearest first)
        for (Chunk chunk : chunks) {
            if (chunk == null) continue;
            
            // Get chunk center position
            ChunkPos pos = chunk.getPosition();
            float chunkCenterX = (pos.x * Chunk.SIZE_X) + (Chunk.SIZE_X / 2.0f);
            float chunkCenterY = (pos.y * Chunk.SIZE_Y) + (Chunk.SIZE_Y / 2.0f);
            float chunkCenterZ = (pos.z * Chunk.SIZE_Z) + (Chunk.SIZE_Z / 2.0f);
            
            // OPTIMIZATION: Calculate distance squared to avoid sqrt
            float dx = chunkCenterX - cameraPosition.x;
            float dy = chunkCenterY - cameraPosition.y;
            float dz = chunkCenterZ - cameraPosition.z;
            float distanceSquared = dx*dx + dy*dy + dz*dz;
            
            // Check if this chunk is potentially opaque (full of solid blocks)
            boolean isOpaque = isChunkOpaque(chunk);
            
            reusableChunkList.add(borrowChunkWithDistance(chunk, distanceSquared, isOpaque));
        }
        
        // Sort by distance (closest to camera first)
        Collections.sort(reusableChunkList, DISTANCE_COMPARATOR);
        
        // Apply occlusion culling
        int culledCount = 0; // Counter for occluded chunks
        
        // First pass: identify chunks that are definitely visible and potential occluders
        for (ChunkWithDistance chunkWithDist : reusableChunkList) {
            Chunk chunk = chunkWithDist.chunk;
            ChunkPos pos = chunk.getPosition();
            
            // Check if this chunk is "behind" any occluders from the camera's perspective
            if (isPotentiallyOccluded(pos, cameraPosition, reusablePotentialOccluders, reusableChunkList)) {
                culledCount++; // Increment culled counter
                // Skipping this chunk as it might be occluded
                continue;
            }
            
            // This chunk is visible
            reusableVisibleChunks.add(chunk);
            
            // If this chunk is opaque, add it as a potential occluder
            if (chunkWithDist.isOpaque && chunkWithDist.distanceSquared <= MAX_OCCLUSION_DISTANCE_SQUARED) {
                reusablePotentialOccluders.add(pos);
            }
        }
        
        logger.debug("Occlusion culling: {} chunks visible out of {} total ({} culled)", reusableVisibleChunks.size(), chunks.size(), culledCount);
        if (occludedChunkCounter != null) {
            occludedChunkCounter.accept(culledCount); // Report the count
        }
        return reusableVisibleChunks;
    }
    
    private ChunkWithDistance borrowChunkWithDistance(Chunk chunk, float distanceSquared, boolean isOpaque) {
        if (poolIndex < chunkWithDistancePool.size()) {
            ChunkWithDistance obj = chunkWithDistancePool.get(poolIndex);
            obj.init(chunk, distanceSquared, isOpaque);
            poolIndex++;
            return obj;
        } else {
            ChunkWithDistance obj = new ChunkWithDistance(chunk, distanceSquared, isOpaque);
            chunkWithDistancePool.add(obj);
            poolIndex++;
            return obj;
        }
    }
    
    // OPTIMIZATION: Static comparator to avoid lambda allocation
    private static final Comparator<ChunkWithDistance> DISTANCE_COMPARATOR = 
        Comparator.comparingDouble(c -> c.distanceSquared);
    
    /**
     * Determines if a chunk is likely opaque (filled with solid blocks).
     * This is used to identify chunks that can occlude other chunks.
     * 
     * @param chunk The chunk to check.
     * @return True if the chunk is considered opaque for occlusion culling.
     */
    private boolean isChunkOpaque(Chunk chunk) {
        if (chunk == null) return false;
        
        ChunkPos pos = chunk.getPosition();
        
        // Check if we've already determined this chunk is opaque
        if (completelyOpaqueChunks.contains(pos)) {
            return true;
        }
        
        // For now, we use a simplified approach
        // In a real implementation, we would check if the chunk is mostly solid blocks
        
        // Add to opaque set if it's below certain Y level (underground chunks)
        // This is a heuristic - underground chunks are more likely to be filled
        if (pos.y < 0) {
            completelyOpaqueChunks.add(pos);
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a chunk position is potentially occluded by known occluders.
     * 
     * @param posToCheck The position of the chunk to check.
     * @param cameraPos The camera position.
     * @param occluderPositions Set of chunk positions known to be occluders.
     * @param allChunks All chunks sorted by distance, for reference.
     * @return True if the chunk at posToCheck is potentially occluded.
     */
    private boolean isPotentiallyOccluded(
            ChunkPos posToCheck, 
            Vector3f cameraPos,
            Set<ChunkPos> occluderPositions,
            List<ChunkWithDistance> allChunks) {
        
        if (occluderPositions.isEmpty()) {
            return false; // No occluders yet
        }
        
        // Get chunk center
        float chunkCenterX = (posToCheck.x * Chunk.SIZE_X) + (Chunk.SIZE_X / 2.0f);
        float chunkCenterY = (posToCheck.y * Chunk.SIZE_Y) + (Chunk.SIZE_Y / 2.0f);
        float chunkCenterZ = (posToCheck.z * Chunk.SIZE_Z) + (Chunk.SIZE_Z / 2.0f);
        
        // Direction vector from camera to chunk
        float dirX = chunkCenterX - cameraPos.x;
        float dirY = chunkCenterY - cameraPos.y;
        float dirZ = chunkCenterZ - cameraPos.z;
        
        // OPTIMIZATION: Use distance squared to avoid sqrt
        float distanceSquared = dirX*dirX + dirY*dirY + dirZ*dirZ;
        
        // OPTIMIZATION: Early exit if distance is too small
        if (distanceSquared < 0.001f) {
            return false; // Camera is too close to the chunk
        }
        
        // Normalize
        float invDistance = 1.0f / (float) Math.sqrt(distanceSquared);
        dirX *= invDistance;
        dirY *= invDistance;
        dirZ *= invDistance;
        
        // OPTIMIZATION: Pre-calculate chunk diagonal size once
        final float chunkDiagonal = (float) Math.sqrt(Chunk.SIZE_X*Chunk.SIZE_X + 
                                                      Chunk.SIZE_Y*Chunk.SIZE_Y + 
                                                      Chunk.SIZE_Z*Chunk.SIZE_Z);
        
        // Check if there are occluders along the line of sight
        for (ChunkWithDistance otherChunk : allChunks) {
            ChunkPos otherPos = otherChunk.chunk.getPosition();
            
            // Skip if it's the same chunk or not an occluder
            if (otherPos.equals(posToCheck) || !occluderPositions.contains(otherPos)) {
                continue;
            }
            
            // Skip if the occluder is further from camera than the chunk we're checking
            if (otherChunk.distanceSquared >= distanceSquared) {
                continue;
            }
            
            // Get occluder chunk center
            float occluderCenterX = (otherPos.x * Chunk.SIZE_X) + (Chunk.SIZE_X / 2.0f);
            float occluderCenterY = (otherPos.y * Chunk.SIZE_Y) + (Chunk.SIZE_Y / 2.0f);
            float occluderCenterZ = (otherPos.z * Chunk.SIZE_Z) + (Chunk.SIZE_Z / 2.0f);
            
            // Calculate how far the occluder is from the line of sight
            // This is simplified - we're just checking if the chunk is in roughly the same direction
            float dotProduct = (occluderCenterX - cameraPos.x) * dirX + 
                               (occluderCenterY - cameraPos.y) * dirY + 
                               (occluderCenterZ - cameraPos.z) * dirZ;
            
            if (dotProduct > 0) {  // Occluder is in front of camera
                // Project the occluder position onto the ray
                float projX = cameraPos.x + dirX * dotProduct;
                float projY = cameraPos.y + dirY * dotProduct;
                float projZ = cameraPos.z + dirZ * dotProduct;
                
                // Distance from occluder center to the ray
                float devX = occluderCenterX - projX;
                float devY = occluderCenterY - projY;
                float devZ = occluderCenterZ - projZ;
                float deviationSquared = devX*devX + devY*devY + devZ*devZ;
                
                // If occluder is close enough to the line of sight, consider it occluding
                // We use the chunk diagonal size as a threshold
                if (deviationSquared < chunkDiagonal * chunkDiagonal) {
                    return true;  // This chunk is likely occluded
                }
            }
        }
        
        return false;  // Not occluded
    }
    
    /**
     * Clears the cached information about opaque chunks.
     * Call this when chunks might have changed (e.g., blocks placed/removed).
     */
    public void clearOpaqueCache() {
        completelyOpaqueChunks.clear();
    }
    
    /**
     * Helper class to store a chunk with its distance from camera.
     */
    private static class ChunkWithDistance {
        Chunk chunk;
        float distanceSquared;
        boolean isOpaque;
        
        ChunkWithDistance(Chunk chunk, float distanceSquared, boolean isOpaque) {
            this.init(chunk, distanceSquared, isOpaque);
        }

        void init(Chunk chunk, float distanceSquared, boolean isOpaque) {
            this.chunk = chunk;
            this.distanceSquared = distanceSquared;
            this.isOpaque = isOpaque;
        }
    }
}
