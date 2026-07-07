/**
 * The program's entry point and main loop.
 *
 * <p>This is where execution begins. {@link de.heger.voxelengine.launcher.Main}
 * reads the configuration, constructs the game, and starts it.
 * {@link de.heger.voxelengine.launcher.GameLoop} is the beating heart of the
 * running game: it opens the window, wires the world, renderer, physics, player,
 * and UI together, and then runs the fixed-update-and-render cycle every frame
 * until the player quits.</p>
 *
 * <p>If you want to understand how everything connects, read {@code GameLoop}
 * first &mdash; it is the one place that touches every other module.</p>
 */
package de.heger.voxelengine.launcher;
