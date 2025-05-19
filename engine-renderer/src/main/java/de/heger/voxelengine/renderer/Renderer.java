package de.heger.voxelengine.renderer;

import de.heger.voxelengine.assets.texture.TextureData;
import de.heger.voxelengine.assets.texture.TextureLoader;
import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.culling.AABB;
import de.heger.voxelengine.renderer.culling.FrustumCuller;
import de.heger.voxelengine.renderer.culling.OcclusionCuller;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import de.heger.voxelengine.renderer.texture.Texture;
import de.heger.voxelengine.world.block.BlockProperties;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.block.TextureRef;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.CoordinateUtils;
import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.renderer.mesh.ChunkMesh;
import de.heger.voxelengine.renderer.mesh.ChunkMeshBuilder;
import de.heger.voxelengine.world.chunk.ChunkMeshState;
import de.heger.voxelengine.renderer.debug.WireframeRenderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import static org.lwjgl.opengl.GL11.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

public class Renderer {

    private static final LoggerFacade logger = LoggerFacade.get(Renderer.class);

    private final Window window;
    private GLCapabilities capabilities;
    private Callback debugCallback;
    private ShaderProgram defaultShaderProgram;
    private Camera camera;
    private TextureLoader textureLoader;
    private Map<String, Texture> textureMap;
    private FrustumCuller frustumCuller;
    private OcclusionCuller occlusionCuller;

    private ChunkManager chunkManager;
    private BlockRegistry blockRegistry;

    private WireframeRenderer wireframeRenderer;
    private boolean wireframeMode = false;

    private final Map<ChunkPos, Map<String, ChunkMesh>> activeChunkMeshes = new HashMap<>();
    private final Map<ChunkPos, AABB> chunkAABBCache = new HashMap<>();

    private int totalIndicesRenderedLastFrame = 0;
    private int drawCallsLastFrame = 0;
    private int occlusionCulledChunksLastFrame = 0;
    private int frustumCulledChunksLastFrame = 0;

    private final Matrix4f viewProjectionMatrixForCulling = new Matrix4f();

    private static final boolean USE_OCCLUSION_CULLING = true;

    public Renderer(Window window) {
        this.window = window;
        this.camera = new Camera();
        this.textureLoader = new TextureLoader();
        this.textureMap = new HashMap<>();
        this.chunkManager = ChunkManager.getInstance();
        this.blockRegistry = BlockRegistry.getInstance();
        this.occlusionCuller = new OcclusionCuller();
        this.wireframeRenderer = new WireframeRenderer();
    }

    public void init() {
        logger.info("Initializing OpenGL context...");

        // This line is crucial! It tells LWJGL to use the current context
        // which was made current by the Window class after creation.
        capabilities = GL.createCapabilities();

        if (capabilities == null) {
            throw new RuntimeException("Failed to create OpenGL capabilities");
        }

        if (capabilities.OpenGL43 || capabilities.GL_KHR_debug) {
            logger.info("OpenGL debug output enabled.");
            debugCallback = GLUtil.setupDebugMessageCallback(System.err); // Log errors to stderr
        } else {
            logger.warn("OpenGL debug output not available.");
        }

        // Set the clear color (background color) - Dark blue-grey
        glClearColor(0.1f, 0.15f, 0.2f, 1.0f);

        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
        logger.info("Depth testing enabled.");

        // Enable back face culling
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        logger.info("Back face culling enabled.");

        // Log OpenGL version
        logger.info("OpenGL Version: {}", glGetString(GL_VERSION));
        logger.info("Vendor: {}", glGetString(GL_VENDOR));
        logger.info("Renderer: {}", glGetString(GL_RENDERER));

        // Set initial aspect ratio for camera
        camera.setAspectRatio(window.getAspectRatio());

        // Load and compile shaders
        try {
            loadShaders();
        } catch (Exception e) {
            logger.error("Failed to load shaders", e);
            throw new RuntimeException("Failed to initialize shaders", e);
        }

        // Load Textures based on BlockRegistry
        try {
            logger.info("Loading textures based on BlockRegistry...");
            loadBlockTextures();
            logger.info("Finished loading block textures. {} textures loaded.", textureMap.size());
        } catch (Exception e) {
            logger.error("Failed to load block textures", e);
            throw new RuntimeException("Failed to initialize block textures", e);
        }

        logger.info("OpenGL context initialized successfully.");
    }

    private void loadBlockTextures() {
        BlockRegistry registry = BlockRegistry.getInstance();
        Set<String> uniqueTextureNames = new HashSet<>();

        // Collect unique texture names from all registered blocks
        for (BlockProperties properties : registry.getAllProperties()) {
            if (properties.getTextures() != null) {
                for (TextureRef texRef : properties.getTextures().values()) {
                    if (texRef != null && texRef.getName() != null) {
                        uniqueTextureNames.add(texRef.getName());
                    }
                }
            }
        }
        // Add air texture explicitly if needed, though air usually isn't rendered
        // uniqueTextureNames.add("core:block/air"); // Example if air had a texture

        logger.debug("Found {} unique texture names to load: {}", uniqueTextureNames.size(), uniqueTextureNames);

        // Load each unique texture
        for (String textureName : uniqueTextureNames) {
            if (textureMap.containsKey(textureName)) {
                logger.warn("Texture '{}' already loaded, skipping.", textureName);
                continue;
            }

            // Derive resource path from name (e.g., "core:block/dirt" ->
            // "textures/block/dirt.png")
            // This assumes a convention: namespace:type/name -> textures/type/name.png
            String resourcePath = deriveTextureResourcePath(textureName);
            if (resourcePath == null) {
                logger.error("Could not derive resource path for texture name: {}", textureName);
                continue; // Or throw an error
            }

            try {
                logger.info("Loading texture '{}' from path '{}'", textureName, resourcePath);
                TextureData textureData = textureLoader.loadTexture(resourcePath);
                if (textureData != null) {
                    Texture texture = new Texture(textureData); // Create OpenGL texture
                    textureMap.put(textureName, texture);
                    logger.info("Loaded texture: '{}'", textureName);
                } else {
                    logger.error("Failed to load texture data for '{}' from path '{}'. Check resource path and file.",
                            textureName, resourcePath);
                    // Optionally: Load a default "missing" texture instead
                    // textureMap.put(textureName, getMissingTexture());
                }
            } catch (Exception e) {
                logger.error("Failed to load or create texture '{}' from path '{}'", textureName, resourcePath, e);
                // Optionally: Load a default "missing" texture instead
                // textureMap.put(textureName, getMissingTexture());
            }
        }
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
        // Assuming format "namespace:type/name"
        String[] parts = textureName.split(":", 2);
        if (parts.length < 2 || !parts[1].contains("/")) {
            logger.warn("Texture name '{}' does not match expected format 'namespace:type/name'. Cannot derive path.",
                    textureName);
            return null;
        }

        String typeAndName = parts[1]; // e.g., "block/dirt"
        // Construct path: "textures/" + type + "/" + name + ".png"
        return "textures/" + typeAndName + ".png";
    }

    private void loadShaders() throws Exception {
        logger.info("Loading default shader program...");
        defaultShaderProgram = new ShaderProgram();

        // Load shader source from resources
        String vertexShaderSource = ShaderProgram.loadShaderSourceFromResources("/shaders/default.vert");
        String fragmentShaderSource = ShaderProgram.loadShaderSourceFromResources("/shaders/default.frag");

        // Create and link shaders
        defaultShaderProgram.createVertexShader(vertexShaderSource);
        defaultShaderProgram.createFragmentShader(fragmentShaderSource);
        defaultShaderProgram.link();
        logger.info("Default shader program loaded and linked successfully.");

        // Create uniforms (example - these will depend on your actual shader)
        // It's good practice to create uniforms after linking
        defaultShaderProgram.createUniform("projection");
        defaultShaderProgram.createUniform("view");
        defaultShaderProgram.createUniform("model");
        defaultShaderProgram.createUniform("uTexture"); // Add texture sampler uniform
        defaultShaderProgram.createUniform("lightDir");
        defaultShaderProgram.createUniform("lightColor");
        defaultShaderProgram.createUniform("ambientColor");
        defaultShaderProgram.createUniform("ambientStrength");
        defaultShaderProgram.createUniform("viewPos");
        defaultShaderProgram.createUniform("specularStrength");
        defaultShaderProgram.createUniform("shininess");
        logger.debug("Created uniforms for default shader program.");
    }

    public void clear() {
        // Clear the framebuffer (color and depth)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Renders the blocks within the given list of chunks. (P3-T2.4, P3-T2.5)
     * This is a basic implementation rendering a separate cube for each block.
     *
     * @param chunks The list of chunks to render.
     */
    public void renderChunks(Collection<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty() || defaultShaderProgram == null || textureMap.isEmpty()
                || blockRegistry == null || chunkManager == null) {
            return;
        }

        drawCallsLastFrame = 0;
        totalIndicesRenderedLastFrame = 0;
        frustumCulledChunksLastFrame = 0;
        occlusionCulledChunksLastFrame = 0;

        defaultShaderProgram.bind();
        Matrix4f currentViewMatrix = camera.getViewMatrix();
        defaultShaderProgram.setUniform("projection", camera.getProjectionMatrix());
        defaultShaderProgram.setUniform("view", currentViewMatrix);

        // Set lighting uniforms
        defaultShaderProgram.setUniform("lightDir", new Vector3f(-0.5f, -1.0f, -0.5f));
        defaultShaderProgram.setUniform("lightColor", new Vector3f(1.0f, 0.95f, 0.8f));
        defaultShaderProgram.setUniform("ambientColor", new Vector3f(0.4f, 0.5f, 0.6f));
        defaultShaderProgram.setUniform("ambientStrength", 0.6f);
        defaultShaderProgram.setUniform("uTexture", 0); // Tell shader to use texture unit 0

        // Set new lighting uniforms
        defaultShaderProgram.setUniform("viewPos", camera.getPosition());
        defaultShaderProgram.setUniform("specularStrength", 0.5f); // Example value
        defaultShaderProgram.setUniform("shininess", 32.0f);      // Example value

        Matrix4f modelMatrix = new Matrix4f(); // Reused for each chunk
        // OLD: Matrix4f viewProjectionMatrix = new
        // Matrix4f(camera.getProjectionMatrix()).mul(currentViewMatrix);
        // NEW: Reuse member field to avoid allocations
        viewProjectionMatrixForCulling.set(camera.getProjectionMatrix());
        viewProjectionMatrixForCulling.mul(currentViewMatrix);

        if (this.frustumCuller == null) {
            this.frustumCuller = new FrustumCuller(viewProjectionMatrixForCulling);
        } else {
            this.frustumCuller.updateViewProjectionMatrix(viewProjectionMatrixForCulling);
        }

        Set<ChunkPos> currentlyVisibleAndMeshedChunks = new HashSet<>();

        // Apply frustum culling first to create a list of potentially visible chunks
        Set<Chunk> chunksInFrustum = new HashSet<>();

        for (Chunk chunk : chunks) {
            if (chunk == null)
                continue;

            AABB chunkAABB = chunkAABBCache.get(chunk.getPosition());
            if (chunkAABB == null) {
                chunkAABB = AABB.fromChunk(chunk);
                chunkAABBCache.put(chunk.getPosition(), chunkAABB);
            }

            if (!frustumCuller.testAABB(chunkAABB)) {
                frustumCulledChunksLastFrame++;
                continue;
            }
            chunksInFrustum.add(chunk);
        }

        Collection<Chunk> visibleChunks;
        if (occlusionCuller != null && USE_OCCLUSION_CULLING) {
            List<Chunk> sortedChunksInFrustum = new ArrayList<>(chunksInFrustum);
            final Vector3f cameraPos = camera.getPosition();
            sortedChunksInFrustum.sort(Comparator.comparingDouble(chunk -> {
                Vec3i worldPos = CoordinateUtils.chunkOriginToWorldCoords(chunk.getPosition());
                // Use Chunk.SIZE_X for cubic chunk dimensions
                return cameraPos.distanceSquared(worldPos.x + Chunk.SIZE_X / 2f, worldPos.y + Chunk.SIZE_Y / 2f,
                        worldPos.z + Chunk.SIZE_Z / 2f);
            }));

            visibleChunks = occlusionCuller.filterOccludedChunks(
                    sortedChunksInFrustum,
                    camera.getPosition(),
                    camera.getFront(),
                    (occludedCount) -> this.occlusionCulledChunksLastFrame = occludedCount // Corrected lambda usage
                                                                                           // based on new signature
            );
        } else {
            visibleChunks = chunksInFrustum;
        }

        // Track occlusion culling statistics
        occlusionCulledChunksLastFrame = chunksInFrustum.size() - visibleChunks.size();

        // Process and render the visible chunks
        for (Chunk chunk : visibleChunks) {
            ChunkPos chunkPos = chunk.getPosition();
            currentlyVisibleAndMeshedChunks.add(chunkPos);

            // Get or build mesh for the chunk
            Map<String, ChunkMesh> meshesForChunk = activeChunkMeshes.get(chunkPos);

            if (meshesForChunk == null || chunk.getMeshState() == ChunkMeshState.NEEDS_REBUILD) {
                if (meshesForChunk != null) { // Old meshes exist, clean them up
                    logger.debug("Chunk {} needs rebuild, cleaning old meshes.", chunkPos);
                    for (ChunkMesh oldMesh : meshesForChunk.values()) {
                        oldMesh.cleanup();
                    }
                }
                logger.debug("Building mesh for chunk {} (State: {})...", chunkPos, chunk.getMeshState());
                // Critical section: set state to BUILDING, build, then set to UP_TO_DATE /
                // EMPTY
                // For now, synchronous build. Asynchronous would need more complex state
                // handling.
                chunk.setMeshState(ChunkMeshState.BUILDING); // Mark as building
                meshesForChunk = ChunkMeshBuilder.buildMeshesByTexture(chunk, chunkManager, blockRegistry);
                activeChunkMeshes.put(chunkPos, meshesForChunk);
                chunk.setMeshState(meshesForChunk.isEmpty() ? ChunkMeshState.EMPTY : ChunkMeshState.UP_TO_DATE);
                logger.debug("Finished building mesh for chunk {}, found {} submeshes. New state: {}",
                        chunkPos, meshesForChunk.size(), chunk.getMeshState());
            }

            if (meshesForChunk.isEmpty()) {
                continue; // Nothing to render for this chunk
            }

            // Calculate chunk's world origin ONCE for all its sub-meshes
            Vec3i chunkOriginWorld = CoordinateUtils.chunkOriginToWorldCoords(chunkPos);
            modelMatrix.identity().translation(chunkOriginWorld.x, chunkOriginWorld.y, chunkOriginWorld.z);
            // Note: The vertices in ChunkMesh are already relative to chunk origin (0,0,0)
            // So, translating the model matrix to the chunk's world origin is correct.

            defaultShaderProgram.setUniform("model", modelMatrix);

            // Render each sub-mesh (grouped by texture)
            for (Map.Entry<String, ChunkMesh> meshEntry : meshesForChunk.entrySet()) {
                String textureName = meshEntry.getKey();
                ChunkMesh chunkMeshToRender = meshEntry.getValue();

                if (chunkMeshToRender.isEmpty()) {
                    continue;
                }

                Texture textureToRender = textureMap.get(textureName);
                if (textureToRender == null) {
                    // logger.warn("Texture '{}' not found for chunk {}, submesh. Using
                    // fallback/default?", textureName, chunkPos);
                    // Optionally: bind a default "missing" texture
                    // For now, skip rendering this submesh if texture is missing to avoid GL errors
                    // or wrong textures.
                    Texture fallbackTexture = textureMap.get("core:block/dirt"); // Example fallback
                    if (fallbackTexture == null && !textureMap.isEmpty())
                        fallbackTexture = textureMap.values().iterator().next();

                    if (fallbackTexture != null) {
                        fallbackTexture.bind(0);
                    } else {
                        // logger.error("No fallback texture available for chunk {}, submesh with
                        // texture key '{}'", chunkPos, textureName);
                        continue; // No texture, no render
                    }
                } else {
                    textureToRender.bind(0); // Bind to texture unit 0
                }

                if (wireframeMode) {
                    wireframeRenderer.render(chunkMeshToRender);
                } else {
                    chunkMeshToRender.render();
                }
                // P4-T2.7: Increment performance counters
                totalIndicesRenderedLastFrame += chunkMeshToRender.getIndexCount();
                drawCallsLastFrame++;
            }
        }
        // Removed old rendering logic that iterates individual blocks/faces

        // After rendering all visible chunks, clean up meshes for chunks that are no
        // longer visible/loaded
        // but were previously meshed and are still in activeChunkMeshes.
        // This assumes `chunks` collection contains ALL chunks that *should* be around.
        // A more robust cleanup might be needed if `chunks` is only a subset of what
        // *could* be loaded.
        activeChunkMeshes.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            if (!currentlyVisibleAndMeshedChunks.contains(pos)) {
                logger.debug("Evicting and cleaning meshes for chunk {} (no longer visible/loaded).", pos);
                for (ChunkMesh mesh : entry.getValue().values()) {
                    mesh.cleanup();
                }
                // P4-T5.2: Also remove from AABB cache
                AABB removedAABB = chunkAABBCache.remove(pos);
                if (removedAABB != null) {
                    logger.debug("Removed AABB for chunk {} from cache.", pos);
                }
                return true; // Remove from activeChunkMeshes
            }
            return false;
        });

        // defaultShaderProgram.unbind(); // Only unbind if no other render passes
        // follow
    }

    public void cleanup() {
        logger.info("Cleaning up Renderer resources...");

        // Cleanup shaders
        if (defaultShaderProgram != null) {
            defaultShaderProgram.cleanup();
            logger.debug("Cleaned up default shader program.");
        }

        // Cleanup textures
        if (textureMap != null) {
            for (Map.Entry<String, Texture> entry : textureMap.entrySet()) {
                try {
                    entry.getValue().cleanup();
                    logger.debug("Cleaned up texture: {}", entry.getKey());
                } catch (Exception e) {
                    logger.error("Error cleaning up texture: {}", entry.getKey(), e);
                }
            }
            textureMap.clear();
            logger.debug("Cleared texture map.");
        }

        // P4-T2.6: Cleanup active chunk meshes
        if (activeChunkMeshes != null) {
            logger.debug("Cleaning up {} cached chunk mesh entries.", activeChunkMeshes.size());
            for (Map<String, ChunkMesh> subMeshes : activeChunkMeshes.values()) {
                for (ChunkMesh mesh : subMeshes.values()) {
                    mesh.cleanup();
                }
            }
            activeChunkMeshes.clear();
            logger.debug("Cleared active chunk meshes cache.");
        }

        // P4-T5.2: Clear AABB cache
        if (chunkAABBCache != null) {
            chunkAABBCache.clear();
            logger.debug("Cleared chunk AABB cache.");
        }

        // Cleanup debug callback
        if (debugCallback != null) {
            debugCallback.free();
            logger.debug("Freed debug callback.");
        }

        // Clean up occlusion culler
        if (occlusionCuller != null) {
            occlusionCuller.clearOpaqueCache();
            logger.debug("Cleared occlusion culler cache.");
        }

        // Destroy OpenGL context? Usually handled by Window class on close.
        // GL.destroy(); // Don't call this here, Window manages context lifetime

        logger.info("Renderer cleanup complete.");
    }

    public Camera getCamera() {
        return camera;
    }

    // P4-T2.7: Getters for performance metrics
    public int getDrawCallsLastFrame() {
        return drawCallsLastFrame;
    }

    public int getTotalIndicesRenderedLastFrame() {
        return totalIndicesRenderedLastFrame;
    }

    public int getOcclusionCulledChunksLastFrame() {
        return occlusionCulledChunksLastFrame;
    }

    public int getFrustumCulledChunksLastFrame() {
        return frustumCulledChunksLastFrame;
    }

    public int getActiveMeshCount() {
        return activeChunkMeshes.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Toggles wireframe rendering mode on or off.
     */
    public void toggleWireframeMode() {
        wireframeMode = !wireframeMode;
        logger.info("Wireframe mode: {}", wireframeMode ? "enabled" : "disabled");
    }

    /**
     * Checks if wireframe mode is currently enabled.
     * 
     * @return true if wireframe mode is enabled, false otherwise.
     */
    public boolean isWireframeMode() {
        return wireframeMode;
    }

    /**
     * Releases any renderer-specific resources associated with a chunk,
     * primarily its meshes.
     * 
     * @param chunkPos The position of the chunk whose resources are to be released.
     */
    public void releaseChunkResources(ChunkPos chunkPos) {
        Map<String, ChunkMesh> meshesForChunk = activeChunkMeshes.remove(chunkPos);
        if (meshesForChunk != null) {
            logger.debug("Releasing {} sub-meshes for chunk {}.", meshesForChunk.size(), chunkPos);
            for (ChunkMesh mesh : meshesForChunk.values()) {
                mesh.cleanup(); // Assumes ChunkMesh has a cleanup method for VBO/VAO
            }
        }
        // P4-T5.2: Also remove from AABB cache
        AABB removedAABB = chunkAABBCache.remove(chunkPos);
        if (removedAABB != null) {
            logger.debug("Removed AABB for chunk {} from cache.", chunkPos);
        }
    }
}
