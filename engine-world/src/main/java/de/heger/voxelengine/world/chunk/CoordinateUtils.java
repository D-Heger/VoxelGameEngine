package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3f;
import de.heger.voxelengine.core.math.Vec3i;

/**
 * Utility class for converting between different coordinate systems
 * (world coordinates, chunk coordinates, local block coordinates).
 */
public final class CoordinateUtils {

    // Chunk dimensions (assuming 16x16x16 based on chunk_design.md)
    public static final int CHUNK_SIZE_X = 16;
    public static final int CHUNK_SIZE_Y = 16;
    public static final int CHUNK_SIZE_Z = 16;

    // Bit shift equivalents for division/modulo by 16
    private static final int CHUNK_SHIFT_X = 4; // log2(16)
    private static final int CHUNK_SHIFT_Y = 4;
    private static final int CHUNK_SHIFT_Z = 4;

    // Bit mask for modulo 16 (16 - 1 = 15 => 0b1111)
    private static final int CHUNK_MASK_X = CHUNK_SIZE_X - 1;
    private static final int CHUNK_MASK_Y = CHUNK_SIZE_Y - 1;
    private static final int CHUNK_MASK_Z = CHUNK_SIZE_Z - 1;

    private CoordinateUtils() {
        // Private constructor to prevent instantiation
    }

    // --- World Coordinates (int) to Chunk/Local Coordinates --- 

    /**
     * Converts world block coordinates (int) to chunk coordinates.
     *
     * @param worldPos The world coordinates (Vec3i).
     * @return The corresponding chunk coordinates (ChunkPos).
     */
    public static ChunkPos worldToChunkCoords(Vec3i worldPos) {
        // Integer division behaves correctly for negative numbers in Java >= 8
        // equivalent to floor(worldPos.x / 16.0)
        int chunkX = worldPos.x >> CHUNK_SHIFT_X;
        int chunkY = worldPos.y >> CHUNK_SHIFT_Y;
        int chunkZ = worldPos.z >> CHUNK_SHIFT_Z;
        return new ChunkPos(chunkX, chunkY, chunkZ);
    }

    /**
     * Converts world block coordinates (int) to local block coordinates within a chunk (0-15).
     *
     * @param worldPos The world coordinates (Vec3i).
     * @return The local coordinates within the chunk (Vec3i, components 0-15).
     */
    public static Vec3i worldToLocalCoords(Vec3i worldPos) {
        // Modulo using bitwise AND handles negative numbers correctly for power-of-two sizes
        // equivalent to worldPos.x % 16 (but works for negative numbers)
        int localX = worldPos.x & CHUNK_MASK_X;
        int localY = worldPos.y & CHUNK_MASK_Y;
        int localZ = worldPos.z & CHUNK_MASK_Z;
        return new Vec3i(localX, localY, localZ);
    }

    // --- World Coordinates (float) to Chunk/Local Coordinates --- 

    /**
     * Converts world coordinates (float) to chunk coordinates.
     *
     * @param worldPos The world coordinates (Vec3f).
     * @return The corresponding chunk coordinates (ChunkPos).
     */
    public static ChunkPos worldToChunkCoords(Vec3f worldPos) {
        int chunkX = (int) Math.floor(worldPos.x / CHUNK_SIZE_X);
        int chunkY = (int) Math.floor(worldPos.y / CHUNK_SIZE_Y);
        int chunkZ = (int) Math.floor(worldPos.z / CHUNK_SIZE_Z);
        return new ChunkPos(chunkX, chunkY, chunkZ);
    }

    /**
     * Converts world coordinates (float) to local block coordinates within a chunk (0-15).
     * Note: This typically involves flooring the world coordinate first to get the block coordinate.
     *
     * @param worldPos The world coordinates (Vec3f).
     * @return The local coordinates within the chunk (Vec3i, components 0-15).
     */
    public static Vec3i worldToLocalCoords(Vec3f worldPos) {
        int blockX = (int) Math.floor(worldPos.x);
        int blockY = (int) Math.floor(worldPos.y);
        int blockZ = (int) Math.floor(worldPos.z);
        return worldToLocalCoords(new Vec3i(blockX, blockY, blockZ));
    }

    // --- Chunk/Local Coordinates to World Coordinates --- 

    /**
     * Converts chunk coordinates and local block coordinates to world block coordinates.
     *
     * @param chunkPos The coordinates of the chunk.
     * @param localPos The local coordinates within the chunk (0-15).
     * @return The corresponding world coordinates (Vec3i).
     */
    public static Vec3i localToWorldCoords(ChunkPos chunkPos, Vec3i localPos) {
        int worldX = (chunkPos.x << CHUNK_SHIFT_X) + localPos.x;
        int worldY = (chunkPos.y << CHUNK_SHIFT_Y) + localPos.y;
        int worldZ = (chunkPos.z << CHUNK_SHIFT_Z) + localPos.z;
        return new Vec3i(worldX, worldY, worldZ);
    }

    /**
     * Calculates the world coordinates of the origin (min corner) of a given chunk.
     *
     * @param chunkPos The coordinates of the chunk.
     * @return The world coordinates (Vec3i) of the block at local position (0, 0, 0) within that chunk.
     */
    public static Vec3i chunkOriginToWorldCoords(ChunkPos chunkPos) {
        int worldX = chunkPos.x << CHUNK_SHIFT_X;
        int worldY = chunkPos.y << CHUNK_SHIFT_Y;
        int worldZ = chunkPos.z << CHUNK_SHIFT_Z;
        return new Vec3i(worldX, worldY, worldZ);
    }

     /**
     * Calculates the index within a chunk's flat 1D array for given local coordinates.
     * Assumes Y-major order as specified in chunk_design.md: y * W*D + z * W + x
     *
     * @param localX Local x-coordinate (0-15).
     * @param localY Local y-coordinate (0-15).
     * @param localZ Local z-coordinate (0-15).
     * @return The index in the flat array (0-4095).
     * @throws IllegalArgumentException if coordinates are out of bounds (0-15).
     */
    public static int localCoordsToIndex(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= CHUNK_SIZE_X ||
            localY < 0 || localY >= CHUNK_SIZE_Y ||
            localZ < 0 || localZ >= CHUNK_SIZE_Z) {
            throw new IllegalArgumentException("Local coordinates out of bounds [0, " + (CHUNK_SIZE_X - 1) + "]: (" +
                                               localX + ", " + localY + ", " + localZ + ")");
        }
        // index = y * CHUNK_WIDTH * CHUNK_DEPTH + z * CHUNK_WIDTH + x
        return localY * (CHUNK_SIZE_X * CHUNK_SIZE_Z) + localZ * CHUNK_SIZE_X + localX;
    }

    /**
     * Calculates the index within a chunk's flat 1D array for given local coordinates.
     * Assumes Y-major order as specified in chunk_design.md: y * W*D + z * W + x
     *
     * @param localPos The local coordinates (Vec3i, components 0-15).
     * @return The index in the flat array (0-4095).
     * @throws IllegalArgumentException if coordinates are out of bounds (0-15).
     */
    public static int localCoordsToIndex(Vec3i localPos) {
        return localCoordsToIndex(localPos.x, localPos.y, localPos.z);
    }

    /**
     * Calculates the local coordinates (Vec3i) from an index in a chunk's flat 1D array.
     * Assumes Y-major order: y * W*D + z * W + x
     *
     * @param index The index in the flat array (0-4095).
     * @return The local coordinates (Vec3i).
     * @throws IllegalArgumentException if the index is out of bounds [0, 4095].
     */
    public static Vec3i indexToLocalCoords(int index) {
        if (index < 0 || index >= (CHUNK_SIZE_X * CHUNK_SIZE_Y * CHUNK_SIZE_Z)) {
            throw new IllegalArgumentException("Index out of bounds [0, " + 
                                               (CHUNK_SIZE_X * CHUNK_SIZE_Y * CHUNK_SIZE_Z - 1) + "]: " + index);
        }
        int y = index / (CHUNK_SIZE_X * CHUNK_SIZE_Z);
        int remainder = index % (CHUNK_SIZE_X * CHUNK_SIZE_Z);
        int z = remainder / CHUNK_SIZE_X;
        int x = remainder % CHUNK_SIZE_X;
        return new Vec3i(x, y, z);
    }
}
