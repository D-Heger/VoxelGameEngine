/**
 * Loading and holding the game's startup configuration.
 *
 * <p>{@link de.heger.voxelengine.core.config.Config} is a plain data holder for
 * settings such as window size, title, vsync, and view distance.
 * {@link de.heger.voxelengine.core.config.ConfigManager} reads those settings
 * from a JSON file on disk (falling back to sensible defaults when the file is
 * missing) so the launcher can build the game with the player's preferences.</p>
 */
package de.heger.voxelengine.core.config;
