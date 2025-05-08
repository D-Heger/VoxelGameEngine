package de.heger.voxelengine.renderer;

import de.heger.voxelengine.assets.texture.TextureData;
import de.heger.voxelengine.assets.texture.TextureLoader;
import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.mesh.Mesh;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import de.heger.voxelengine.renderer.texture.Texture;
import de.heger.voxelengine.world.block.BlockProperties;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.block.TextureRef;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.CoordinateUtils;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL11.*;

import java.util.Collection;
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
    private Matrix4f projectionMatrix;
    private Mesh cubeMesh; // Keep for testing for now
    private TextureLoader textureLoader;
    // private Texture cubeTexture; // Replaced by textureMap
    private Map<String, Texture> textureMap; // Stores textures loaded based on BlockRegistry

    public Renderer(Window window) {
        this.window = window;
        this.camera = new Camera();
        this.textureLoader = new TextureLoader();
        this.textureMap = new HashMap<>(); // Initialize map
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
        //logger.info("GLSL Version: {}", glGetString(GL_SHADING_LANGUAGE_VERSION)); // GLSL version string requires GL 4.3+ or extension
        logger.info("Vendor: {}", glGetString(GL_VENDOR));
        logger.info("Renderer: {}", glGetString(GL_RENDERER));

        // Calculate initial projection matrix
        updateProjectionMatrix();

        // Load and compile shaders
        try {
            loadShaders();
        } catch (Exception e) {
            logger.error("Failed to load shaders", e);
            // Depending on the desired behavior, you might want to re-throw,
            // exit, or try loading fallback shaders.
            throw new RuntimeException("Failed to initialize shaders", e);
        }

        // Load Textures based on BlockRegistry
        try {
            logger.info("Loading textures based on BlockRegistry...");
            loadBlockTextures(); // New method to load textures
            logger.info("Finished loading block textures. {} textures loaded.", textureMap.size());
        } catch (Exception e) {
            logger.error("Failed to load block textures", e);
            throw new RuntimeException("Failed to initialize block textures", e);
        }


        // Initialize simple cube mesh (now uses UVs) - Keep for testing
        cubeMesh = Mesh.createCube();

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
        // defaultShaderProgram.createUniform("objectColor"); // Removed
        // defaultShaderProgram.createUniform("lightColor"); // Removed
        logger.debug("Created uniforms for default shader program.");

    }

    // Method to update projection matrix (e.g., on window resize)
    public void updateProjectionMatrix() {
        float aspectRatio = window.getAspectRatio();
        // Example: Perspective projection
        // FOV (field of view), aspect ratio, near plane, far plane
        projectionMatrix = new Matrix4f().perspective((float) Math.toRadians(45.0f), aspectRatio, 0.1f, 200.0f); // Increased far plane
        logger.debug("Projection matrix updated for aspect ratio: {}", aspectRatio);
        // If shader is already bound, update the uniform immediately
        // if (defaultShaderProgram != null /* && shader is bound */) {
        //     defaultShaderProgram.setUniform("projection", projectionMatrix);
        // }
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
             Matrix4f viewMatrix = camera.getViewMatrix(); // Get view matrix from camera
             defaultShaderProgram.setUniform("projection", projectionMatrix); // Use calculated projection matrix
             defaultShaderProgram.setUniform("view", viewMatrix); // Use camera's view matrix

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
        if (defaultShaderProgram == null || cubeMesh == null || textureMap.isEmpty() || chunks == null || chunks.isEmpty()) {
            // logger.trace("Skipping chunk rendering - resources not ready or no chunks.");
            return;
        }

        defaultShaderProgram.bind();
        // cubeTexture.bind(0); // Bind texture once for all blocks
        defaultShaderProgram.setUniform("uTexture", 0); // Set texture unit once

        // Set camera uniforms once
        Matrix4f viewMatrix = camera.getViewMatrix();
        defaultShaderProgram.setUniform("projection", projectionMatrix);
        defaultShaderProgram.setUniform("view", viewMatrix);

        // Iterate through chunks (P3-T2.4)
        for (Chunk chunk : chunks) {
            ChunkPos chunkPos = chunk.getPosition();
            // Calculate the world origin of this chunk
            Vector3f chunkWorldOrigin = new Vector3f(
                    chunkPos.x * CoordinateUtils.CHUNK_SIZE_X,
                    chunkPos.y * CoordinateUtils.CHUNK_SIZE_Y,
                    chunkPos.z * CoordinateUtils.CHUNK_SIZE_Z
            );

            // Iterate through blocks within the chunk
            for (int y = 0; y < CoordinateUtils.CHUNK_SIZE_Y; y++) {
                for (int z = 0; z < CoordinateUtils.CHUNK_SIZE_Z; z++) {
                    for (int x = 0; x < CoordinateUtils.CHUNK_SIZE_X; x++) {
                        short blockId = chunk.getBlock(x, y, z);
                        BlockProperties properties = BlockRegistry.getInstance().getBlock(blockId); // Get properties

                        // Render if not air (P3-T2.5) and properties are valid
                        if (properties != null && blockId != BlockRegistry.AIR.getId()) { // Use AIR constant
                            // --- Bind the correct texture ---
                            // For now, just use one texture (e.g., SOUTH face) for the whole cube
                            // Proper multi-texture blocks require meshing changes.
                            TextureRef texRef = properties.getTexture(de.heger.voxelengine.world.chunk.Direction.SOUTH); // Example: Use South face texture
                            Texture textureToRender = null;
                            if (texRef != null) {
                                textureToRender = textureMap.get(texRef.getName());
                            }

                            if (textureToRender == null) {
                                // Fallback or warning if texture not found for this block
                                // logger.warn("Texture not found for block ID {} (\'{}\'), skipping render.", blockId, properties.getName());
                                // Optionally bind a default 'missing' texture here
                                // For now, we just won't render it if the texture is missing
                                continue; 
                            }
                            
                            // Bind the specific texture for this block to unit 0
                            textureToRender.bind(0); 
                            // The uniform uTexture is already set to 0 outside the loop

                            // Calculate world position of the block's center
                            // Add 0.5f to center the cube on the block coordinate
                            float blockWorldX = chunkWorldOrigin.x + x + 0.5f;
                            float blockWorldY = chunkWorldOrigin.y + y + 0.5f;
                            float blockWorldZ = chunkWorldOrigin.z + z + 0.5f;

                            // Calculate model matrix for this block
                            Matrix4f modelMatrix = new Matrix4f().identity()
                                    .translate(blockWorldX, blockWorldY, blockWorldZ);
                                    // No scaling or rotation needed for basic blocks yet
                            defaultShaderProgram.setUniform("model", modelMatrix);

                            // Render the cube mesh
                            cubeMesh.render();
                        }
                    }
                }
            }
        }

        // Unbind shader (optional, good practice)
        defaultShaderProgram.unbind();
        // It's often good practice to unbind the texture from the last used unit,
        // binding texture 0 is a common way to reset the state.
        //glActiveTexture(GL_TEXTURE0); 
        glBindTexture(GL_TEXTURE_2D, 0);
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
