/**
 * Where the engine's pieces become an actual game.
 *
 * <p>The engine modules are deliberately generic; this package is the opinionated
 * glue that makes them play together. {@link de.heger.voxelengine.game.Player}
 * represents the person in the world &mdash; their entity, camera, and current
 * interaction state &mdash; and {@link de.heger.voxelengine.game.PlayerController}
 * translates input into action: moving and looking, applying physics, and
 * breaking or placing blocks based on where the player is aiming.</p>
 */
package de.heger.voxelengine.game;
