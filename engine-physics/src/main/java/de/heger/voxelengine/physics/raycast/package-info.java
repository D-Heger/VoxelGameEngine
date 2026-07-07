/**
 * Casting a ray through the block grid to find what it hits.
 *
 * <p>When the player looks at the world and wants to break or place a block, the
 * engine needs to know which block their crosshair is over.
 * {@link de.heger.voxelengine.physics.raycast.Raycaster} walks the ray voxel by
 * voxel using the classic Amanatides &amp; Woo grid-traversal algorithm, and
 * {@link de.heger.voxelengine.physics.raycast.RaycastResult} reports what was
 * struck: the block hit, the exact point, and the face normal (so a newly placed
 * block knows which side to sit on).</p>
 */
package de.heger.voxelengine.physics.raycast;
