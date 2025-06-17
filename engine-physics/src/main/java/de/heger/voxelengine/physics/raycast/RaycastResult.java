package de.heger.voxelengine.physics.raycast;

import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.world.chunk.Direction;

/**
 * Represents the result of a block ray-cast through the world.
 * <p>
 * The ray-cast returns the first solid block hit along the tested ray together
 * with the world block coordinates, the face that was intersected and the
 * travelled distance in world units.
 */
public record RaycastResult(Vec3i blockPos, Direction hitFace, float distance) {
} 