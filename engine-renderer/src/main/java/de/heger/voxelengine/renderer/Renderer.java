package de.heger.voxelengine.renderer;

import de.heger.voxelengine.assets.texture.TextureData;
import de.heger.voxelengine.assets.texture.TextureLoader;
import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.culling.AABB;
import de.heger.voxelengine.renderer.culling.FrustumCuller;
import de.heger.voxelengine.renderer.mesh.Mesh;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import de.heger.voxelengine.renderer.texture.Texture;
import de.heger.voxelengine.world.block.BlockProperties;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.block.TextureRef;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.CoordinateUtils;
import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.world.chunk.Direction;
import de.heger.voxelengine.world.chunk.ChunkManager;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL11.*;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Renderer {

    private static final LoggerFacade logger = LoggerFacade.get(Renderer.class);

    private final Window window;
    private GLCapabilities capabilities;
    private Callback debugCallback;
    private ShaderProgram defaultShaderProgram;
    private Camera camera;
    private Mesh cubeMesh; // Keep for testing for now
    private TextureLoader textureLoader;
    private Map<String, Texture> textureMap; // Stores textures loaded based on BlockRegistry
    private FrustumCuller frustumCuller; // Add FrustumCuller field

    private Map<Direction, Mesh> faceMeshes; // For face culling
    private ChunkManager chunkManager; // For neighbor chunk access
    private BlockRegistry blockRegistry; // For block properties access

    public Renderer(Window window) {
        this.window = window;
        this.camera = new Camera();
        this.textureLoader = new TextureLoader();
        this.textureMap = new HashMap<>();
        this.faceMeshes = new EnumMap<>(Direction.class); // Initialize faceMeshes map
        this.chunkManager = ChunkManager.getInstance(); // Get ChunkManager instance
        this.blockRegistry = BlockRegistry.getInstance(); // Get BlockRegistry instance
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

        // Initialize simple cube mesh (now uses UVs) - Keep for testing
        cubeMesh = Mesh.createCube();

        // Initialize face meshes for face culling
        logger.info("Initializing face meshes for culling...");
        try {
            faceMeshes.put(Direction.UP, Mesh.createUpFace());
            faceMeshes.put(Direction.DOWN, Mesh.createDownFace());
            faceMeshes.put(Direction.NORTH, Mesh.createNorthFace());
            faceMeshes.put(Direction.SOUTH, Mesh.createSouthFace());
            faceMeshes.put(Direction.WEST, Mesh.createWestFace());
            faceMeshes.put(Direction.EAST, Mesh.createEastFace());
            logger.info("Face meshes initialized.");
        } catch (Exception e) {
            logger.error("Failed to initialize face meshes", e);
            throw new RuntimeException("Failed to initialize face meshes", e);
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

            // Derive resource path from name (e.g., "core:block/dirt" -> "textures/block/dirt.png")
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
                    logger.error("Failed to load texture data for '{}' from path '{}'. Check resource path and file.", textureName, resourcePath);
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
     * @param textureName The texture name from the registry.
     * @return The resource path string, or null if derivation fails.
     */
    private String deriveTextureResourcePath(String textureName) {
        if (textureName == null || !textureName.contains(":")) {
            logger.warn("Texture name '{}' is null or missing namespace separator ':'. Cannot derive path.", textureName);
            return null;
        }
        // Assuming format "namespace:type/name"
        String[] parts = textureName.split(":", 2);
        if (parts.length < 2 || !parts[1].contains("/")) {
             logger.warn("Texture name '{}' does not match expected format 'namespace:type/name'. Cannot derive path.", textureName);
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
        logger.debug("Created uniforms for default shader program.");
    }

    public void clear() {
        // Clear the framebuffer (color and depth)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    // Example render method (will be expanded later) - Modified to use textureMap
    public void render() {
         // Check if resources are initialized
         if (defaultShaderProgram != null && cubeMesh != null && !textureMap.isEmpty()) {
             defaultShaderProgram.bind();

             // Set camera-based uniforms
             Matrix4f viewMatrix = camera.getViewMatrix();
             defaultShaderProgram.setUniform("projection", camera.getProjectionMatrix());
             defaultShaderProgram.setUniform("view", viewMatrix);

             // Set lighting uniforms
             defaultShaderProgram.setUniform("lightDir", new Vector3f(-0.5f, -1.0f, -0.5f)); // Sunlight direction
             defaultShaderProgram.setUniform("lightColor", new Vector3f(1.0f, 0.95f, 0.8f)); // Slightly warm sunlight
             defaultShaderProgram.setUniform("ambientColor", new Vector3f(0.4f, 0.5f, 0.6f)); // Slightly blue-tinted ambient
             defaultShaderProgram.setUniform("ambientStrength", 0.6f); // Increased ambient strength

             // Compute rotating model matrix for test cube
             double time = glfwGetTime();
             Matrix4f modelMatrix = new Matrix4f()
                     .identity()
                     .rotateY((float)(time * Math.toRadians(50.0f))); // Rotate around Y
                     // .rotateX((float)(time * Math.toRadians(20.0f))); // Optional: Add X rotation
             defaultShaderProgram.setUniform("model", modelMatrix);

             // --- Use a texture from the map for the test cube ---
             // Try to get dirt, fallback to the first available texture if dirt isn't loaded
             Texture textureToRender = textureMap.get("core:block/dirt");
             if (textureToRender == null && !textureMap.isEmpty()) {
                 // Fallback to the first texture in the map if dirt is missing
                 Texture fallbackTexture = textureMap.values().iterator().next();
                 logger.warn("Texture 'core:block/dirt' not found, rendering test cube with fallback: {}",
                           textureMap.entrySet().stream().filter(entry -> entry.getValue() == fallbackTexture).findFirst().map(Map.Entry::getKey).orElse("Unknown"));
                 textureToRender = fallbackTexture;
             }

             if (textureToRender != null) {
                 // Bind texture to texture unit 0
                 textureToRender.bind(0);
                 // Tell shader to use texture unit 0 for uTexture sampler
                 defaultShaderProgram.setUniform("uTexture", 0);

                 // Render the mesh
                 cubeMesh.render();
             } else {
                  logger.error("No textures available to render the test cube.");
             }


             // Unbind shader (optional, good practice)
             defaultShaderProgram.unbind();
             // Unbind texture (less common, binding 0 is safer if needed)
             // glBindTexture(GL_TEXTURE_2D, 0);
         } else {
             logger.warn("Attempted to render before resources (shader, mesh, textures) were fully initialized.");
         }
    }

    /**
     * Renders the blocks within the given list of chunks. (P3-T2.4, P3-T2.5)
     * This is a basic implementation rendering a separate cube for each block.
     *
     * @param chunks The list of chunks to render.
     */
    public void renderChunks(Collection<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty() || defaultShaderProgram == null || faceMeshes.isEmpty() || textureMap.isEmpty() || blockRegistry == null) {
            return;
        }

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

        Matrix4f modelMatrix = new Matrix4f();
        Matrix4f viewProjectionMatrix = new Matrix4f(camera.getProjectionMatrix()).mul(currentViewMatrix);

        if (this.frustumCuller == null) {
            this.frustumCuller = new FrustumCuller(viewProjectionMatrix);
        } else {
            this.frustumCuller.updateViewProjectionMatrix(viewProjectionMatrix);
        }

        for (Chunk chunk : chunks) {
            if (chunk == null) continue;

            AABB chunkAABB = AABB.fromChunk(chunk);
            if (!frustumCuller.testAABB(chunkAABB)) {
                continue;
            }

            ChunkPos chunkPos = chunk.getPosition();
            Vec3i chunkOriginWorld = CoordinateUtils.chunkOriginToWorldCoords(chunkPos);

            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    for (int x = 0; x < Chunk.SIZE_X; x++) {
                        Vec3i localPos = new Vec3i(x, y, z);
                        BlockProperties currentBlockProps = chunk.getBlockProperties(localPos);

                        if (currentBlockProps != null && currentBlockProps.getId() != BlockRegistry.AIR.getId()) {
                            for (Direction faceDir : Direction.values()) {
                                if (isFaceVisible(chunk, localPos, faceDir)) {
                                    TextureRef texRef = currentBlockProps.getTexture(faceDir);
                                    Texture textureToRender = null;
                                    if (texRef != null && texRef.getName() != null) {
                                        textureToRender = textureMap.get(texRef.getName());
                                    }

                                    if (textureToRender == null) {
                                        // logger.warn("Texture not found for block '{}' face {}, texRef: '{}'. Skipping face.", 
                                        //             currentBlockProps.getName(), faceDir, (texRef != null ? texRef.getName() : "null"));
                                        continue; // Skip rendering this face if texture is missing
                                    }
                                    textureToRender.bind(0);

                                    Mesh faceMesh = faceMeshes.get(faceDir);
                                    if (faceMesh == null) { // Should not happen if initialized correctly
                                        logger.error("Face mesh for direction {} is null!", faceDir);
                                        continue;
                                    }

                                    float blockWorldX = chunkOriginWorld.x + x + 0.5f;
                                    float blockWorldY = chunkOriginWorld.y + y + 0.5f;
                                    float blockWorldZ = chunkOriginWorld.z + z + 0.5f;

                                    modelMatrix.identity().translation(blockWorldX, blockWorldY, blockWorldZ);
                                    defaultShaderProgram.setUniform("model", modelMatrix);

                                    faceMesh.render();
                                }
                            }
                        }
                    }
                }
            }
        }
        // defaultShaderProgram.unbind(); // Only unbind if no other render passes follow
    }

    /**
     * Determines if a specific face of a block is visible.
     * A face is visible if the block in the direction of the face is transparent.
     * It's also visible if the neighboring location is outside loaded chunks.
     *
     * @param currentChunk The chunk containing the block whose face is being checked.
     * @param localBlockPosInCurrentChunk The local coordinates (0-15) of the block within its chunk.
     * @param faceDir The direction of the face being checked (e.g., Direction.UP).
     * @return true if the face is visible, false otherwise.
     */
    private boolean isFaceVisible(Chunk currentChunk, Vec3i localBlockPosInCurrentChunk, Direction faceDir) {
        Vec3i neighborOffset = faceDir.getOffset();
        int neighborLocalX = localBlockPosInCurrentChunk.x + neighborOffset.x;
        int neighborLocalY = localBlockPosInCurrentChunk.y + neighborOffset.y;
        int neighborLocalZ = localBlockPosInCurrentChunk.z + neighborOffset.z;

        BlockProperties neighborBlockProps;

        if (neighborLocalX >= 0 && neighborLocalX < Chunk.SIZE_X &&
            neighborLocalY >= 0 && neighborLocalY < Chunk.SIZE_Y &&
            neighborLocalZ >= 0 && neighborLocalZ < Chunk.SIZE_Z) {
            // Neighbor is within the same chunk
            neighborBlockProps = currentChunk.getBlockProperties(new Vec3i(neighborLocalX, neighborLocalY, neighborLocalZ));
        } else {
            // Neighbor is in an adjacent chunk
            Vec3i currentBlockWorldPos = CoordinateUtils.localToWorldCoords(currentChunk.getPosition(), localBlockPosInCurrentChunk);
            Vec3i neighborBlockWorldPos = new Vec3i(
                currentBlockWorldPos.x + neighborOffset.x,
                currentBlockWorldPos.y + neighborOffset.y,
                currentBlockWorldPos.z + neighborOffset.z
            );

            ChunkPos neighborChunkPos = CoordinateUtils.worldToChunkCoords(neighborBlockWorldPos);
            Vec3i localPosInNeighborChunk = CoordinateUtils.worldToLocalCoords(neighborBlockWorldPos);

            Chunk neighborChunk = chunkManager.getChunk(neighborChunkPos);

            if (neighborChunk == null) {
                return true; // Neighbor chunk not loaded, face is visible (exposed to void)
            }
            neighborBlockProps = neighborChunk.getBlockProperties(localPosInNeighborChunk);
        }
        // neighborBlockProps is guaranteed by Chunk.getBlockProperties to return AIR if underlying is null
        return neighborBlockProps.isTransparent();
    }

    public void cleanup() {
        logger.info("Cleaning up Renderer resources...");

        // Cleanup shaders
        if (defaultShaderProgram != null) {
            defaultShaderProgram.cleanup();
            logger.debug("Cleaned up default shader program.");
        }

        // Cleanup meshes
        if (cubeMesh != null) {
            cubeMesh.cleanup();
            logger.debug("Cleaned up cube mesh.");
        }

        // Cleanup face meshes
        if (faceMeshes != null) {
            for (Map.Entry<Direction, Mesh> entry : faceMeshes.entrySet()) {
                try {
                    entry.getValue().cleanup();
                    logger.debug("Cleaned up face mesh for direction: {}", entry.getKey());
                } catch (Exception e) {
                    logger.error("Error cleaning up face mesh for direction: {}", entry.getKey(), e);
                }
            }
            faceMeshes.clear();
            logger.debug("Cleared face meshes map.");
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


        // Cleanup debug callback
        if (debugCallback != null) {
            debugCallback.free();
            logger.debug("Freed debug callback.");
        }

        // Destroy OpenGL context? Usually handled by Window class on close.
        // GL.destroy(); // Don't call this here, Window manages context lifetime

        logger.info("Renderer cleanup complete.");
    }

    public Camera getCamera() {
        return camera;
    }
}
