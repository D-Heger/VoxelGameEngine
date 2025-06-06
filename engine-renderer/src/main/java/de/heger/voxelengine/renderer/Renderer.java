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
            return new ArrayList<>(reusableChunkList); // Return a copy
        }
    }

    private void renderVisibleChunks(Collection<Chunk> visibleChunks) {
        Matrix4f modelMatrix = reusableMatrix4f;
        int lastBoundVaoId = 0;

        for (Chunk chunk : visibleChunks) {
            chunkMeshManager.ensureMeshForChunk(chunk);
            Map<String, ChunkMesh> meshesForChunk = chunkMeshManager.getMeshesForChunk(chunk.getPosition());
            if (meshesForChunk == null || meshesForChunk.isEmpty()) {
                continue;
            }

            Vec3i chunkOriginWorld = CoordinateUtils.chunkOriginToWorldCoords(chunk.getPosition());
            modelMatrix.identity().translation(chunkOriginWorld.x, chunkOriginWorld.y, chunkOriginWorld.z);
            defaultShaderProgram.setUniform("model", modelMatrix);

            for (Map.Entry<String, ChunkMesh> meshEntry : meshesForChunk.entrySet()) {
                ChunkMesh chunkMeshToRender = meshEntry.getValue();
                if (chunkMeshToRender.isEmpty()) continue;

                Texture textureToRender = textureManager.getTexture(meshEntry.getKey());
                if (textureToRender == null) {
                    textureToRender = textureManager.getDefaultFallbackTexture();
                    if (textureToRender == null) continue; // Skip if no texture and no fallback
                }

                textureToRender.bind(0);

                int vaoId = chunkMeshToRender.getVaoId();
                if (vaoId != lastBoundVaoId) {
                    glBindVertexArray(vaoId);
                    lastBoundVaoId = vaoId;
                }

                if (wireframeMode) {
                    wireframeRenderer.render(chunkMeshToRender);
                } else {
                    chunkMeshToRender.render();
                }
                renderStats.addIndices(chunkMeshToRender.getIndexCount());
                renderStats.incrementDrawCalls();
            }
        }

        if (lastBoundVaoId != 0) {
            glBindVertexArray(0);
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
}
