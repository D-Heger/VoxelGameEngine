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
import de.heger.voxelengine.renderer.mesh.MeshData;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Future;

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
    private static final int MESH_BUILDER_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    // Static light direction to avoid allocations in render loop
    private static final Vector3f DEFAULT_LIGHT_DIR = new Vector3f(-0.5f, -1.0f, -0.5f);
    private static final String FALLBACK_TEXTURE_NAME = "core:block/dirt";
    private Texture defaultFallbackTexture = null;

    private float currentTimeOfDay = 0.5f; // 0.0 (midnight) to 1.0 (next midnight), 0.5 is midday

    // Define light properties for key times
    private final Vector3f middayLightColor = new Vector3f(1.0f, 0.95f, 0.8f);
    private final Vector3f midnightLightColor = new Vector3f(0.05f, 0.05f, 0.15f); // Bluish moonlight
    private final float middayAmbientStrength = 0.8f;
    private final float midnightAmbientStrength = 0.15f;
    private final Vector3f middayAmbientColor = new Vector3f(0.6f, 0.6f, 0.6f);
    private final Vector3f midnightAmbientColor = new Vector3f(0.05f, 0.05f, 0.1f);

    // Define fog colors for key times
    private final Vector3f middayFogColor = new Vector3f(0.5f, 0.6f, 0.7f);
    private final Vector3f midnightFogColor = new Vector3f(0.0f, 0.0f, 0.05f);

    // OPTIMIZATION: Reusable objects to reduce allocations
    private final Vector3f reusableVector3f1 = new Vector3f();
    private final Vector3f reusableVector3f2 = new Vector3f();
    private final Vector3f reusableVector3f3 = new Vector3f();
    private final Matrix4f reusableMatrix4f = new Matrix4f();
    private final Set<ChunkPos> reusableChunkPosSet = new HashSet<>();
    private final Set<Chunk> reusableChunkSet = new HashSet<>();
    private final List<Chunk> reusableChunkList = new ArrayList<>();
    
    // OPTIMIZATION: Cache lighting calculations
    private float lastCalculatedTimeOfDay = -1.0f;
    private final Vector3f cachedLightColor = new Vector3f();
    private final Vector3f cachedAmbientColor = new Vector3f();
    private final Vector3f cachedFogColor = new Vector3f();
    private float cachedAmbientStrength = 0.0f;
    
    // OPTIMIZATION: Threshold for time-based recalculation
    private static final float TIME_CALCULATION_THRESHOLD = 0.001f;

    // For asynchronous mesh building
    private ExecutorService meshBuilderExecutor;
    private BlockingQueue<CompletedMeshData> completedMeshDataQueue;
    private final Map<ChunkPos, Future<?>> pendingMeshTasks = new HashMap<>(); // To avoid re-submitting tasks

    // Helper class to store result from mesh building threads
    private static class CompletedMeshData {
        final ChunkPos chunkPos;
        final Map<String, MeshData> meshDataMap;
        final boolean isEmpty; // Indicates if the generated mesh data resulted in no renderable geometry

        CompletedMeshData(ChunkPos chunkPos, Map<String, MeshData> meshDataMap) {
            this.chunkPos = chunkPos;
            this.meshDataMap = meshDataMap;
            boolean trulyEmpty = true;
            if (meshDataMap != null) {
                for (MeshData md : meshDataMap.values()) {
                    if (!md.isEmpty()) {
                        trulyEmpty = false;
                        break;
                    }
                }
            }
            this.isEmpty = trulyEmpty;
        }
    }

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

        // Initial clear color, will be updated by fog in shader / dynamic clear later if needed
        Vector3f initialFogColor = reusableVector3f1;
        interpolateVector3f(middayFogColor, midnightFogColor, (float) (Math.sin(this.currentTimeOfDay * Math.PI)), initialFogColor);
        glClearColor(initialFogColor.x, initialFogColor.y, initialFogColor.z, 1.0f);

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

        this.meshBuilderExecutor = Executors.newFixedThreadPool(MESH_BUILDER_THREADS, r -> {
            Thread t = new Thread(r);
            t.setName("MeshBuilderThread-" + t.getId());
            t.setDaemon(true); // Allow JVM to exit if only daemon threads are running
            return t;
        });
        this.completedMeshDataQueue = new LinkedBlockingQueue<>();

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

        // Attempt to set the default fallback texture
        this.defaultFallbackTexture = textureMap.get(FALLBACK_TEXTURE_NAME);
        if (this.defaultFallbackTexture == null) {
            logger.warn("Configured default fallback texture '{}' not found in texture map. Submeshes with missing textures might not be rendered if their specific texture is also missing.", FALLBACK_TEXTURE_NAME);
            // If FALLBACK_TEXTURE_NAME is not found, defaultFallbackTexture will remain null.
            // This means if a chunk tries to use a missing texture, and the designated fallback is also missing, that part of the chunk won't render.
        } else {
            logger.info("Default fallback texture set to: '{}'", FALLBACK_TEXTURE_NAME);
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

        // Fog uniforms
        defaultShaderProgram.createUniform("fogColor");
        defaultShaderProgram.createUniform("fogStart");
        defaultShaderProgram.createUniform("fogEnd");

        logger.debug("Created uniforms for default shader program.");
    }

    public void clear() {
        // Clear the framebuffer (color and depth)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * OPTIMIZATION: Calculate lighting only when time changes significantly
     */
    private void updateLightingIfNeeded() {
        if (Math.abs(currentTimeOfDay - lastCalculatedTimeOfDay) < TIME_CALCULATION_THRESHOLD) {
            return; // Skip recalculation if time hasn't changed enough
        }
        
        // Calculate time-based light intensity (0 at midnight, 1 at midday)
        float lightIntensityFactor = (float) Math.sin(this.currentTimeOfDay * Math.PI);
        // Ensure factor is clamped between 0 and 1, as sin can go slightly outside due to precision with PI.
        lightIntensityFactor = Math.max(0.0f, Math.min(1.0f, lightIntensityFactor));

        // Interpolate light properties
        interpolateVector3f(midnightLightColor, middayLightColor, lightIntensityFactor, cachedLightColor);
        cachedAmbientStrength = midnightAmbientStrength + (middayAmbientStrength - midnightAmbientStrength) * lightIntensityFactor;
        interpolateVector3f(midnightAmbientColor, middayAmbientColor, lightIntensityFactor, cachedAmbientColor);
        interpolateVector3f(midnightFogColor, middayFogColor, lightIntensityFactor, cachedFogColor);
        
        lastCalculatedTimeOfDay = currentTimeOfDay;
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

        // Process completed mesh data from background threads
        processCompletedMeshData();

        // OPTIMIZATION: Update lighting only when needed
        updateLightingIfNeeded();
        
        // Update glClearColor for the background, in case fog doesn't reach infinity
        // This is often done if there's no skybox and fog is the primary background.
        glClearColor(cachedFogColor.x, cachedFogColor.y, cachedFogColor.z, 1.0f);

        drawCallsLastFrame = 0;
        totalIndicesRenderedLastFrame = 0;
        frustumCulledChunksLastFrame = 0;
        occlusionCulledChunksLastFrame = 0;

        defaultShaderProgram.bind();
        Matrix4f currentViewMatrix = camera.getViewMatrix();
        defaultShaderProgram.setUniform("projection", camera.getProjectionMatrix());
        defaultShaderProgram.setUniform("view", currentViewMatrix);

        // Set lighting uniforms using cached values
        defaultShaderProgram.setUniform("lightDir", DEFAULT_LIGHT_DIR);
        defaultShaderProgram.setUniform("lightColor", cachedLightColor);
        defaultShaderProgram.setUniform("ambientColor", cachedAmbientColor);
        defaultShaderProgram.setUniform("ambientStrength", cachedAmbientStrength);
        defaultShaderProgram.setUniform("uTexture", 0); // Tell shader to use texture unit 0

        // Set new lighting uniforms
        defaultShaderProgram.setUniform("viewPos", camera.getPosition());

        // Set fog uniforms
        float cameraViewDistance = camera.getViewDistance();
        float fogStartDistance = cameraViewDistance * 0.60f;

        defaultShaderProgram.setUniform("fogColor", cachedFogColor);
        defaultShaderProgram.setUniform("fogStart", fogStartDistance);
        defaultShaderProgram.setUniform("fogEnd", cameraViewDistance);

        // OPTIMIZATION: Reuse matrix instead of creating new one
        Matrix4f modelMatrix = reusableMatrix4f; // Reused for each chunk
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

        // OPTIMIZATION: Reuse collections
        reusableChunkPosSet.clear();

        // Apply frustum culling first to create a list of potentially visible chunks
        // OPTIMIZATION: Reuse collection
        reusableChunkSet.clear();

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
            reusableChunkSet.add(chunk);
        }

        // Prepare a list of frustum-culled chunks and sort it by distance to camera (closest first)
        reusableChunkList.clear();
        reusableChunkList.addAll(reusableChunkSet);

        final Vector3f cameraPosForSorting = camera.getPosition(); // Capture camera position for sorting
        reusableChunkList.sort(Comparator.comparingDouble(aChunk -> { // Renamed lambda param for clarity
            if (aChunk == null) return Double.MAX_VALUE; // Should not happen if input `chunks` is clean
            Vec3i worldPos = CoordinateUtils.chunkOriginToWorldCoords(aChunk.getPosition());
            return cameraPosForSorting.distanceSquared(
                worldPos.x + Chunk.SIZE_X / 2f, 
                worldPos.y + Chunk.SIZE_Y / 2f, 
                worldPos.z + Chunk.SIZE_Z / 2f
            );
        }));

        Collection<Chunk> visibleChunks;
        if (occlusionCuller != null && USE_OCCLUSION_CULLING) {
            // Occlusion culler now takes the already sorted list.
            // The lambda within filterOccludedChunks is expected to set occlusionCulledChunksLastFrame.
            visibleChunks = occlusionCuller.filterOccludedChunks(
                    reusableChunkList, // Pass the sorted list
                    cameraPosForSorting, // Reuse captured camera position
                    camera.getFront(),
                    (occludedCount) -> this.occlusionCulledChunksLastFrame = occludedCount
            );
            // The 'occlusionCulledChunksLastFrame' is set by the lambda passed to filterOccludedChunks.
            // No need for: this.occlusionCulledChunksLastFrame = reusableChunkList.size() - visibleChunks.size();
        } else {
            visibleChunks = new ArrayList<>(reusableChunkList); // Use the sorted list directly (copy for safety if modification occurs later)
            this.occlusionCulledChunksLastFrame = 0; // No chunks culled by occlusion culling
        }

        // Process and render the visible chunks (now sorted closest to farthest)
        for (Chunk chunk : visibleChunks) {
            ChunkPos chunkPos = chunk.getPosition();
            reusableChunkPosSet.add(chunkPos);

            Map<String, ChunkMesh> meshesForChunk = activeChunkMeshes.get(chunkPos);
            ChunkMeshState currentMeshState = chunk.getMeshState();

            boolean needsRebuild = currentMeshState == ChunkMeshState.NEEDS_REBUILD;
            boolean isUpToDateButMissingMeshes = (currentMeshState == ChunkMeshState.UP_TO_DATE || currentMeshState == ChunkMeshState.EMPTY) && 
                                                 (meshesForChunk == null || meshesForChunk.isEmpty()) && 
                                                 currentMeshState != ChunkMeshState.EMPTY; // Don't rebuild if it was processed and found to be EMPTY
            
            // If it's truly EMPTY and has no meshes, that's fine, skip rebuild for that specific case.
            // The EMPTY state implies it was processed and found to have no geometry.
            // This block is redundant because currentMeshState != ChunkMeshState.EMPTY already handles it.
            // if (currentMeshState == ChunkMeshState.EMPTY && (meshesForChunk == null || meshesForChunk.isEmpty())) {
            //     isUpToDateButMissingMeshes = false; 
            // }

            if ((needsRebuild || isUpToDateButMissingMeshes) && !pendingMeshTasks.containsKey(chunkPos)) {
                if (meshesForChunk != null) { // Clean up any stray old meshes if they exist (should be rare for isUpToDateButMissingMeshes)
                    logger.debug("Chunk {} is {} or marked for rebuild and missing meshes, cleaning old GL meshes (if any) before async build.", chunkPos, currentMeshState);
                    for (ChunkMesh oldMesh : meshesForChunk.values()) {
                        oldMesh.cleanup();
                    }
                    activeChunkMeshes.remove(chunkPos); 
                }

                logger.debug("Submitting mesh data generation task for chunk {} (State: {}, MissingMeshes: {})...", chunkPos, currentMeshState, isUpToDateButMissingMeshes);
                chunk.setMeshState(ChunkMeshState.BUILDING_DATA); 

                Chunk finalChunk = chunk;
                final ChunkManager finalChunkManager = chunkManager; 
                final BlockRegistry finalBlockRegistry = blockRegistry; 

                Future<?> task = meshBuilderExecutor.submit(() -> {
                    try {
                        Map<String, MeshData> generatedData = ChunkMeshBuilder.generateMeshDataByTexture(finalChunk, finalChunkManager, finalBlockRegistry);
                        completedMeshDataQueue.put(new CompletedMeshData(finalChunk.getPosition(), generatedData));
                    } catch (InterruptedException e) {
                        logger.warn("Mesh building thread for chunk {} interrupted.", finalChunk.getPosition());
                        Thread.currentThread().interrupt(); 
                        // Ensure state is reset for the main thread to pick up
                        synchronized(finalChunk) { // Synchronize state change
                            finalChunk.setMeshState(ChunkMeshState.NEEDS_REBUILD); 
                        }
                    } catch (Exception e) {
                        logger.error("Error building mesh data for chunk {}", finalChunk.getPosition(), e);
                        // Ensure state is reset for the main thread to pick up
                        synchronized(finalChunk) { // Synchronize state change
                            finalChunk.setMeshState(ChunkMeshState.NEEDS_REBUILD); 
                        }
                    } finally {
                        pendingMeshTasks.remove(finalChunk.getPosition()); 
                    }
                });
                pendingMeshTasks.put(chunkPos, task);

            } else if (meshesForChunk == null && (currentMeshState == ChunkMeshState.UP_TO_DATE || currentMeshState == ChunkMeshState.EMPTY)) {
                // This specific else-if was part of the original problem statement logic that was too broad.
                // The more precise isUpToDateButMissingMeshes condition above handles the necessary cases.
                // Keeping this path empty or removing it unless a very specific scenario is identified.
                // logger.debug("Chunk {} is {} but has no active meshes. State handled by primary rebuild logic if necessary.", chunkPos, currentMeshState);
            }

            if (meshesForChunk == null || meshesForChunk.isEmpty()) {
                if (currentMeshState != ChunkMeshState.BUILDING_DATA &&
                    currentMeshState != ChunkMeshState.MESH_DATA_READY &&
                    currentMeshState != ChunkMeshState.NEEDS_REBUILD && /* Already handled by submission logic */
                    currentMeshState != ChunkMeshState.BUILDING) { /* Handled by processCompletedMeshData */
                    // If chunk is UP_TO_DATE or EMPTY but has no mesh, it's fine, means it's truly empty or was processed.
                } else if (meshesForChunk != null && meshesForChunk.isEmpty() && (currentMeshState == ChunkMeshState.UP_TO_DATE || currentMeshState == ChunkMeshState.EMPTY)) {
                    // This is fine, chunk has been processed and found to be empty or is up-to-date with empty mesh.
                }
                // Don't render if no meshes or still processing
                continue;
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
                    // For now, skip rendering this submesh if texture is missing to avoid GL errors
                    // or wrong textures.
                    if (this.defaultFallbackTexture != null) {
                        // logger.debug("Texture '{}' not found for chunk {}, submesh. Using default fallback texture.", textureName, chunkPos);
                        this.defaultFallbackTexture.bind(0);
                    } else {
                        // logger.warn("Texture '{}' not found for chunk {} and no default fallback texture is set. Skipping render for this submesh.", textureName, chunkPos);
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
        
        // OPTIMIZATION: Use iterator to avoid creating new entry set
        activeChunkMeshes.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            Chunk chunk = chunkManager.getChunk(pos); // Check if chunk still exists and its state

            boolean shouldEvict = false;
            if (chunk == null) { // Chunk is no longer loaded by ChunkManager
                shouldEvict = true;
                logger.debug("Evicting meshes for unloaded chunk {}.", pos);
            } else if (chunk.getMeshState() == ChunkMeshState.NEEDS_REBUILD) { // Chunk is loaded but marked for rebuild (e.g. block change)
                shouldEvict = true;
                // This log might be redundant if the rebuild submission path also logs cleanup.
                // logger.debug("Evicting meshes for chunk {} marked NEEDS_REBUILD, pending new build.", pos);
            }
            // Future consideration: Evict meshes for chunks that are loaded and UP_TO_DATE,
            // but haven't been rendered for a significant period (e.g., an LRU cache for meshes
            // of chunks not in `reusableChunkPosSet` over time).
            // For now, this simpler logic reduces churn from temporary culling.

            if (shouldEvict) {
                logger.debug("Evicting and cleaning GL meshes for chunk {}.", pos);
                for (ChunkMesh mesh : entry.getValue().values()) {
                    mesh.cleanup();
                }
                chunkAABBCache.remove(pos);

                Future<?> pendingTask = pendingMeshTasks.remove(pos);
                if (pendingTask != null && !pendingTask.isDone()) {
                    pendingTask.cancel(true); // Attempt to interrupt the task if it's running
                    logger.debug("Cancelled pending mesh build task for evicted chunk {}.", pos);
                }
                return true; // Remove from activeChunkMeshes
            }
            return false;
        });

        // defaultShaderProgram.unbind(); // Only unbind if no other render passes
        // follow
    }

    private void processCompletedMeshData() {
        CompletedMeshData completedData;
        while ((completedData = completedMeshDataQueue.poll()) != null) {
            ChunkPos chunkPos = completedData.chunkPos;
            Chunk chunk = chunkManager.getChunk(chunkPos); 

            if (chunk == null) {
                logger.warn("Completed mesh data for chunk {} but chunk is no longer loaded. Discarding.", chunkPos);
                // MeshData buffers are direct and managed by GC or their direct release if not wrapped by ChunkMesh.
                // If ChunkMesh was created, its cleanup would handle it. Here, MeshData is raw.
                // No active GL meshes to clean here as the chunk is gone, eviction should have handled them.
                pendingMeshTasks.remove(chunkPos); // Ensure it's removed if somehow still there (worker's finally should get it)
                continue;
            }
            
            // The pendingMeshTasks.remove for this task is handled in the worker thread's finally block.
            
            synchronized (chunk) { // Synchronize on the chunk object to protect its state
                ChunkMeshState currentState = chunk.getMeshState();

                if (currentState == ChunkMeshState.NEEDS_REBUILD) {
                    logger.debug("Chunk {} is marked NEEDS_REBUILD. Discarding completed mesh data for {} as it's stale.", chunkPos, chunkPos);
                    // Old meshes (if any) will be handled by the rebuild process or eviction.
                    // Don't alter activeChunkMeshes here based on stale data.
                    continue; 
                }

                // Regardless of new data, if there were old GL meshes, they are now stale or being replaced.
                // Remove and clean them up.
                Map<String, ChunkMesh> oldMeshesForChunk = activeChunkMeshes.remove(chunkPos);
                if (oldMeshesForChunk != null) {
                    logger.debug("Removed and cleaning up old GL meshes for chunk {} to process new mesh data.", chunkPos);
                    for (ChunkMesh oldMesh : oldMeshesForChunk.values()) {
                        oldMesh.cleanup();
                    }
                }

                if (completedData.isEmpty) {
                    // Mesh data from worker thread indicates no renderable geometry.
                    // Old meshes already removed and cleaned above.
                    chunk.setMeshState(ChunkMeshState.EMPTY);
                    logger.debug("Processed completed EMPTY mesh data for chunk {}. State set to EMPTY.", chunkPos);
                    continue; 
                }

                // If we reach here, completedData is NOT empty.
                if (currentState != ChunkMeshState.BUILDING_DATA && currentState != ChunkMeshState.BUILDING) {
                    //BUILDING could be a valid intermediate if this process was somehow re-entrant for the same chunk, though unlikely with pendingMeshTasks.
                    //Most common expected state here is BUILDING_DATA.
                    logger.warn("Chunk {} state was {} (expected BUILDING_DATA or BUILDING) when non-empty mesh data completed. Proceeding to build meshes, but this might indicate an unexpected state transition.", chunkPos, currentState);
                }
                
                chunk.setMeshState(ChunkMeshState.BUILDING); // Now we are building GL objects on the main thread

                Map<String, ChunkMesh> newMeshesForChunk = new HashMap<>();
                if (completedData.meshDataMap != null) { // Already checked completedData.isEmpty, so meshDataMap should have something.
                    for (Map.Entry<String, MeshData> entry : completedData.meshDataMap.entrySet()) {
                        MeshData md = entry.getValue();
                        if (!md.isEmpty()) { // Double check individual mesh data emptiness
                            // This is a GL call, must be on the main render thread
                            ChunkMesh newMesh = new ChunkMesh(md.getVertexBuffer(), md.getIndexBuffer());
                            if (!newMesh.isEmpty()) { // ChunkMesh constructor can also determine emptiness
                                newMeshesForChunk.put(entry.getKey(), newMesh);
                            } else {
                                // MeshData had content, but ChunkMesh decided it's empty.
                                logger.debug("MeshData for texture {} in chunk {} was not empty, but resulted in an empty ChunkMesh.", entry.getKey(), chunkPos);
                            }
                        }
                    }
                }

                if (!newMeshesForChunk.isEmpty()) {
                    activeChunkMeshes.put(chunkPos, newMeshesForChunk);
                    chunk.setMeshState(ChunkMeshState.UP_TO_DATE);
                    logger.debug("Created {} new submeshes for chunk {}. State: UP_TO_DATE", newMeshesForChunk.size(), chunkPos);
                } else {
                    // No renderable GL geometry was created from the (non-empty) mesh data, or original data was empty and handled above.
                    // activeChunkMeshes already had chunkPos removed.
                    chunk.setMeshState(ChunkMeshState.EMPTY);
                    logger.debug("No renderable geometry created for chunk {} after mesh data processing (all sub-meshes from data were empty). State: EMPTY", chunkPos);
                }
            } // end synchronized (chunk)
            // pendingMeshTasks.remove(chunkPos); // Already handled by the worker thread's finally block.
        }
    }

    public void cleanup() {
        logger.info("Cleaning up Renderer resources...");

        // Shutdown mesh builder executor
        if (meshBuilderExecutor != null) {
            logger.debug("Shutting down mesh builder executor...");
            meshBuilderExecutor.shutdown();
            try {
                // Wait a while for existing tasks to terminate
                if (!meshBuilderExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    meshBuilderExecutor.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!meshBuilderExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                        logger.error("Mesh builder executor did not terminate.");
                }
            } catch (InterruptedException ie) {
                meshBuilderExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.debug("Mesh builder executor shut down.");
        }

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

    // Method to update time of day
    public void updateTimeOfDay(float timeOfDay) {
        this.currentTimeOfDay = timeOfDay % 1.0f; // Ensure it wraps around 0-1
    }

    // Helper for interpolation
    private void interpolateVector3f(Vector3f start, Vector3f end, float factor, Vector3f result) {
        result.x = start.x + (end.x - start.x) * factor;
        result.y = start.y + (end.y - start.y) * factor;
        result.z = start.z + (end.z - start.z) * factor;
    }
}
