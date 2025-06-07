package de.heger.voxelengine.renderer;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.culling.AABB;
import de.heger.voxelengine.renderer.culling.FrustumCuller;
import de.heger.voxelengine.renderer.culling.OcclusionCuller;
import de.heger.voxelengine.renderer.debug.WireframeRenderer;
import de.heger.voxelengine.renderer.management.ChunkMeshManager;
import de.heger.voxelengine.renderer.management.RenderStats;
import de.heger.voxelengine.renderer.management.SceneLightingManager;
import de.heger.voxelengine.renderer.management.TextureManager;
import de.heger.voxelengine.renderer.mesh.ChunkMesh;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import de.heger.voxelengine.renderer.texture.Texture;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.CoordinateUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL11.GL_VERSION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * The main rendering class for the voxel engine. It orchestrates the entire rendering process,
 * from managing OpenGL state to drawing chunk meshes and handling scene properties.
 */
public class Renderer {

    private static final LoggerFacade logger = LoggerFacade.get(Renderer.class);

    private final Window window;
    private final Camera camera;
    private final ChunkManager chunkManager;
    private final BlockRegistry blockRegistry;

    // Sub-systems for managing different rendering aspects
    private final TextureManager textureManager;
    private final SceneLightingManager sceneLightingManager;
    private final ChunkMeshManager chunkMeshManager;
    private final RenderStats renderStats;

    // Rendering tools and state
    private GLCapabilities capabilities;
    private Callback debugCallback;
    private ShaderProgram defaultShaderProgram;
    private FrustumCuller frustumCuller;
    private OcclusionCuller occlusionCuller;
    private WireframeRenderer wireframeRenderer;
    private boolean wireframeMode = false;

    private static final boolean USE_OCCLUSION_CULLING = true;

    // Reusable objects to reduce allocations in the render loop
    private final Matrix4f reusableMatrix4f = new Matrix4f();
    private final Matrix4f viewProjectionMatrixForCulling = new Matrix4f();
    private final List<Chunk> reusableChunkList = new ArrayList<>();
    private final List<Chunk> reusableOcclusionList = new ArrayList<>();

    // Texture batching optimization - reusable collections
    private final Map<String, List<RenderableChunkData>> textureToChunkDataMap = new HashMap<>();
    private final List<RenderableChunkData> reusableRenderableDataList = new ArrayList<>();

    // Texture binding state tracking
    private String currentlyBoundTexture = null;

    /**
     * Helper class to store renderable chunk data for texture batching optimization.
     */
    private static class RenderableChunkData {
        final Chunk chunk;
        final String textureName;
        final ChunkMesh mesh;
        final Vec3i chunkOriginWorld;

        RenderableChunkData(Chunk chunk, String textureName, ChunkMesh mesh, Vec3i chunkOriginWorld) {
            this.chunk = chunk;
            this.textureName = textureName;
            this.mesh = mesh;
            this.chunkOriginWorld = chunkOriginWorld;
        }
    }

    public Renderer(Window window) {
        this.window = window;
        this.camera = new Camera();
        this.chunkManager = ChunkManager.getInstance();
        this.blockRegistry = BlockRegistry.getInstance();

        // Initialize management subsystems
        this.renderStats = new RenderStats();
        this.textureManager = new TextureManager(blockRegistry);
        this.sceneLightingManager = new SceneLightingManager();
        this.chunkMeshManager = new ChunkMeshManager(chunkManager, blockRegistry);

        // Initialize rendering tools
        this.occlusionCuller = new OcclusionCuller();
        this.wireframeRenderer = new WireframeRenderer();
    }

    /**
     * Initializes the OpenGL context, loads shaders and textures, and sets up initial GL state.
     * This must be called after the window and OpenGL context have been created.
     */
    public void init() {
        logger.info("Initializing OpenGL context...");
        capabilities = GL.createCapabilities();
        if (capabilities == null) {
            throw new RuntimeException("Failed to create OpenGL capabilities");
        }

        if (capabilities.OpenGL43 || capabilities.GL_KHR_debug) {
            logger.info("OpenGL debug output enabled.");
            debugCallback = GLUtil.setupDebugMessageCallback(System.err);
        } else {
            logger.warn("OpenGL debug output not available.");
        }

        sceneLightingManager.update();
        Vector3f initialFogColor = sceneLightingManager.getFogColor();
        glClearColor(initialFogColor.x, initialFogColor.y, initialFogColor.z, 1.0f);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        logger.info("OpenGL Version: {}", glGetString(GL_VERSION));
        camera.setAspectRatio(window.getAspectRatio());

        try {
            loadShaders();
            textureManager.loadBlockTextures();
        } catch (Exception e) {
            logger.error("Failed to initialize renderer resources", e);
            throw new RuntimeException("Failed to initialize renderer resources", e);
        }

        logger.info("Renderer initialized successfully.");
    }

    private void loadShaders() throws Exception {
        logger.info("Loading default shader program...");
        defaultShaderProgram = new ShaderProgram();
        String vertexShaderSource = ShaderProgram.loadShaderSourceFromResources("/shaders/default.vert");
        String fragmentShaderSource = ShaderProgram.loadShaderSourceFromResources("/shaders/default.frag");
        defaultShaderProgram.createVertexShader(vertexShaderSource);
        defaultShaderProgram.createFragmentShader(fragmentShaderSource);
        defaultShaderProgram.link();
        logger.info("Default shader program loaded and linked successfully.");

        // Create uniforms
        defaultShaderProgram.createUniform("projection");
        defaultShaderProgram.createUniform("view");
        defaultShaderProgram.createUniform("model");
        defaultShaderProgram.createUniform("uTexture");
        defaultShaderProgram.createUniform("lightDir");
        defaultShaderProgram.createUniform("lightColor");
        defaultShaderProgram.createUniform("ambientColor");
        defaultShaderProgram.createUniform("ambientStrength");
        defaultShaderProgram.createUniform("viewPos");
        defaultShaderProgram.createUniform("fogColor");
        defaultShaderProgram.createUniform("fogStart");
        defaultShaderProgram.createUniform("fogEnd");
    }

    /**
     * Renders all visible chunks. This is the main entry point for world rendering each frame.
     * @param chunks A collection of all currently loaded chunks that are candidates for rendering.
     */
    public void renderChunks(Collection<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty() || defaultShaderProgram == null) {
            return;
        }

        chunkMeshManager.processCompletedMeshData();
        sceneLightingManager.update();
        renderStats.reset();

        Vector3f fogColor = sceneLightingManager.getFogColor();
        glClearColor(fogColor.x, fogColor.y, fogColor.z, 1.0f);
        clear();

        setupShaderUniforms();

        // Pre-compute AABB for all chunks
        chunkMeshManager.precomputeAABBForChunk(chunks);

        Collection<Chunk> visibleChunks = performCulling(chunks);

        renderVisibleChunks(visibleChunks);

        chunkMeshManager.evictStaleMeshes();
    }

    /**
     * Clears the framebuffer.
     */
    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private void setupShaderUniforms() {
        defaultShaderProgram.bind();
        defaultShaderProgram.setUniform("projection", camera.getProjectionMatrix());
        defaultShaderProgram.setUniform("view", camera.getViewMatrix());
        defaultShaderProgram.setUniform("viewPos", camera.getPosition());
        defaultShaderProgram.setUniform("uTexture", 0);
        sceneLightingManager.applyUniforms(defaultShaderProgram, camera.getViewDistance());
    }

    private Collection<Chunk> performCulling(Collection<Chunk> allChunks) {
        // Update frustum culler with the latest camera matrices
        viewProjectionMatrixForCulling.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
        if (this.frustumCuller == null) {
            this.frustumCuller = new FrustumCuller(viewProjectionMatrixForCulling);
        } else {
            this.frustumCuller.updateViewProjectionMatrix(viewProjectionMatrixForCulling);
        }

        // Frustum cull and sort visible chunks
        reusableChunkList.clear();
        reusableOcclusionList.clear();
        for (Chunk chunk : allChunks) {
            if (chunk == null) continue;
            AABB chunkAABB = chunkMeshManager.getAABBForChunk(chunk);
            if (!frustumCuller.testAABB(chunkAABB)) {
                renderStats.incrementFrustumCulledChunks();
                continue;
            }
            reusableChunkList.add(chunk);
        }

        final Vector3f cameraPosForSorting = camera.getPosition();
        reusableChunkList.sort(Comparator.comparingDouble(chunk -> {
            Vec3i worldPos = CoordinateUtils.chunkOriginToWorldCoords(chunk.getPosition());
            return cameraPosForSorting.distanceSquared(
                    worldPos.x + Chunk.SIZE_X / 2f,
                    worldPos.y + Chunk.SIZE_Y / 2f,
                    worldPos.z + Chunk.SIZE_Z / 2f
            );
        }));

        // Occlusion cull the remaining chunks
        if (occlusionCuller != null && USE_OCCLUSION_CULLING) {
            return occlusionCuller.filterOccludedChunks(
                    reusableChunkList,
                    cameraPosForSorting,
                    camera.getFront(),
                    renderStats::setOcclusionCulledChunks
            );
        } else {
            renderStats.setOcclusionCulledChunks(0);
            reusableOcclusionList.addAll(reusableChunkList);
            return reusableOcclusionList;
        }
    }

    /**
     * Renders visible chunks using texture batching optimization.
     * Groups all meshes by texture to minimize texture binding calls.
     */
    private void renderVisibleChunks(Collection<Chunk> visibleChunks) {
        Matrix4f modelMatrix = reusableMatrix4f;
        int lastBoundVaoId = 0;

        // Reset texture binding state
        currentlyBoundTexture = null;

        // Clear reusable collections
        textureToChunkDataMap.clear();
        reusableRenderableDataList.clear();

        // First pass: collect all renderable data and group by texture
        for (Chunk chunk : visibleChunks) {
            chunkMeshManager.ensureMeshForChunk(chunk);
            Map<String, ChunkMesh> meshesForChunk = chunkMeshManager.getMeshesForChunk(chunk.getPosition());
            if (meshesForChunk == null || meshesForChunk.isEmpty()) {
                continue;
            }

            Vec3i chunkOriginWorld = CoordinateUtils.chunkOriginToWorldCoords(chunk.getPosition());

            for (Map.Entry<String, ChunkMesh> meshEntry : meshesForChunk.entrySet()) {
                ChunkMesh chunkMeshToRender = meshEntry.getValue();
                if (chunkMeshToRender.isEmpty()) continue;

                String textureName = meshEntry.getKey();
                RenderableChunkData renderableData = new RenderableChunkData(
                    chunk, textureName, chunkMeshToRender, chunkOriginWorld
                );

                // Group by texture
                textureToChunkDataMap.computeIfAbsent(textureName, k -> new ArrayList<>()).add(renderableData);
            }
        }

        // Second pass: render grouped by texture to minimize texture switches
        for (Map.Entry<String, List<RenderableChunkData>> textureGroup : textureToChunkDataMap.entrySet()) {
            String textureName = textureGroup.getKey();
            List<RenderableChunkData> renderableDataList = textureGroup.getValue();

            // Bind texture once for all meshes using this texture
            bindTextureIfNeeded(textureName);

            // Render all meshes for this texture
            for (RenderableChunkData renderableData : renderableDataList) {
                // Set model matrix for this chunk
                modelMatrix.identity().translation(
                    renderableData.chunkOriginWorld.x,
                    renderableData.chunkOriginWorld.y,
                    renderableData.chunkOriginWorld.z
                );
                defaultShaderProgram.setUniform("model", modelMatrix);

                // Bind VAO if different from last bound
                int vaoId = renderableData.mesh.getVaoId();
                if (vaoId != lastBoundVaoId) {
                    glBindVertexArray(vaoId);
                    lastBoundVaoId = vaoId;
                }

                // Render the mesh
                if (wireframeMode) {
                    wireframeRenderer.render(renderableData.mesh);
                } else {
                    renderableData.mesh.render();
                }

                renderStats.addIndices(renderableData.mesh.getIndexCount());
                renderStats.incrementDrawCalls();
            }
        }

        if (lastBoundVaoId != 0) {
            glBindVertexArray(0);
        }
    }

    /**
     * Binds a texture only if it's different from the currently bound texture.
     * This avoids redundant texture binding calls.
     * 
     * @param textureName The name of the texture to bind
     */
    private void bindTextureIfNeeded(String textureName) {
        if (!textureName.equals(currentlyBoundTexture)) {
            Texture textureToRender = textureManager.getTexture(textureName);
            if (textureToRender == null) {
                textureToRender = textureManager.getDefaultFallbackTexture();
                if (textureToRender == null) {
                    logger.warn("No texture found for '{}' and no fallback texture available", textureName);
                    return;
                }
            }

            textureToRender.bind(0);
            currentlyBoundTexture = textureName;
        }
    }

    /**
     * Cleans up all resources used by the renderer and its managers.
     */
    public void cleanup() {
        logger.info("Cleaning up Renderer resources...");
        chunkMeshManager.cleanup();
        textureManager.cleanup();

        if (defaultShaderProgram != null) {
            defaultShaderProgram.cleanup();
        }
        if (debugCallback != null) {
            debugCallback.free();
        }
        if (occlusionCuller != null) {
            occlusionCuller.clearOpaqueCache();
        }
        logger.info("Renderer cleanup complete.");
    }

    public Camera getCamera() {
        return camera;
    }

    public int getDrawCallsLastFrame() {
        return renderStats.getDrawCalls();
    }

    public int getTotalIndicesRenderedLastFrame() {
        return renderStats.getTotalIndicesRendered();
    }

    public int getOcclusionCulledChunksLastFrame() {
        return renderStats.getOcclusionCulledChunks();
    }

    public int getFrustumCulledChunksLastFrame() {
        return renderStats.getFrustumCulledChunks();
    }

    public int getActiveMeshCount() {
        return chunkMeshManager.getActiveMeshCount();
    }

    public void toggleWireframeMode() {
        wireframeMode = !wireframeMode;
        logger.info("Wireframe mode: {}", wireframeMode ? "enabled" : "disabled");
    }

    public boolean isWireframeMode() {
        return wireframeMode;
    }

    /**
     * Releases any renderer-specific resources associated with a chunk, such as its meshes and AABB.
     * @param chunkPos The position of the chunk whose resources are to be released.
     */
    public void releaseChunkResources(ChunkPos chunkPos) {
        chunkMeshManager.releaseChunkResources(chunkPos);
    }

    /**
     * Updates the time of day, which affects lighting and fog.
     * @param timeOfDay The new time of day, from 0.0 (midnight) to 1.0 (next midnight).
     */
    public void updateTimeOfDay(float timeOfDay) {
        sceneLightingManager.updateTimeOfDay(timeOfDay);
    }

    /**
     * Sets the threshold for scene lighting recalculation.
     * @param threshold The new threshold value (recommended range: 0.001f to 0.1f)
     */
    public void setLightingCalculationThreshold(float threshold) {
        sceneLightingManager.setTimeCalculationThreshold(threshold);
    }

    /**
     * Gets the current lighting calculation threshold.
     * @return The current threshold value
     */
    public float getLightingCalculationThreshold() {
        return sceneLightingManager.getTimeCalculationThreshold();
    }

    /**
     * Gets performance metrics for scene lighting calculations.
     * @return Performance metrics as a formatted string
     */
    public String getLightingPerformanceMetrics() {
        return sceneLightingManager.getPerformanceMetrics();
    }

    /**
     * Gets the lighting recalculation rate as a percentage.
     * @return Percentage of update calls that resulted in actual recalculation
     */
    public double getLightingRecalculationRate() {
        return sceneLightingManager.getRecalculationRate();
    }

    /**
     * Gets the average lighting recalculation time in milliseconds.
     * @return Average time spent on lighting recalculations
     */
    public double getAverageLightingRecalculationTimeMs() {
        return sceneLightingManager.getAverageRecalculationTimeMs();
    }

    /**
     * Gets the number of lighting cache hits.
     * @return Number of times cached sin values were used
     */
    public long getLightingCacheHits() {
        return sceneLightingManager.getCacheHits();
    }

    /**
     * Resets lighting performance metrics for benchmarking.
     */
    public void resetLightingPerformanceMetrics() {
        sceneLightingManager.resetPerformanceMetrics();
    }
}
