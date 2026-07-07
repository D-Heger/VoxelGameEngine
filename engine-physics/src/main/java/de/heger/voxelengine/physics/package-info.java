/**
 * Movement, gravity, and collision against the voxel grid.
 *
 * <p>The physics here is deliberately modest &mdash; enough to make walking,
 * falling, and bumping into blocks feel right, not a full rigid-body engine.
 * {@link de.heger.voxelengine.physics.PhysicsService} advances an entity each
 * step with simple Euler integration and gravity, while
 * {@link de.heger.voxelengine.physics.CollisionResolver} keeps that entity from
 * tunnelling into solid blocks by resolving its bounding box against the world
 * one axis at a time.</p>
 *
 * <p>See the {@link de.heger.voxelengine.physics.raycast} sub-package for the
 * ray-versus-voxel traversal used to figure out which block the player is
 * pointing at.</p>
 */
package de.heger.voxelengine.physics;
