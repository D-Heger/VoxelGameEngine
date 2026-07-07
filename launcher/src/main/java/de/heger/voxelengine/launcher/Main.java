package de.heger.voxelengine.launcher;

import de.heger.voxelengine.core.config.Config;
import de.heger.voxelengine.core.config.ConfigManager;
import de.heger.voxelengine.core.logging.LoggerFacade;

/**
 * The application entry point.
 *
 * <p>Its job is small: load the {@link Config} from disk, build a
 * {@link GameLoop} configured with those settings, and run it. Any exception
 * that escapes the loop is logged and turned into a non-zero exit code so
 * failures are visible.</p>
 */
public class Main {

    private static final LoggerFacade LOGGER = LoggerFacade.get(Main.class);

    public static void main(String[] args) {
        try {
            LOGGER.info("Starting VoxelGameEngine Launcher...");
            Config config = ConfigManager.load();
            LOGGER.info("Loaded configuration: title={}, width={}, height={}, vsync={}, fullscreen={}, viewDistance={}",
                        config.getWindowTitle(), config.getWidth(), config.getHeight(), config.isVsync(), config.isFullscreen(), config.getViewDistance());
            GameLoop gameLoop = new GameLoop(
                    config.getWindowTitle(), config.getWidth(), config.getHeight(), config.isVsync(), config.isFullscreen(), config.getViewDistance());
            gameLoop.run();
            LOGGER.info("Launcher finished cleanly.");
        } catch (Exception e) {
            LOGGER.error("An unexpected error occurred in the launcher:", e);
            System.exit(-1);
        }
    }
}
