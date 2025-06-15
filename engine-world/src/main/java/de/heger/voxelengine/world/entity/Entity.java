package de.heger.voxelengine.world.entity;

import de.heger.voxelengine.core.math.AABB;
import org.joml.Vector3f;

/**
 * Base class for any object that has a physical presence in the voxel world.
 * <p>
 * The {@code Entity} stores its fundamental physical attributes (position,
 * velocity, orientation) and provides a lightweight bounding volume based on
 * an axis-aligned bounding box ({@link AABB}). Complex behaviour such as input
 * handling, physics integration, and AI should be implemented in higher-level
 * systems or subclasses (e.g., {@code Player}, mobs).
 * </p>
 */
public class Entity {

    /** Current world-space position (block units). */
    protected final Vector3f position;

    /** Current linear velocity in blocks per second. */
    protected final Vector3f velocity;

    /** Orientation expressed as Euler angles: yaw (Y), pitch (X), roll (Z) in degrees. */
    protected final Vector3f orientation;

    private final float halfWidth;
    private final float halfHeight;
    private final float halfDepth;

    /**
     * Creates a new {@code Entity} at the given position with the specified hit-box size.
     *
     * @param spawnPosition initial position in world coordinates.
     * @param width         bounding box width (X extent).
     * @param height        bounding box height (Y extent).
     * @param depth         bounding box depth (Z extent).
     */
    public Entity(Vector3f spawnPosition, float width, float height, float depth) {
        if (spawnPosition == null) {
            throw new IllegalArgumentException("spawnPosition must not be null");
        }
        this.position = new Vector3f(spawnPosition);
        this.velocity = new Vector3f();
        this.orientation = new Vector3f();

        this.halfWidth = width * 0.5f;
        this.halfHeight = height;       // we assume origin at feet (like Minecraft).
        this.halfDepth = depth * 0.5f;
    }

    // ----------------------------------------------------
    // Accessors
    // ----------------------------------------------------

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getVelocity() {
        return velocity;
    }

    public Vector3f getOrientation() {
        return orientation;
    }

    public float getWidth() {
        return halfWidth * 2f;
    }

    public float getHeight() {
        return halfHeight;
    }

    public float getDepth() {
        return halfDepth * 2f;
    }

    /**
     * Returns a freshly computed axis-aligned bounding box for this entity
     * in world coordinates. The bounding box is defined with the entity's
     * feet at {@code position.y} (Y-up world).
     */
    public AABB getAABB() {
        float minX = position.x - halfWidth;
        float minY = position.y;              // feet
        float minZ = position.z - halfDepth;
        float maxX = position.x + halfWidth;
        float maxY = position.y + halfHeight; // top
        float maxZ = position.z + halfDepth;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Convenience method to set position */
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    // ----------------------------------------------------
    // Basic update helpers (physics integration happens elsewhere)
    // ----------------------------------------------------

    /**
     * Simple Euler integration: p += v * dt
     */
    public void integrate(float deltaSec) {
        position.fma(deltaSec, velocity); // JOML util: pos += vel * dt
    }
} 