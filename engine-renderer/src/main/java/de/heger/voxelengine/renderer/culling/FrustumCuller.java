package de.heger.voxelengine.renderer.culling;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/**
 * Handles frustum culling operations.
 */
public class FrustumCuller {
    private final FrustumIntersection frustumIntersection;

    /**
     * Constructs a FrustumCuller with the given view-projection matrix.
     * @param viewProjectionMatrix The combined view and projection matrix.
     */
    public FrustumCuller(Matrix4f viewProjectionMatrix) {
        if (viewProjectionMatrix == null) {
            throw new IllegalArgumentException("View-projection matrix cannot be null for FrustumCuller.");
        }
        this.frustumIntersection = new FrustumIntersection(viewProjectionMatrix);
    }

    /**
     * Tests if the given AABB intersects with the frustum.
     * @param aabb The Axis-Aligned Bounding Box to test.
     * @return True if the AABB is at least partially inside or intersects the frustum, false otherwise.
     */
    public boolean testAABB(AABB aabb) {
        if (aabb == null) {
            return false; // Or throw an IllegalArgumentException
        }
        return frustumIntersection.testAab(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    /**
     * Updates the frustum planes based on a new view-projection matrix.
     * This might be useful if the camera or projection changes frequently without creating new FrustumCuller instances.
     * @param viewProjectionMatrix The new combined view and projection matrix.
     */
    public void updateViewProjectionMatrix(Matrix4f viewProjectionMatrix) {
        if (viewProjectionMatrix == null) {
            throw new IllegalArgumentException("View-projection matrix cannot be null for update.");
        }
        this.frustumIntersection.set(viewProjectionMatrix);
    }
} 