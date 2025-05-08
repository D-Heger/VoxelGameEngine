package de.heger.voxelengine.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadDefaultCreatesFileAndReturnsDefaultConfig() throws IOException {
        Path configPath = tempDir.resolve("defaultConfig.json");
        assertFalse(Files.exists(configPath));
        Config config = ConfigManager.load(configPath);
        assertTrue(Files.exists(configPath));
        assertEquals("Voxel Game Engine", config.getWindowTitle());
        assertEquals(1280, config.getWidth());
        assertEquals(720, config.getHeight());
        assertTrue(config.isVsync());
        assertFalse(config.isFullscreen());
    }

    @Test
    void testSaveAndLoadCustomConfig() throws IOException {
        Path configPath = tempDir.resolve("customConfig.json");
        Config custom = new Config();
        custom.setWindowTitle("Test Title");
        custom.setWidth(800);
        custom.setHeight(600);
        custom.setVsync(false);
        custom.setFullscreen(true);

        ConfigManager.save(custom, configPath);
        assertTrue(Files.exists(configPath));

        Config loaded = ConfigManager.load(configPath);
        assertEquals("Test Title", loaded.getWindowTitle());
        assertEquals(800, loaded.getWidth());
        assertEquals(600, loaded.getHeight());
        assertFalse(loaded.isVsync());
        assertTrue(loaded.isFullscreen());
    }

    @Test
    void testLoadMalformedFileThrowsRuntimeException() throws IOException {
        Path configPath = tempDir.resolve("badConfig.json");
        Files.writeString(configPath, "{ invalid json");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ConfigManager.load(configPath));
        assertTrue(ex.getMessage().contains("Failed to load config"));
    }
}
