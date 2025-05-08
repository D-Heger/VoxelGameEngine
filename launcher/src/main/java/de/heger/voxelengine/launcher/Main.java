package de.heger.voxelengine.launcher;

import de.heger.voxelengine.core.config.Config;
import de.heger.voxelengine.core.config.ConfigManager;
import de.heger.voxelengine.core.logging.LoggerFacade;

public class Main {

    private static final LoggerFacade LOGGER = LoggerFacade.get(Main.class);

    public static void main(String[] args) {
        try {
            LOGGER.info("Starting VoxelGameEngine Launcher...");
            Config config = ConfigManager.load();
            LOGGER.info("Loaded configuration: title={}, width={}, height={}, vsync={}, fullscreen={}",
                        config.getWindowTitle(), config.getWidth(), config.getHeight(), config.isVsync(), config.isFullscreen());
            GameLoop gameLoop = new GameLoop(
                    config.getWindowTitle(), config.getWidth(), config.getHeight(), config.isVsync(), config.isFullscreen());
            gameLoop.run();
            LOGGER.info("Launcher finished cleanly.");
        } catch (Exception e) {
            LOGGER.error("An unexpected error occurred in the launcher:", e);
            System.exit(-1);
        }
    }
}
