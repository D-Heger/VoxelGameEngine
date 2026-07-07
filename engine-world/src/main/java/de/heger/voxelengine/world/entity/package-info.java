/**
 * Things that live in the world and move around.
 *
 * <p>For now this is just {@link de.heger.voxelengine.world.entity.Entity}: a
 * position, a velocity, and a bounding box. It is intentionally minimal &mdash;
 * the player is built on top of it, and the physics module reads and writes its
 * state each step. As the engine grows, mobs and other movers would extend this
 * same foundation.</p>
 */
package de.heger.voxelengine.world.entity;
