package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3i;

import java.util.Objects;

/**
 * Represents the coordinates of a chunk in the world grid.
 * Chunks are typically 16x16x16 blocks.
 * Instances of this class are immutable and suitable for use as keys in maps.
 */
public final class ChunkPos {

    public final int x;
    public final int y;
    public final int z;

    /**
     * Creates a new ChunkPos instance.
     *
     * @param x The x-coordinate of the chunk.
     * @param y The y-coordinate of the chunk.
     * @param z The z-coordinate of the chunk.
     */
    public ChunkPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a ChunkPos from a Vec3i containing chunk coordinates.
     *
     * @param chunkCoords The vector containing the chunk coordinates.
     * @return A new ChunkPos instance.
     */
    public static ChunkPos fromVec3i(Vec3i chunkCoords) {
        return new ChunkPos(chunkCoords.x, chunkCoords.y, chunkCoords.z);
    }

    /**
     * Converts this ChunkPos to a Vec3i.
     *
     * @return A Vec3i representing the chunk coordinates.
     */
    public Vec3i toVec3i() {
        return new Vec3i(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkPos chunkPos = (ChunkPos) o;
        return x == chunkPos.x && y == chunkPos.y && z == chunkPos.z;
    }

    @Override
    public int hashCode() {
        // Uses a common hashing strategy for 3D integer coordinates
        // See: https://stackoverflow.com/questions/919612/mapping-two-integers-to-one-in-a-unique-and-deterministic-way
        // Adapting Szudzik's function pairing for 3 integers
        int xyHash = x >= y ? x * x + x + y : y * y + x;
        return xyHash >= z ? xyHash * xyHash + xyHash + z : z * z + xyHash;
        // Alternative simple hash:
        // return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "ChunkPos(" + x + ", " + y + ", " + z + ")";
    }
}
