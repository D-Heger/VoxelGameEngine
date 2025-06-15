package de.heger.voxelengine.core.math;

/**
 * Represents an Axis-Aligned Bounding Box (AABB) defined by minimum and maximum coordinates.
 *
 * This class intentionally resides in the engine-core module so it can be shared across
 * rendering, physics, and world logic without introducing cross-module dependencies.
 *
 * Note: Utility methods that require knowledge about higher-level concepts (e.g., converting
 * a {@code Chunk} to an {@code AABB}) MUST live outside this class to avoid cyclic
 * dependencies with modules such as {@code engine-world}.
 */
public class AABB {
    public final float minX, minY, minZ;
    public final float maxX, maxY, maxZ;

    /**
     * Constructs an immutable AABB.
     *
     * @param minX minimum X coordinate
     * @param minY minimum Y coordinate
     * @param minZ minimum Z coordinate
     * @param maxX maximum X coordinate
     * @param maxY maximum Y coordinate
     * @param maxZ maximum Z coordinate
     */
    public AABB(float minX, float minY, float minZ,
                float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Computes the width of the box on the X-axis.
     */
    public float getSizeX() {
        return maxX - minX;
    }

    /**
     * Computes the height of the box on the Y-axis.
     */
    public float getSizeY() {
        return maxY - minY;
    }

    /**
     * Computes the depth of the box on the Z-axis.
     */
    public float getSizeZ() {
        return maxZ - minZ;
    }
} 