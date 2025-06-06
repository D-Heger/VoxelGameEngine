package de.heger.voxelengine.renderer.management;

import de.heger.voxelengine.assets.texture.TextureData;
import de.heger.voxelengine.assets.texture.TextureLoader;
import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.texture.Texture;
import de.heger.voxelengine.world.block.BlockProperties;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.block.TextureRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages the loading, storage, and retrieval of textures.
 */
public class TextureManager {
    private static final LoggerFacade logger = LoggerFacade.get(TextureManager.class);

    private final Map<String, Texture> textureMap = new HashMap<>();
    private final TextureLoader textureLoader = new TextureLoader();
    private final BlockRegistry blockRegistry;

    private static final String FALLBACK_TEXTURE_NAME = "core:block/dirt";
    private Texture defaultFallbackTexture = null;

    public TextureManager(BlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    /**
     * Loads all textures required by the blocks defined in the {@link BlockRegistry}.
     */
    public void loadBlockTextures() {
        logger.info("Loading textures based on BlockRegistry...");
        Set<String> uniqueTextureNames = new HashSet<>();

        // Collect unique texture names from all registered blocks
        for (BlockProperties properties : blockRegistry.getAllProperties()) {
            if (properties.getTextures() != null) {
                for (TextureRef texRef : properties.getTextures().values()) {
                    if (texRef != null && texRef.getName() != null) {
                        uniqueTextureNames.add(texRef.getName());
                    }
                }
            }
        }

        logger.debug("Found {} unique texture names to load: {}", uniqueTextureNames.size(), uniqueTextureNames);

        // Load each unique texture
        for (String textureName : uniqueTextureNames) {
            if (textureMap.containsKey(textureName)) {
                logger.warn("Texture '{}' already loaded, skipping.", textureName);
                continue;
            }

            String resourcePath = deriveTextureResourcePath(textureName);
            if (resourcePath == null) {
                logger.error("Could not derive resource path for texture name: {}", textureName);
                continue;
            }

            try {
                logger.info("Loading texture '{}' from path '{}'", textureName, resourcePath);
                TextureData textureData = textureLoader.loadTexture(resourcePath);
                if (textureData != null) {
                    Texture texture = new Texture(textureData);
                    textureMap.put(textureName, texture);
                    logger.info("Loaded texture: '{}'", textureName);
                } else {
                    logger.error("Failed to load texture data for '{}' from path '{}'. Check resource path and file.",
                            textureName, resourcePath);
                }
            } catch (Exception e) {
                logger.error("Failed to load or create texture '{}' from path '{}'", textureName, resourcePath, e);
            }
        }

        this.defaultFallbackTexture = textureMap.get(FALLBACK_TEXTURE_NAME);
        if (this.defaultFallbackTexture == null) {
            logger.warn("Configured default fallback texture '{}' not found in texture map.", FALLBACK_TEXTURE_NAME);
        } else {
            logger.info("Default fallback texture set to: '{}'", FALLBACK_TEXTURE_NAME);
        }
        logger.info("Finished loading block textures. {} textures loaded.", textureMap.size());
    }

    /**
     * Retrieves a loaded texture by its name.
     * @param textureName The name of the texture (e.g., "core:block/dirt").
     * @return The {@link Texture} object, or null if not found.
     */
    public Texture getTexture(String textureName) {
        return textureMap.get(textureName);
    }

    /**
     * @return The default fallback texture to be used when a requested texture is not found. Can be null.
     */
    public Texture getDefaultFallbackTexture() {
        return defaultFallbackTexture;
    }

    /**
     * Cleans up all loaded textures, releasing their GPU resources.
     */
    public void cleanup() {
        logger.debug("Cleaning up textures...");
        for (Map.Entry<String, Texture> entry : textureMap.entrySet()) {
            try {
                entry.getValue().cleanup();
            } catch (Exception e) {
                logger.error("Error cleaning up texture: {}", entry.getKey(), e);
            }
        }
        textureMap.clear();
        logger.debug("Texture cleanup complete.");
    }

    /**
     * Derives the resource path for a texture based on its registry name.
     * Example: "core:block/dirt" -> "textures/block/dirt.png"
     *
     * @param textureName The texture name from the registry.
     * @return The resource path string, or null if derivation fails.
     */
    private String deriveTextureResourcePath(String textureName) {
        if (textureName == null || !textureName.contains(":")) {
            logger.warn("Texture name '{}' is null or missing namespace separator ':'. Cannot derive path.",
                    textureName);
            return null;
        }
        String[] parts = textureName.split(":", 2);
        if (parts.length < 2 || !parts[1].contains("/")) {
            logger.warn("Texture name '{}' does not match expected format 'namespace:type/name'. Cannot derive path.",
                    textureName);
            return null;
        }

        String typeAndName = parts[1]; // e.g., "block/dirt"
        return "textures/" + typeAndName + ".png";
    }
} 