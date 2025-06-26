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

    private int atlasTextureId = 0;
    private final Map<String, float[]> atlasTransformMap = new HashMap<>(); // textureName -> {offsetU, offsetV, scaleU, scaleV}
    private static final int ATLAS_TILE_SIZE = 16; // assumes all block textures are square 16×16

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

        buildTextureAtlas(); // NEW – build atlas after individual textures are on GPU
    }

    /**
     * Builds a single atlas texture containing every loaded block texture arranged on a grid.<br/>
     * This drastically reduces texture-bind traffic at run-time.  All tiles are assumed to be
     * uniformly sized (currently 16×16).  For heterogeneous sizes, pre-scaling would be required.
     */
    private void buildTextureAtlas() {
        if (textureMap.isEmpty()) {
            logger.warn("No textures loaded – atlas creation skipped.");
            return;
        }

        int tileSize = ATLAS_TILE_SIZE;
        int tileCount = textureMap.size();
        int tilesPerRow = (int) Math.ceil(Math.sqrt(tileCount));
        int atlasSize = tilesPerRow * tileSize;

        // Allocate empty RGBA8 atlas texture
        int atlasTexId = org.lwjgl.opengl.GL11.glGenTextures();
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, atlasTexId);
        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                org.lwjgl.opengl.GL11.GL_RGBA8, atlasSize, atlasSize, 0,
                org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);

        // Set sampler params (pixel-art style – nearest)
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_NEAREST);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);

        // --- Copy each small texture into the atlas ---
        java.nio.ByteBuffer scratch = org.lwjgl.BufferUtils.createByteBuffer(tileSize * tileSize * 4);
        int index = 0;
        for (Map.Entry<String, Texture> entry : textureMap.entrySet()) {
            String texName = entry.getKey();
            Texture srcTex = entry.getValue();

            int tileX = index % tilesPerRow;
            int tileY = index / tilesPerRow;
            int destX = tileX * tileSize;
            int destY = tileY * tileSize;

            // Read back pixels from the GPU and upload into atlas (slow but done once at start-up)
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, srcTex.getTextureId());
            scratch.clear();
            org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, scratch);

            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, atlasTexId);
            org.lwjgl.opengl.GL11.glTexSubImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                    destX, destY, tileSize, tileSize,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, scratch);

            float offsetU = (float) destX / atlasSize;
            float offsetV = (float) destY / atlasSize;
            float scaleU = (float) tileSize / atlasSize;
            float scaleV = (float) tileSize / atlasSize;
            atlasTransformMap.put(texName, new float[]{offsetU, offsetV, scaleU, scaleV});

            index++;
        }

        // Generate mip-maps for the atlas
        org.lwjgl.opengl.GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

        this.atlasTextureId = atlasTexId;
        logger.info("Texture atlas built: {} × {} ({} tiles)", atlasSize, atlasSize, tileCount);
    }

    /**
     * Returns the atlas texture ID.
     */
    public int getAtlasTextureId() {
        return atlasTextureId;
    }

    /**
     * Returns the UV transform for a specific block texture inside the atlas.
     * The returned array has layout {offsetU, offsetV, scaleU, scaleV}.  Can be null if the
     * texture name was not part of the atlas (should not happen for valid blocks).
     */
    public float[] getAtlasTransform(String textureName) {
        return atlasTransformMap.get(textureName);
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
        if (atlasTextureId != 0) {
            org.lwjgl.opengl.GL11.glDeleteTextures(atlasTextureId);
            atlasTextureId = 0;
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