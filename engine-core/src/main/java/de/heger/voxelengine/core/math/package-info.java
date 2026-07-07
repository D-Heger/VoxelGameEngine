/**
 * Small, dependency-free math building blocks used all over the engine.
 *
 * <p>This is where the humble workhorses live: {@link de.heger.voxelengine.core.math.Vec3i}
 * for discrete block and chunk coordinates, {@link de.heger.voxelengine.core.math.Vec3f}
 * for continuous positions and directions, and
 * {@link de.heger.voxelengine.core.math.AABB} for the axis-aligned boxes that
 * physics and culling lean on. {@link de.heger.voxelengine.core.math.JomlUtils}
 * bridges these types to JOML, the linear-algebra library the renderer speaks.</p>
 *
 * <p>Everything here is intentionally free of higher-level concepts so it can be
 * shared by rendering, physics, and world logic without dragging in dependencies
 * or creating cycles.</p>
 */
package de.heger.voxelengine.core.math;
