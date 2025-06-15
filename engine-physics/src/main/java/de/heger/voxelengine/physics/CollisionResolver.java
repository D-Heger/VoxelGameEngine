package de.heger.voxelengine.physics;

import de.heger.voxelengine.core.math.AABB;
import de.heger.voxelengine.world.chunk.*;
import de.heger.voxelengine.world.entity.Entity;
import org.joml.Vector3f;
import de.heger.voxelengine.core.math.Vec3i;

/**
 * Performs very simple axis-aligned collision detection/resolution between an
 * {@link Entity}'s AABB and solid world blocks.
 *
 * <p>Algorithm: sweep the entity along each axis separately (X → Y → Z). After
 * applying velocity * dt on a single axis we check every block intersecting the
 * new AABB; if any are solid we clamp movement on that axis to the nearest
 * block boundary.</p>
 */
public class CollisionResolver {

    private final ChunkManager chunkManager;

    public CollisionResolver(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    /**
     * Resolves collisions for the given entity and desired movement. This method
     * directly mutates the entity's position & velocity to ensure it does not
     * interpenetrate solid blocks.
     */
    public void resolve(Entity entity, Vector3f desiredMovement) {
        // Work on copy because we change per axis
        Vector3f moveRemaining = new Vector3f(desiredMovement);

        // --- X axis ---
        moveAlongAxis(entity, moveRemaining, 0);
        // --- Y axis ---
        moveAlongAxis(entity, moveRemaining, 1);
        // --- Z axis ---
        moveAlongAxis(entity, moveRemaining, 2);
    }

    private void moveAlongAxis(Entity entity, Vector3f move, int axis) {
        float delta = (axis == 0 ? move.x : (axis == 1 ? move.y : move.z));
        if (delta == 0) return;

        // Apply tentative movement on axis
        if (axis == 0) entity.getPosition().x += delta;
        else if (axis == 1) entity.getPosition().y += delta;
        else entity.getPosition().z += delta;

        // Build new AABB
        AABB aabb = entity.getAABB();

        // Determine range of blocks to test
        int minX = (int) Math.floor(aabb.minX);
        int maxX = (int) Math.floor(aabb.maxX - 1e-4f);
        int minY = (int) Math.floor(aabb.minY);
        int maxY = (int) Math.floor(aabb.maxY - 1e-4f);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxZ = (int) Math.floor(aabb.maxZ - 1e-4f);

        boolean collided = false;
        outer:
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (isBlockSolid(x, y, z)) {
                        collided = true;
                        break outer;
                    }
                }
            }
        }

        if (collided) {
            // Clamp movement back to block boundary
            if (delta > 0) {
                // Moving positive direction – place entity just before solid block
                float blockBoundary = axis == 0 ? (float) (Math.floor(aabb.maxX) )
                        : axis == 1 ? (float) (Math.floor(aabb.maxY))
                        : (float) (Math.floor(aabb.maxZ));
                switch (axis) {
                    case 0:
                        entity.getPosition().x = blockBoundary - entity.getWidth() / 2f - 1e-3f;
                        break;
                    case 1:
                        entity.getPosition().y = blockBoundary - entity.getHeight() - 1e-3f;
                        break;
                    case 2:
                        entity.getPosition().z = blockBoundary - entity.getDepth() / 2f - 1e-3f;
                        break;
                }
            } else {
                float blockBoundary = axis == 0 ? (float) (Math.floor(aabb.minX) + 1)
                        : axis == 1 ? (float) (Math.floor(aabb.minY) + 1)
                        : (float) (Math.floor(aabb.minZ) + 1);
                switch (axis) {
                    case 0:
                        entity.getPosition().x = blockBoundary + entity.getWidth() / 2f + 1e-3f;
                        break;
                    case 1:
                        entity.getPosition().y = blockBoundary + 1e-3f;
                        break;
                    case 2:
                        entity.getPosition().z = blockBoundary + entity.getDepth() / 2f + 1e-3f;
                        break;
                }
            }
            // Stop velocity along axis
            if (axis == 0) entity.getVelocity().x = 0;
            else if (axis == 1) entity.getVelocity().y = 0;
            else entity.getVelocity().z = 0;
        } else {
            // No collision -> consume move
            if (axis == 0) move.x = 0;
            else if (axis == 1) move.y = 0;
            else move.z = 0;
        }
    }

    private boolean isBlockSolid(int worldX, int worldY, int worldZ) {
        Vec3i world = new Vec3i(worldX, worldY, worldZ);
        ChunkPos chunkPos = CoordinateUtils.worldToChunkCoords(world);
        Chunk chunk = chunkManager.getChunk(chunkPos);
        if (chunk == null) return false; // treat unloaded chunks as air
        Vec3i local = CoordinateUtils.worldToLocalCoords(world);
        return chunk.isSolid(local.x, local.y, local.z);
    }
} 