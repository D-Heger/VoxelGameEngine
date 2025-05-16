package de.heger.voxelengine.renderer.culling;

import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;

/**
 * Represents an Axis-Aligned Bounding Box (AABB).
 */
public class AABB {
    public final float minX, minY, minZ;
    public final float maxX, maxY, maxZ;

    /**
     * Constructs an AABB with the given minimum and maximum coordinates.
     * @param minX Minimum X coordinate.
     * @param minY Minimum Y coordinate.
     * @param minZ Minimum Z coordinate.
     * @param maxX Maximum X coordinate.
     * @param maxY Maximum Y coordinate.
     * @param maxZ Maximum Z coordinate.
     */
    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Creates an AABB for a given chunk.
     * @param chunk The chunk for which to create the AABB.
     * @return The AABB representing the chunk's bounds in world coordinates.
     */
    public static AABB fromChunk(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null when creating AABB.");
        }
        ChunkPos chunkPos = chunk.getPosition();
        float minX = (float) chunkPos.x * Chunk.SIZE_X;
        float minY = (float) chunkPos.y * Chunk.SIZE_Y; // Assuming Chunks can be at different Y levels
        float minZ = (float) chunkPos.z * Chunk.SIZE_Z;
        float maxX = minX + Chunk.SIZE_X;
        float maxY = minY + Chunk.SIZE_Y;
        float maxZ = minZ + Chunk.SIZE_Z;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
} 