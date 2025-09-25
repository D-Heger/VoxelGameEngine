package de.heger.voxelengine.renderer;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.core.math.AABB;
import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.culling.FrustumCuller;
import de.heger.voxelengine.renderer.culling.OcclusionCuller;
import de.heger.voxelengine.renderer.debug.BlockOutlineRenderer;
import de.heger.voxelengine.renderer.debug.WireframeRenderer;
import de.heger.voxelengine.renderer.management.ChunkMeshManager;
import de.heger.voxelengine.renderer.management.RenderStats;
import de.heger.voxelengine.renderer.management.SceneLightingManager;
import de.heger.voxelengine.renderer.management.TextureManager;
import de.heger.voxelengine.renderer.mesh.ChunkMesh;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import de.heger.voxelengine.renderer.shader.UniformBuffer;
import de.heger.voxelengine.renderer.texture.Texture;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*; // Provides GL, GL11, GL13, GL14, GL20, GL30, etc.
import org.lwjgl.system.Callback;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * The main rendering class for the voxel engine. It orchestrates the entire rendering process,
 * from managing OpenGL state to drawing chunk meshes and handling scene properties.
 */
public class Renderer {

    private static final LoggerFacade logger = LoggerFacade.get(Renderer.class);

    // UBO constants
    private static final int UBO_BINDING_POINT_CAMERA = 0;
    private static final int UBO_BINDING_POINT_LIGHTING = 1;
    // mat4(64) * 2 + vec3(12) + pad(4) = 144 bytes
    private static final int CAMERA_UBO_SIZE = 144;
    // vec3(12)*3 + float(4)*5 + pad(4)*3 = 80 bytes
    private static final int LIGHTING_UBO_SIZE = 80;

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
    private BlockOutlineRenderer blockOutlineRenderer;
    private boolean wireframeMode = false;

    // UBOs for shader data
    private UniformBuffer cameraUBO;
    private UniformBuffer lightingUBO;

    private static final boolean USE_OCCLUSION_CULLING = true;

    // Reusable objects to reduce allocations in the render loop
    private final Matrix4f reusableMatrix4f = new Matrix4f();
    private final Matrix4f viewProjectionMatrixForCulling = new Matrix4f();
    private final List<Chunk> reusableChunkList = new ArrayList<>();
    private final List<Chunk> reusableOcclusionList = new ArrayList<>();
    private final List<RenderableChunkData> renderableDataPool = new ArrayList<>();
    private int renderableDataPoolIndex = 0;

    // Texture batching optimization - reusable collections
    private final Map<String, List<RenderableChunkData>> textureToChunkDataMap = new HashMap<>();
    private final List<RenderableChunkData> reusableRenderableDataList = new ArrayList<>();

    // Texture binding state tracking
    private String currentlyBoundTexture = null;

    // Shadow mapping resources (Cascaded Shadow Maps)
    private int shadowFbo;
    private int shadowDepthTexture;
    private static final int SHADOW_MAP_SIZE = 4096;
    private ShaderProgram depthShaderProgram;

    // Cascaded Shadow Map parameters
    private static final int NUM_CASCADES = 4; // We use a 4-split CSM
    private static final int CASCADE_ATLAS_SPLIT = 2; // 2x2 grid in atlas (supports up to 4 cascades)

    private final Matrix4f[] cascadeLightSpaceMatrices = new Matrix4f[NUM_CASCADES];
    private final Vector4f[] cascadeAtlasRects = new Vector4f[NUM_CASCADES];
    private final float[] cascadeSplits = new float[NUM_CASCADES];

    private final Matrix4f lightViewMatrix = new Matrix4f();

    /**
     * Helper class to store renderable chunk data for texture batching optimization.
     */
    private static class RenderableChunkData {
        ChunkMesh mesh;
        Vec3i chunkOriginWorld;

        RenderableChunkData(Chunk chunk, String textureName, ChunkMesh mesh, Vec3i chunkOriginWorld) {
            this.init(chunk, textureName, mesh, chunkOriginWorld);
        }

        void init(Chunk chunk, String textureName, ChunkMesh mesh, Vec3i chunkOriginWorld) {
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

        // Initialize GPU-based occlusion culler (Hierarchical-Z). Falls back to CPU heuristic internally.
        this.occlusionCuller = new de.heger.voxelengine.renderer.culling.HZBOcclusionCuller(
                this.camera,
                this.chunkMeshManager,
                (int) window.getWidth(),
                (int) window.getHeight()
        );

        // Initialize rendering tools
        this.wireframeRenderer = new WireframeRenderer();
        this.blockOutlineRenderer = new BlockOutlineRenderer();

        // Allocate cascade-related objects
        for (int i = 0; i < NUM_CASCADES; i++) {
            cascadeLightSpaceMatrices[i] = new Matrix4f();
            cascadeAtlasRects[i] = new Vector4f();
        }
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
            blockOutlineRenderer.init();
            createUBOs();
            initShadowMapResources();
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

        // Create uniforms for non-UBO data
        defaultShaderProgram.createUniform("model");
        defaultShaderProgram.createUniform("uTexture");
        defaultShaderProgram.createUniform("shadowMap");
        defaultShaderProgram.createUniform("specularStrength");
        defaultShaderProgram.createUniform("shininess");
        defaultShaderProgram.createUniform("uAtlasOffsetScale");

        // Cascaded shadow map uniforms
        for (int i = 0; i < NUM_CASCADES; i++) {
            defaultShaderProgram.createUniform("lightSpaceMatrices[" + i + "]");
            defaultShaderProgram.createUniform("cascadeAtlasRects[" + i + "]");
            if (i < NUM_CASCADES - 1) {
                defaultShaderProgram.createUniform("cascadeSplits[" + i + "]");
            }
        }
    }

    private void createUBOs() {
        logger.info("Creating Uniform Buffer Objects (UBOs)...");
        cameraUBO = new UniformBuffer(CAMERA_UBO_SIZE, UBO_BINDING_POINT_CAMERA);
        lightingUBO = new UniformBuffer(LIGHTING_UBO_SIZE, UBO_BINDING_POINT_LIGHTING);

        // Bind the newly created UBOs to the corresponding uniform blocks in the default shader.
        cameraUBO.bindToShader(defaultShaderProgram, "CameraData");
        lightingUBO.bindToShader(defaultShaderProgram, "Lighting");

        logger.info("UBOs created and bound to shader blocks.");
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
        renderableDataPoolIndex = 0; // Reset pool for the new frame

        // --- Compute light matrices for shadow mapping ---
        computeCascadedShadowMatrices();

        Vector3f fogColor = sceneLightingManager.getFogColor();
        glClearColor(fogColor.x, fogColor.y, fogColor.z, 1.0f);
        clear();

        setupShaderUniforms();

        // Pre-compute AABB for all chunks
        chunkMeshManager.precomputeAABBForChunk(chunks);

        Collection<Chunk> visibleChunks = performCulling(chunks);

        // Render depth from light's perspective first (shadow map)
        renderShadowPass(visibleChunks);

        // Render scene normally with shadow map applied
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

        // Update Camera UBO
        ByteBuffer cameraBuffer = cameraUBO.getBuffer();
        camera.getProjectionMatrix().get(cameraBuffer);
        cameraBuffer.position(64);
        camera.getViewMatrix().get(cameraBuffer);
        cameraBuffer.position(128);
        camera.getPosition().get(cameraBuffer);
        cameraBuffer.position(140); // to end of vec3
        // No need to flip, getBuffer clears and prepares for writing

        cameraUBO.updateData(cameraBuffer);

        // Update Lighting UBO
        ByteBuffer lightingBuffer = lightingUBO.getBuffer();
        sceneLightingManager.fillLightingUboBuffer(lightingBuffer, camera.getViewDistance());
        lightingUBO.updateData(lightingBuffer);

        // Set remaining non-UBO uniforms
        defaultShaderProgram.setUniform("uTexture", 0);
        defaultShaderProgram.setUniform("shadowMap", 1);

        // Upload cascaded shadow map uniforms
        for (int i = 0; i < NUM_CASCADES; i++) {
            defaultShaderProgram.setUniform("lightSpaceMatrices[" + i + "]", cascadeLightSpaceMatrices[i]);
            defaultShaderProgram.setUniform("cascadeAtlasRects[" + i + "]", cascadeAtlasRects[i]);
            if (i < NUM_CASCADES - 1) {
                defaultShaderProgram.setUniform("cascadeSplits[" + i + "]", cascadeSplits[i]);
            }
        }

        defaultShaderProgram.setUniform("specularStrength", 0.4f);
        defaultShaderProgram.setUniform("shininess", 32.0f);
        defaultShaderProgram.setUniform("uAtlasOffsetScale", new Vector4f(0f, 0f, 1f, 1f));

        // Bind shadow map texture to texture unit 1
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowDepthTexture);
        glActiveTexture(GL_TEXTURE0);
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

        // Occlusion cull the remaining chunks
        if (occlusionCuller != null && USE_OCCLUSION_CULLING) {
            return occlusionCuller.filterOccludedChunks(
                    reusableChunkList,
                    cameraPosForSorting,
                    camera.getFront(),
                    renderStats::setOcclusionCulledChunks
            );
        } else {
            // If occlusion culling is disabled we still sort chunks front-to-back for better depth testing.
            reusableChunkList.sort(Comparator.comparingDouble(chunk -> {
                Vec3i worldPos = CoordinateUtils.chunkOriginToWorldCoords(chunk.getPosition());
                return cameraPosForSorting.distanceSquared(
                        worldPos.x + Chunk.SIZE_X / 2f,
                        worldPos.y + Chunk.SIZE_Y / 2f,
                        worldPos.z + Chunk.SIZE_Z / 2f
                );
            }));

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
        defaultShaderProgram.bind();
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
                RenderableChunkData renderableData = borrowRenderableChunkData(
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

            // Bind atlas texture (only first call) and set per-texture UV transform
            bindTextureIfNeeded(textureName);
            float[] uv = textureManager.getAtlasTransform(textureName);
            if (uv != null) {
                defaultShaderProgram.setUniform("uAtlasOffsetScale", new org.joml.Vector4f(uv[0], uv[1], uv[2], uv[3]));
            } else {
                // Fallback: full atlas
                defaultShaderProgram.setUniform("uAtlasOffsetScale", new org.joml.Vector4f(0f, 0f, 1f, 1f));
            }

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

    private RenderableChunkData borrowRenderableChunkData(Chunk chunk, String textureName, ChunkMesh mesh, Vec3i chunkOriginWorld) {
        if (renderableDataPoolIndex < renderableDataPool.size()) {
            RenderableChunkData obj = renderableDataPool.get(renderableDataPoolIndex);
            obj.init(chunk, textureName, mesh, chunkOriginWorld);
            renderableDataPoolIndex++;
            return obj;
        } else {
            RenderableChunkData obj = new RenderableChunkData(chunk, textureName, mesh, chunkOriginWorld);
            renderableDataPool.add(obj);
            renderableDataPoolIndex++;
            return obj;
        }
    }

    private void bindTextureIfNeeded(String textureName) {
        int atlasId = textureManager.getAtlasTextureId();

        if (atlasId != 0) {
            // Atlas path – bind once per frame (or when not yet bound).
            if (!"ATLAS_BOUND".equals(currentlyBoundTexture)) {
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, atlasId);
                currentlyBoundTexture = "ATLAS_BOUND";
            }
        } else {
            // Legacy per-texture binding path.
            if (!textureName.equals(currentlyBoundTexture)) {
                Texture tex = textureManager.getTexture(textureName);
                if (tex == null) tex = textureManager.getDefaultFallbackTexture();
                if (tex != null) tex.bind(0);
                currentlyBoundTexture = textureName;
            }
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
        if (cameraUBO != null) {
            cameraUBO.cleanup();
        }
        if (lightingUBO != null) {
            lightingUBO.cleanup();
        }
        if (debugCallback != null) {
            debugCallback.free();
        }
        if (occlusionCuller != null) {
            occlusionCuller.clearOpaqueCache();
        }
        if (wireframeRenderer != null) {
            // nothing to cleanup
        }
        if (blockOutlineRenderer != null) {
            blockOutlineRenderer.cleanup();
        }
        if (depthShaderProgram != null) {
            depthShaderProgram.cleanup();
        }
        if (shadowDepthTexture != 0) {
            glDeleteTextures(shadowDepthTexture);
        }
        if (shadowFbo != 0) {
            glDeleteFramebuffers(shadowFbo);
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

    // -------------------------------------------------------------------------
    // Block Outline Rendering
    // -------------------------------------------------------------------------

    public void renderBlockOutline(Vec3i blockPos) {
        if (blockPos == null || blockOutlineRenderer == null) return;

        // Build model matrix translating the unit cube to block world position
        Matrix4f model = new Matrix4f().translation(blockPos.x, blockPos.y, blockPos.z);

        Matrix4f mvp = new Matrix4f(camera.getProjectionMatrix())
                .mul(camera.getViewMatrix())
                .mul(model);

        blockOutlineRenderer.render(mvp);
    }
    
    /**
     * Immediately rebuilds the mesh for the supplied chunk, blocking until the new GL meshes are
     * uploaded.  This is primarily intended for player-initiated edits where latency should be
     * minimal (e.g. block break / placement).
     *
     * @param chunk the chunk to rebuild; ignored if null.
     */
    public void rebuildChunkMeshImmediately(Chunk chunk) {
        if (chunk == null) return;
        chunkMeshManager.rebuildChunkImmediately(chunk);
    }

    private void initShadowMapResources() {
        logger.info("Initializing shadow mapping resources ({}x{})...", SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);

        // 1. Create depth texture
        shadowDepthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, shadowDepthTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH24_STENCIL8, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, 0,
                GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, (java.nio.ByteBuffer) null);
        // Use linear filtering so hardware automatically performs 2x2 PCF between neighbouring texels
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        // Set border color to 1.0 to ensure fragments outside shadow map are lit
        float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f};
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        // 2. Create framebuffer and attach depth texture
        shadowFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, shadowDepthTexture, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Shadow framebuffer is not complete: status=" + status);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // 3. Load depth-only shader program
        try {
            depthShaderProgram = new ShaderProgram();
            String vs = ShaderProgram.loadShaderSourceFromResources("/shaders/shadow_depth.vert");
            String fs = ShaderProgram.loadShaderSourceFromResources("/shaders/shadow_depth.frag");
            depthShaderProgram.createVertexShader(vs);
            depthShaderProgram.createFragmentShader(fs);
            depthShaderProgram.link();
            depthShaderProgram.createUniform("model");
            depthShaderProgram.createUniform("lightSpaceMatrix");
        } catch (Exception e) {
            logger.error("Failed to create depth shader program for shadow mapping", e);
            throw new RuntimeException(e);
        }

        logger.info("Shadow mapping resources initialized.");
    }

    /**
     * Computes the view-projection matrices for each cascade as well as the atlas UV rectangles
     * and distance splits used by the shaders.
     *
     * NOTE: This is a simplified implementation that approximates each cascade with a bounding
     * sphere around the camera. Although not as tight as the exhaustive frustum corner method,
     * it is sufficient for first-pass CSM support and keeps the math fairly lightweight.
     */
    private void computeCascadedShadowMatrices() {
        Vector3f lightDir = sceneLightingManager.getLightDirection();
        Vector3f cameraPos = camera.getPosition();

        // --- Calculate cascade split distances ---
        float nearPlane = 0.1f; // Matches Camera near plane
        float farPlane = camera.getViewDistance();
        float lambda = 0.95f; // Weighting between uniform and logarithmic split

        for (int i = 0; i < NUM_CASCADES; i++) {
            float p = (i + 1) / (float) NUM_CASCADES;
            float log = nearPlane * (float) Math.pow(farPlane / nearPlane, p);
            float uni = nearPlane + (farPlane - nearPlane) * p;
            cascadeSplits[i] = lambda * log + (1.0f - lambda) * uni;
        }

        // --- Calculate atlas rectangles (2x2 grid) ---
        float tileSize = 1.0f / CASCADE_ATLAS_SPLIT;
        for (int i = 0; i < NUM_CASCADES; i++) {
            int row = i / CASCADE_ATLAS_SPLIT;
            int col = i % CASCADE_ATLAS_SPLIT;
            cascadeAtlasRects[i].set(col * tileSize, row * tileSize, tileSize, tileSize);
        }

        // --- Compute view-projection matrix for each cascade ---
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f invLightDir = new Vector3f(lightDir).normalize().negate();

        float maxShadowDistance = 300.0f;

        int tileResolution = SHADOW_MAP_SIZE / CASCADE_ATLAS_SPLIT;

        Vector3f front = camera.getFront();
        Vector3f right = new Vector3f(front).cross(up).normalize();
        Vector3f camUp = new Vector3f(right).cross(front).normalize();

        float fovRad = (float) Math.toRadians(camera.getFov());
        float tanFov = (float) Math.tan(fovRad * 0.5f);

        float prevSplitDist = nearPlane;

        for (int i = 0; i < NUM_CASCADES; i++) {
            float splitDist = cascadeSplits[Math.min(i, NUM_CASCADES - 2)];

            // Build frustum slice corner points in world space
            float nearDist = prevSplitDist;
            float farDist = (i == NUM_CASCADES - 1) ? farPlane : splitDist;

            float nearHeight = nearDist * tanFov;
            float aspect = window.getAspectRatio();
            float nearWidth = nearHeight * aspect;
            float farHeight = farDist * tanFov;
            float farWidth = farHeight * aspect;

            Vector3f camPos = new Vector3f(cameraPos);

            Vector3f nc = new Vector3f(camPos).add(new Vector3f(front).mul(nearDist));
            Vector3f fc = new Vector3f(camPos).add(new Vector3f(front).mul(farDist));

            // 8 corners
            Vector3f[] corners = new Vector3f[8];
            corners[0] = new Vector3f(nc).sub(right.mul(nearWidth, new Vector3f())).add(camUp.mul(nearHeight, new Vector3f()));
            corners[1] = new Vector3f(nc).add(right.mul(nearWidth, new Vector3f())).add(camUp.mul(nearHeight, new Vector3f()));
            corners[2] = new Vector3f(nc).add(right.mul(nearWidth, new Vector3f())).sub(camUp.mul(nearHeight, new Vector3f()));
            corners[3] = new Vector3f(nc).sub(right.mul(nearWidth, new Vector3f())).sub(camUp.mul(nearHeight, new Vector3f()));

            corners[4] = new Vector3f(fc).sub(right.mul(farWidth, new Vector3f())).add(camUp.mul(farHeight, new Vector3f()));
            corners[5] = new Vector3f(fc).add(right.mul(farWidth, new Vector3f())).add(camUp.mul(farHeight, new Vector3f()));
            corners[6] = new Vector3f(fc).add(right.mul(farWidth, new Vector3f())).sub(camUp.mul(farHeight, new Vector3f()));
            corners[7] = new Vector3f(fc).sub(right.mul(farWidth, new Vector3f())).sub(camUp.mul(farHeight, new Vector3f()));

            // Compute center of slice
            Vector3f sliceCenter = new Vector3f();
            for (Vector3f c : corners) sliceCenter.add(c);
            sliceCenter.mul(1.0f / 8.0f);

            // Build light view matrix
            Vector3f lightPos = new Vector3f(sliceCenter).add(new Vector3f(invLightDir).mul(maxShadowDistance * 0.5f));
            lightViewMatrix.identity().lookAt(lightPos, sliceCenter, up);

            // Transform corners to light space & compute bounds
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

            for (Vector3f c : corners) {
                Vector4f tmp = new Vector4f(c, 1.0f).mul(lightViewMatrix);
                minX = Math.min(minX, tmp.x);
                minY = Math.min(minY, tmp.y);
                minZ = Math.min(minZ, tmp.z);
                maxX = Math.max(maxX, tmp.x);
                maxY = Math.max(maxY, tmp.y);
                maxZ = Math.max(maxZ, tmp.z);
            }

            // Make bounding box square to maintain texel stability
            float extentX = maxX - minX;
            float extentY = maxY - minY;
            float orthoExtent = Math.max(extentX, extentY) * 0.5f;

            Vector3f boxCenterLS = new Vector3f((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f);

            // Snap to texel grid in light space
            float texelSize = (orthoExtent * 2.0f) / tileResolution;
            boxCenterLS.x = (float) Math.floor(boxCenterLS.x / texelSize) * texelSize;
            boxCenterLS.y = (float) Math.floor(boxCenterLS.y / texelSize) * texelSize;

            // Build light projection
            Matrix4f lightProj = new Matrix4f().ortho(-orthoExtent, orthoExtent, -orthoExtent, orthoExtent, -maxShadowDistance, maxShadowDistance);

            // Reconstruct view matrix with snapped translation
            Matrix4f snappedView = new Matrix4f(lightViewMatrix);
            snappedView.m30(snappedView.m30() - boxCenterLS.x);
            snappedView.m31(snappedView.m31() - boxCenterLS.y);

            lightProj.mul(snappedView, cascadeLightSpaceMatrices[i]);

            prevSplitDist = farDist;
        }
    }

    private void renderShadowPass(Collection<Chunk> visibleChunks) {
        int tileSize = SHADOW_MAP_SIZE / CASCADE_ATLAS_SPLIT;

        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glClear(GL_DEPTH_BUFFER_BIT);

        Matrix4f modelMatrix = reusableMatrix4f;
        int lastVao = 0;

        depthShaderProgram.bind();

        for (int cascadeIndex = 0; cascadeIndex < NUM_CASCADES; cascadeIndex++) {
            // Configure viewport for this cascade inside the atlas
            int row = cascadeIndex / CASCADE_ATLAS_SPLIT;
            int col = cascadeIndex % CASCADE_ATLAS_SPLIT;
            glViewport(col * tileSize, row * tileSize, tileSize, tileSize);

            // Upload cascade-specific light space matrix
            depthShaderProgram.setUniform("lightSpaceMatrix", cascadeLightSpaceMatrices[cascadeIndex]);

            // Render all visible chunks for this cascade
            for (Chunk chunk : visibleChunks) {
                chunkMeshManager.ensureMeshForChunk(chunk);
                Map<String, ChunkMesh> meshes = chunkMeshManager.getMeshesForChunk(chunk.getPosition());
                if (meshes == null) continue;

                Vec3i chunkOriginWorld = CoordinateUtils.chunkOriginToWorldCoords(chunk.getPosition());
                modelMatrix.identity().translation(chunkOriginWorld.x, chunkOriginWorld.y, chunkOriginWorld.z);
                depthShaderProgram.setUniform("model", modelMatrix);

                for (ChunkMesh mesh : meshes.values()) {
                    if (mesh.isEmpty()) continue;
                    int vao = mesh.getVaoId();
                    if (vao != lastVao) {
                        glBindVertexArray(vao);
                        lastVao = vao;
                    }
                    mesh.render();
                }
            }
        }

        if (lastVao != 0) {
            glBindVertexArray(0);
        }

        depthShaderProgram.unbind();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Restore viewport to window size
        glViewport(0, 0, window.getWidth(), window.getHeight());
    }
}
