package de.heger.voxelengine.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.io.File;

/**
 * Utility class for loading and saving configuration.
 */
public class ConfigManager {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DEFAULT_CONFIG_FILE = "config.json";

    /**
     * Load configuration from default file, creating it with defaults if absent.
     */
    public static Config load() {
        return load(Path.of(DEFAULT_CONFIG_FILE));
    }

    /**
     * Load configuration from given path.
     * @param path Path to config file
     */
    public static Config load(Path path) {
        try {
            File file = path.toFile();
            if (!file.exists()) {
                Config defaultConfig = new Config();
                save(defaultConfig, path);
                return defaultConfig;
            }
            return mapper.readValue(file, Config.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config from " + path, e);
        }
    }

    /**
     * Save configuration to default file.
     */
    public static void save(Config config) {
        save(config, Path.of(DEFAULT_CONFIG_FILE));
    }

    /**
     * Save configuration to given path.
     * @param path Path to config file
     */
    public static void save(Config config, Path path) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save config to " + path, e);
        }
    }
}
