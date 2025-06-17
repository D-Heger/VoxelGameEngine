package de.heger.voxelengine.physics.raycast;

import de.heger.voxelengine.core.math.Vec3f;
import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.core.utils.Validate;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.CoordinateUtils;
import de.heger.voxelengine.world.chunk.Direction;

/**
 * Utility class containing a 3-D Digital Differential Analyzer (DDA) ray-casting
 * implementation (a.k.a Amanatides & Woo) for traversing the discrete block
 * grid. The algorithm returns the first solid block hit along the ray or
 * {@code null} if none is found within the specified max distance.
 *
 * <p>Unless specified otherwise, all coordinates are expressed in world space
 * where integer values address block positions and fractional parts represent
 * positions inside the block.</p>
 */
public final class Raycaster {

    private Raycaster() {
    }

    /**
     * Casts a ray through the voxel world and returns the first solid block
     * encountered.
     *
     * @param origin      the ray origin in world coordinates (x/y/z in blocks)
     * @param direction   the (non-zero) direction vector – will be normalised
     *                    internally
     * @param maxDistance the maximum distance (in blocks) the ray may travel
     * @return the {@link RaycastResult} or {@code null} if no solid block was
     *         hit within {@code maxDistance}
     */
    public static RaycastResult raycast(Vec3f origin, Vec3f direction, float maxDistance) {
        Validate.notNull(origin, "origin cannot be null");
        Validate.notNull(direction, "direction cannot be null");
        if (maxDistance <= 0) {
            throw new IllegalArgumentException("maxDistance must be > 0");
        }

        // Normalise direction (copy because Vec3f is immutable, create new instance)
        Vec3f dir = new Vec3f(direction.x, direction.y, direction.z);
        if (dir.lengthSquared() == 0) {
            throw new IllegalArgumentException("direction must not be zero vector");
        }
        dir = dir.normalize();

        // Current block coordinates (integer grid cell)
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        // Direction steps (+1, 0, -1)
        int stepX = dir.x > 0 ? 1 : (dir.x < 0 ? -1 : 0);
        int stepY = dir.y > 0 ? 1 : (dir.y < 0 ? -1 : 0);
        int stepZ = dir.z > 0 ? 1 : (dir.z < 0 ? -1 : 0);

        // tMax = distance along ray until first voxel boundary
        float tMaxX = stepX != 0 ?
                (stepX > 0 ? (float) ((x + 1) - origin.x) / dir.x
                            : (float) (origin.x - x) / -dir.x)
                : Float.POSITIVE_INFINITY;
        float tMaxY = stepY != 0 ?
                (stepY > 0 ? (float) ((y + 1) - origin.y) / dir.y
                            : (float) (origin.y - y) / -dir.y)
                : Float.POSITIVE_INFINITY;
        float tMaxZ = stepZ != 0 ?
                (stepZ > 0 ? (float) ((z + 1) - origin.z) / dir.z
                            : (float) (origin.z - z) / -dir.z)
                : Float.POSITIVE_INFINITY;

        // tDelta = distance between subsequent voxel boundary crossings
        float tDeltaX = stepX != 0 ? Math.abs(1f / dir.x) : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? Math.abs(1f / dir.y) : Float.POSITIVE_INFINITY;
        float tDeltaZ = stepZ != 0 ? Math.abs(1f / dir.z) : Float.POSITIVE_INFINITY;

        // Iterate through blocks until maxDistance reached
        Direction face = null; // face we entered current block through
        float travelled = 0f;
        ChunkManager cm = ChunkManager.getInstance();
        BlockRegistry br = BlockRegistry.getInstance();

        while (travelled <= maxDistance) {
            // -----------------------------------------------------------------
            // Check if current block is solid – only if the chunk is loaded.
            // Unloaded chunks are treated as air so the ray continues.
            // -----------------------------------------------------------------
            Vec3i worldPos = new Vec3i(x, y, z);
            Chunk chunk = cm.getChunk(CoordinateUtils.worldToChunkCoords(worldPos));
            if (chunk != null) {
                // Local coordinates inside chunk
                Vec3i local = CoordinateUtils.worldToLocalCoords(worldPos);
                // Fast path: check if block id != AIR
                short id = chunk.getBlock(local);
                if (id != BlockRegistry.AIR.getId()) {
                    var props = br.getBlock(id);
                    if (props != null && props.isSolid()) {
                        // Hit!
                        return new RaycastResult(worldPos, face != null ? face : Direction.UP, travelled);
                    }
                }
            }

            // -----------------------------------------------------------------
            // Advance to next voxel boundary
            // -----------------------------------------------------------------
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    // step in X
                    x += stepX;
                    travelled = tMaxX;
                    tMaxX += tDeltaX;
                    face = stepX == 1 ? Direction.WEST : Direction.EAST;
                } else {
                    // step in Z
                    z += stepZ;
                    travelled = tMaxZ;
                    tMaxZ += tDeltaZ;
                    face = stepZ == 1 ? Direction.NORTH : Direction.SOUTH;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    // step in Y
                    y += stepY;
                    travelled = tMaxY;
                    tMaxY += tDeltaY;
                    face = stepY == 1 ? Direction.DOWN : Direction.UP;
                } else {
                    // step in Z
                    z += stepZ;
                    travelled = tMaxZ;
                    tMaxZ += tDeltaZ;
                    face = stepZ == 1 ? Direction.NORTH : Direction.SOUTH;
                }
            }
        }
        return null; // No hit within maxDistance
    }
} 