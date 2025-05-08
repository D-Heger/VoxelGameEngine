package de.heger.voxelengine.launcher;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.InputManager;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.Renderer;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.Direction;

import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.Set; // Import Set

public class GameLoop {

    private static final LoggerFacade LOGGER = LoggerFacade.get(GameLoop.class);
    private static final float TARGET_UPS = 60.0f; // Target updates per second (Increased for smoother input)
    private static final float TARGET_FPS = 60.0f; // Target frames per second (for timing, not limiting)

    private final Window window;
    private final InputManager inputManager;
    private final Renderer renderer; // Add Renderer instance
    private final Camera camera; // Add Camera instance
    private final ChunkManager chunkManager; // Added for P3-T3.7
    private final BlockRegistry blockRegistry; // Added for P3-T4
    private boolean running = false;

    public GameLoop(String windowTitle, int width, int height, boolean vsync, boolean fullscreen) {
        LOGGER.info("Initializing game loop...");
        // Window creation also initializes GLFW
        window = new Window(width, height, windowTitle, vsync, fullscreen);
        // Get input manager from window AFTER window creation
        inputManager = window.getInputManager();

        // Initialize Renderer after window context is current
        renderer = new Renderer(window);
        // NOTE: Renderer.init() calls BlockRegistry.getAllProperties(), which relies on the registry being finalized.
        // This might be the source of the issue if called before finalization. Let's move renderer.init() after registry finalization.
        // renderer.init(); // Moved down

        // Get camera from renderer
        camera = renderer.getCamera();

        // Get ChunkManager singleton instance - P3-T3.7
        chunkManager = ChunkManager.getInstance();

        // Block Registry initialization
        blockRegistry = BlockRegistry.getInstance();
        blockRegistry.finalizeRegistry();
        LOGGER.info("Block registry finalized with {} block types.", blockRegistry.getRegisteredBlockCount());

        // Initialize Renderer AFTER block registry is finalized, as renderer needs the finalized properties.
        renderer.init();
        LOGGER.info("Renderer initialized.");


        // Capture mouse cursor
        GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        LOGGER.info("Mouse cursor captured.");

        // P3-T2.3: Initialize test chunks
        initTestWorld(); // Call the original test world
        //initStressTestWorld(); // Call the stress test world

        LOGGER.info("Game loop initialized.");
    }

    // P3-T3.7: Refactored method to use ChunkManager for chunk management
    private void initTestWorld() {
        LOGGER.info("Initializing test world with ChunkManager...");
        
        // --- Chunk (0,0,0): Flat layer ---
        ChunkPos pos000 = new ChunkPos(0, 0, 0);
        Chunk chunk000 = new Chunk(pos000);
        LOGGER.debug("Populating chunk (0,0,0) with a flat layer...");
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk000.setBlock(x, 0, z, blockRegistry.getId("core:block/dirt"));
            }
        }
        chunkManager.addChunk(chunk000);
        
        // --- Chunk (1,0,0): Pyramid ---
        ChunkPos pos100 = new ChunkPos(1, 0, 0);
        Chunk chunk100 = new Chunk(pos100);
        LOGGER.debug("Populating chunk (1,0,0) with a pyramid...");
        int centerX = Chunk.SIZE_X / 2;
        int centerZ = Chunk.SIZE_Z / 2;
        int maxHalfWidth = Math.min(Chunk.SIZE_X / 2, Chunk.SIZE_Z / 2) - 1;
        int pyramidHeight = Math.min(Chunk.SIZE_Y, maxHalfWidth + 1);
        LOGGER.debug("Building pyramid in chunk (1,0,0) with height {} and max base halfWidth {}", pyramidHeight, maxHalfWidth);
        for (int y = 0; y < pyramidHeight; y++) {
            int currentHalfWidth = maxHalfWidth - y;
            if (currentHalfWidth < 0) continue;
            int minX = centerX - currentHalfWidth;
            int maxX = centerX + currentHalfWidth;
            int minZ = centerZ - currentHalfWidth;
            int maxZ = centerZ + currentHalfWidth;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x >= 0 && x < Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z) {
                        chunk100.setBlock(x, y, z, blockRegistry.getId("core:block/stone"));
                    }
                }
            }
        }
        chunkManager.addChunk(chunk100);

        // --- Chunk (0,0,1): Checkerboard Floor ---
        ChunkPos pos001 = new ChunkPos(0, 0, 1);
        Chunk chunk001 = new Chunk(pos001);
        LOGGER.debug("Populating chunk (0,0,1) with a checkerboard floor...");
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                if ((x + z) % 2 == 0) { // Place block if sum of coords is even
                    chunk001.setBlock(x, 0, z, blockRegistry.getId("core:block/dirt"));
                }
            }
        }
        chunkManager.addChunk(chunk001);

        // --- Chunk (-1,0,0): Hollow Box ---
        ChunkPos posN100 = new ChunkPos(-1, 0, 0);
        Chunk chunkN100 = new Chunk(posN100);
        LOGGER.debug("Populating chunk (-1,0,0) with a hollow box...");
        int boxHeight = Chunk.SIZE_Y / 2; // Make a half-height box
        for (int y = 0; y < boxHeight; y++) {
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    // Place block if it's on the floor (y=0) or on the walls (x=0, x=max, z=0, z=max)
                    // or on the ceiling (y=boxHeight-1)
                    boolean isWall = (x == 0 || x == Chunk.SIZE_X - 1 || z == 0 || z == Chunk.SIZE_Z - 1);
                    boolean isFloor = (y == 0);
                    boolean isCeiling = (y == boxHeight - 1);

                    if (isFloor || isCeiling || isWall) {
                        chunkN100.setBlock(x, y, z, blockRegistry.getId("core:block/dirt"));
                    }
                }
            }
        }
        chunkManager.addChunk(chunkN100);

        // Set up neighbor references - must use getChunk to ensure we're working with the references stored in the ChunkManager
        LOGGER.debug("Setting up chunk neighbor references...");
        // chunk000 neighbors
        chunk000 = chunkManager.getChunk(pos000); // Get reference from ChunkManager
        chunk100 = chunkManager.getChunk(pos100);
        chunk001 = chunkManager.getChunk(pos001);
        chunkN100 = chunkManager.getChunk(posN100);
        
        chunk000.setNeighbor(Direction.EAST, chunk100);
        chunk000.setNeighbor(Direction.SOUTH, chunk001);
        chunk000.setNeighbor(Direction.WEST, chunkN100);
        
        // chunk100 neighbors
        chunk100.setNeighbor(Direction.WEST, chunk000);
        
        // chunk001 neighbors
        chunk001.setNeighbor(Direction.NORTH, chunk000);
        
        // chunkN100 neighbors
        chunkN100.setNeighbor(Direction.EAST, chunk000);
        
        // Validate neighbor references
        LOGGER.debug("Validating neighbor references...");
        if (chunk000.getNeighbor(Direction.EAST) == chunk100 && 
            chunk100.getNeighbor(Direction.WEST) == chunk000) {
            LOGGER.debug("Neighbor validation passed between (0,0,0) and (1,0,0)");
        }

        // --- Create a tower of 16 chunks far away ---
        LOGGER.debug("Creating tower of chunks at position (3,0,3)...");
        Chunk previousChunk = null;
        
        // Create 16 chunks stacked vertically
        for (int y = 0; y < 16; y++) {
            ChunkPos towerPos = new ChunkPos(3, y, 3);
            Chunk chunk = new Chunk(towerPos);
            
            // Fill the chunk completely with blocks
            for (int localY = 0; localY < Chunk.SIZE_Y; localY++) {
                for (int localX = 0; localX < Chunk.SIZE_X; localX++) {
                    for (int localZ = 0; localZ < Chunk.SIZE_Z; localZ++) {
                        chunk.setBlock(localX, localY, localZ, blockRegistry.getId("core:block/grass"));
                    }
                }
            }
            
            // Add to chunk manager
            chunkManager.addChunk(chunk);
            
            // Set up vertical neighbors
            if (previousChunk != null) {
                // Connect this chunk with the one below it
                chunk.setNeighbor(Direction.DOWN, previousChunk);
                previousChunk.setNeighbor(Direction.UP, chunk);
            }
            
            previousChunk = chunk;
        }
        
        LOGGER.debug("Tower of chunks created and filled");
        
        LOGGER.info("Test world initialized with {} chunks.", chunkManager.getLoadedChunkCount());
    }

    // P3-T3.7: Refactored method to use ChunkManager for stress test world
    private void initStressTestWorld() {
        LOGGER.info("Initializing stress test world with ChunkManager (16x16x16 chunks)...");
        final int gridDim = 4; // Dimension of the chunk grid (4x4x4)
        int totalChunks = gridDim * gridDim * gridDim;
        LOGGER.debug("Creating {} total chunks...", totalChunks);

        long startTime = System.nanoTime();
        int chunksCreated = 0;

        for (int cx = 0; cx < gridDim; cx++) {
            for (int cy = 0; cy < gridDim; cy++) {
                for (int cz = 0; cz < gridDim; cz++) {
                    ChunkPos pos = new ChunkPos(cx, cy, cz);
                    Chunk chunk = new Chunk(pos);

                    // Fill the chunk completely with blocks
                    for (int localX = 0; localX < Chunk.SIZE_X; localX++) {
                        for (int localY = 0; localY < Chunk.SIZE_Y; localY++) {
                            for (int localZ = 0; localZ < Chunk.SIZE_Z; localZ++) {
                                chunk.setBlock(localX, localY, localZ, blockRegistry.getId("core:block/dirt"));
                            }
                        }
                    }
                    
                    // Add to the ChunkManager
                    chunkManager.addChunk(chunk);
                    chunksCreated++;

                    // Basic progress logging to avoid flooding
                    if (chunksCreated % 10 == 0) {
                        LOGGER.debug("Created {}/{} chunks...", chunksCreated, totalChunks);
                    }
                }
            }
        }

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;

        LOGGER.info("Stress test world initialized with {} chunks in {:.2f} seconds.", 
                   chunkManager.getLoadedChunkCount(), durationSeconds);
        
        // Note: Neighbor linking is skipped for this stress test to focus on creation/rendering load.
    }

    public void run() {
        LOGGER.info("Starting game loop...");
        running = true;

        double lastLoopTime = GLFW.glfwGetTime();
        double accumulator = 0.0;
        double timeU = 1.0 / TARGET_UPS; // Time per update

        //double lastRenderTime = GLFW.glfwGetTime(); // Not strictly needed for this loop structure
        //double timeF = 1.0 / TARGET_FPS; // Approximate time per frame for stats

        int frames = 0;
        int updates = 0;
        double timer = GLFW.glfwGetTime();

        // Main game loop
        try {
            while (running && !window.shouldClose()) {
                double now = GLFW.glfwGetTime();
                double delta = now - lastLoopTime;
                lastLoopTime = now;
                accumulator += delta;

                // --- Input ---
                // Input manager update polls events AND resets mouse delta for the frame
                inputManager.update();
                input(); // Process polled input state

                // --- Update ---
                // Fixed timestep update loop
                while (accumulator >= timeU) {
                    update((float) timeU); // Pass fixed delta time
                    accumulator -= timeU;
                    updates++;
                }

                // --- Render ---
                // Interpolation factor could be calculated here for smoother rendering:
                // float alpha = (float) (accumulator / timeU);
                // But we'll keep it simple for now and render based on the last update state.
                render(); // Render based on current state
                frames++;

                // --- Sync & Timing ---
                // window.update() is not needed here as inputManager.update() calls glfwPollEvents()
                window.swapBuffers(); // Swaps buffers (might block if vsync is on)

                // --- FPS/UPS Counter ---
                if (GLFW.glfwGetTime() - timer > 1.0) {
                    timer++; // Add one second
                    LOGGER.debug("FPS: {}, UPS: {}", frames, updates);
                    frames = 0;
                    updates = 0;
                }
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred in the game loop:", e);
            running = false; // Stop the loop on error
        } finally {
            LOGGER.info("Game loop stopped or encountered error, starting cleanup...");
            cleanup();
        }
    }

    private void input() {
        // Process input polled by inputManager.update()

        // Check for escape key to close
        if (inputManager.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            running = false;
            LOGGER.info("Escape key pressed, stopping loop.");
            return; // Exit early if closing
        }

        // Process camera mouse look
        double deltaX = inputManager.getDeltaMouseX();
        double deltaY = inputManager.getDeltaMouseY();
        if (deltaX != 0 || deltaY != 0) {
             camera.processMouseMovement(deltaX, deltaY);
        }

        // Camera keyboard movement is handled in the update loop with delta time
    }

    private void update(float deltaTime) {
        // Process camera keyboard movement using fixed delta time
        camera.processKeyboard(inputManager, deltaTime);

        // Placeholder for other game logic updates (physics, AI, etc.)
        // LOGGER.trace("Update tick (delta: {})", deltaTime);

        // Check if window was resized and update projection matrix
        // TODO: Implement a more robust resize event system later
        // For now, we can check if the aspect ratio changed significantly,
        // but it's better handled via callbacks or flags.
        // if (Math.abs(window.getAspectRatio() - lastAspectRatio) > 0.01f) {
        //     renderer.updateProjectionMatrix();
        //     lastAspectRatio = window.getAspectRatio();
        // }
    }

    private void render() {
        // Clear the screen using the renderer
        renderer.clear();

        // Tell the renderer to render the scene (which uses the camera)
        // renderer.render(); // Comment out old single cube rendering for now
        
        // Use ChunkManager to provide chunks directly to the renderer
        renderer.renderChunks(chunkManager.getAllLoadedChunks());
    }

    private void cleanup() {
        LOGGER.info("Cleaning up game loop resources...");
        // Release mouse cursor
        GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        LOGGER.info("Mouse cursor released.");

        try {
            // Clean up ChunkManager by removing all chunks
            LOGGER.info("Cleaning up ChunkManager...");
            Collection<Chunk> chunks = List.copyOf(chunkManager.getAllLoadedChunks());
            for (Chunk chunk : chunks) {
                chunkManager.removeChunk(chunk.getPosition());
            }
            LOGGER.info("ChunkManager cleanup complete. Removed {} chunks.", chunks.size());
            
            if (renderer != null) {
                LOGGER.info("Cleaning up renderer...");
                renderer.cleanup();
            } else {
                LOGGER.warn("Renderer was null during cleanup.");
            }
        } catch (Exception e) {
            LOGGER.error("Error during renderer cleanup:", e);
        }
        // InputManager cleanup is now called by Window cleanup if it owns it,
        // or should be called explicitly if managed separately.
        // Since Window creates it, Window should clean it up. Let's verify Window.cleanup()
        // calls inputManager.cleanup(). Yes, Window.java passes handle to InputManager constructor,
        // but GameLoop also creates one? Let's fix GameLoop to use Window's InputManager.
        // --> Fixed in constructor: inputManager = window.getInputManager();
        // --> Removed explicit inputManager.cleanup() call here.

        try {
            if (window != null) {
                LOGGER.info("Cleaning up window...");
                window.cleanup(); // Window cleanup should handle its InputManager
            } else {
                LOGGER.warn("Window was null during cleanup.");
            }
        } catch (Exception e) {
            LOGGER.error("Error during window cleanup:", e);
        }
        LOGGER.info("Game loop cleanup finished.");
    }
}
