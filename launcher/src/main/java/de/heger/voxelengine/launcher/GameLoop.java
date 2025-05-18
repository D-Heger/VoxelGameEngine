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
import de.heger.voxelengine.world.generation.NoiseTerrainGenerator;
import de.heger.voxelengine.world.generation.TerrainGenerator;
import de.heger.voxelengine.world.generation.thread.ChunkGenerationService;
import de.heger.voxelengine.world.generation.thread.LoggingTaskResultHandler;
import de.heger.voxelengine.world.generation.thread.PerformanceTrackingTaskResultHandler;
import de.heger.voxelengine.world.generation.thread.TaskResultHandler;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;

public class GameLoop {

    private static final LoggerFacade LOGGER = LoggerFacade.get(GameLoop.class);
    private static final float TARGET_UPS = 60.0f;
    private static final float TARGET_FPS = 60.0f;

    private static final int MAX_WORLD_HEIGHT_CHUNKS = 16;
    private static final int CHUNK_LOAD_RADIUS = 16;
    private static final int CHUNK_UNLOAD_OFFSET = 1; // Unload if further than LOAD_RADIUS + OFFSET
    private static final double CHUNK_LOAD_CHECK_INTERVAL = 0.5;

    private final Window window;
    private final InputManager inputManager;
    private final Renderer renderer;
    private final Camera camera;
    private final ChunkManager chunkManager;
    private final BlockRegistry blockRegistry;
    private ChunkGenerationService chunkGenerationService;
    private boolean running = false;

    private ChunkPos lastCameraXZChunkPos = null;
    private double chunkLoadCheckTimer = 0.0;

    private PerformanceTrackingTaskResultHandler performanceTrackingHandler;
    private String originalWindowTitle;
    private int currentFps = 0;
    private int currentUps = 0;
    private long lastFrameRenderedIndices = 0;
    private int lastFrameDrawCalls = 0;
    private int lastFrameOcclusionCulledChunks = 0;

    private boolean wasF3Pressed = false;

    public GameLoop(String windowTitle, int width, int height, boolean vsync, boolean fullscreen, float viewDistance) {
        LOGGER.info("Initializing game loop...");
        this.originalWindowTitle = windowTitle; // Store original window title
        // Window creation also initializes GLFW
        // Pass the icon resource path. Assuming "window.ico" is in src/main/resources
        window = new Window(width, height, windowTitle, vsync, fullscreen, "/window.png");
        // Get input manager from window AFTER window creation
        inputManager = window.getInputManager();

        // Initialize BlockRegistry (P3-T4)
        this.blockRegistry = BlockRegistry.getInstance();
        // No need to explicitly call a load method if it auto-loads or is configured
        // elsewhere
        // For now, assuming BlockRegistry.getInstance() handles its setup.
        // Ensure it's finalized before use by generators
        if (!this.blockRegistry.isInitialized()) {
            this.blockRegistry.finalizeRegistry();
        }
        LOGGER.info("Block registry finalized with {} block types.", blockRegistry.getRegisteredBlockCount());

        // Initialize ChunkManager (P3-T3.7)
        chunkManager = ChunkManager.getInstance();

        // Initialize Renderer AFTER block registry is finalized, as renderer needs the
        // finalized properties.
        renderer = new Renderer(window);
        renderer.init();
        camera = renderer.getCamera();
        camera.setViewDistance(viewDistance);
        LOGGER.info("Renderer initialized with view distance: {}", viewDistance);


        TerrainGenerator noiseTerrainGen = new NoiseTerrainGenerator(1337);
        TaskResultHandler loggingHandler = new LoggingTaskResultHandler();
        this.performanceTrackingHandler = new PerformanceTrackingTaskResultHandler(loggingHandler);

        // Determine dynamic queue capacity based on CHUNK_LOAD_RADIUS
        int horizontalDim = (CHUNK_LOAD_RADIUS * 2) + 1;
        int calculatedMaxTasks = horizontalDim * horizontalDim * MAX_WORLD_HEIGHT_CHUNKS;
        // Ensure capacity can hold all tasks within load radius, plus a buffer.
        int queueCapacity = Math.max(256, (int) (calculatedMaxTasks * 1.5));
        LOGGER.info("Calculated ChunkGenerationService queue capacity: {} for CHUNK_LOAD_RADIUS {}", queueCapacity,
                CHUNK_LOAD_RADIUS);

        // TODO: Tune these pool sizes based on testing and typical core counts
        int corePoolSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2); // Example: Half of available
                                                                                        // cores
        int maxPoolSize = Math.max(corePoolSize, Runtime.getRuntime().availableProcessors() - 1); // Example: Most
                                                                                                  // cores, but leave
                                                                                                  // one for OS/main
                                                                                                  // thread
        if (maxPoolSize <= corePoolSize)
            maxPoolSize = corePoolSize + 1; // ensure max > core slightly if low cores
        int keepAliveSeconds = 60;

        this.chunkGenerationService = new ChunkGenerationService(
                noiseTerrainGen,
                // resultHandler, // Use the new wrapped handler
                this.performanceTrackingHandler, // Use the new wrapped handler
                corePoolSize, maxPoolSize, keepAliveSeconds,
                queueCapacity // Use the dynamically calculated capacity
        );
        LOGGER.info("ChunkGenerationService initialized.");
        LOGGER.info("Generating {} initial chunks", (CHUNK_LOAD_RADIUS * 2 + 1)
                * (CHUNK_LOAD_RADIUS * 2 + 1) * MAX_WORLD_HEIGHT_CHUNKS);

        // Capture mouse cursor
        GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        LOGGER.info("Mouse cursor captured.");

        // Initialize lastCameraXZChunkPos to ensure the first load check runs
        this.lastCameraXZChunkPos = new ChunkPos(Integer.MIN_VALUE, 0, Integer.MIN_VALUE);

        LOGGER.info("Game loop initialized.");
    }

    /**
     * Initializes a procedural world by ASYNCHRONOUSLY requesting chunks
     * in a square area around the origin (0,Y,0)
     * and vertically up to {@link #MAX_WORLD_HEIGHT_CHUNKS}.
     * 
     * @param worldRadiusChunks The radius of the world to generate in chunks
     *                          horizontally.
     */
    // This method is no longer called for initial world generation due to P3-T7
    // dynamic loading.
    // It can be kept for testing or other purposes if needed.
    @SuppressWarnings("unused") // Mark as unused if it's no longer called
    private void initAsyncProceduralWorld(int worldRadiusChunks) {
        LOGGER.info(
                "Requesting ASYNCHRONOUS procedural world generation with radius: {} chunks (Total {}x{} area horizontally, {} chunks high)...",
                worldRadiusChunks, (worldRadiusChunks * 2) + 1, (worldRadiusChunks * 2) + 1, MAX_WORLD_HEIGHT_CHUNKS);
        int chunksRequested = 0;
        int requestFailed = 0;

        // Priority: For initial load, a simple constant priority is fine.
        // Chunks closer to the player/center could get higher priority (lower number).
        // Example: Priority can be based on distance from (0,y,0) for initial load.
        // int basePriority = 1000;

        for (int cx = -worldRadiusChunks; cx <= worldRadiusChunks; cx++) {
            for (int cz = -worldRadiusChunks; cz <= worldRadiusChunks; cz++) {
                for (int cy = 0; cy < MAX_WORLD_HEIGHT_CHUNKS; cy++) {
                    ChunkPos pos = new ChunkPos(cx, cy, cz);
                    // Simple priority: lower Y levels, or closer to 0,0 horizontal get slightly
                    // higher priority.
                    int priority = 1000 + (MAX_WORLD_HEIGHT_CHUNKS - 1 - cy) + (Math.abs(cx) + Math.abs(cz));
                    boolean requested = this.chunkGenerationService.requestChunkGeneration(pos, priority);
                    if (requested) {
                        chunksRequested++;
                    } else {
                        requestFailed++;
                    }
                }
            }
        }
        if (requestFailed > 0) {
            LOGGER.warn(
                    "Asynchronous procedural world initialization: {} chunks requested, {} requests failed (e.g. already loaded/queued, or queue full).",
                    chunksRequested, requestFailed);
        } else {
            LOGGER.info("Asynchronous procedural world initialization complete. Requested {} chunks.", chunksRequested);
        }
    }

    public void run() {
        LOGGER.info("Starting game loop...");
        running = true;

        double lastLoopTime = GLFW.glfwGetTime();
        double accumulator = 0.0;
        double timeU = 1.0 / TARGET_UPS; // Time per update

        // double lastRenderTime = GLFW.glfwGetTime(); // Not strictly needed for this
        // loop structure
        // double timeF = 1.0 / TARGET_FPS; // Approximate time per frame for stats

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
                // window.update() is not needed here as inputManager.update() calls
                // glfwPollEvents()
                window.swapBuffers(); // Swaps buffers (might block if vsync is on)

                // --- FPS/UPS Counter ---
                if (GLFW.glfwGetTime() - timer > 1.0) {
                    timer++; // Add one second
                    // LOGGER.debug("FPS: {}, UPS: {}", frames, updates);
                    this.currentFps = frames;
                    this.currentUps = updates;
                    LOGGER.debug("FPS: {}, UPS: {}", this.currentFps, this.currentUps);
                    frames = 0;
                    updates = 0;                    // Update performance metrics for display
                    lastFrameRenderedIndices = renderer.getTotalIndicesRenderedLastFrame();
                    lastFrameDrawCalls = renderer.getDrawCallsLastFrame();
                    lastFrameOcclusionCulledChunks = renderer.getOcclusionCulledChunksLastFrame();

                    String perfMetricsString = String.format("FPS: %d, UPS: %d, Avg Gen: %.2fms (%d samples), DrawCalls: %d, Idx: %dk, Occlusion: %d chunks",
                        this.currentFps,
                        this.currentUps,
                        performanceTrackingHandler.getAverageGenerationTimeMillis(),
                        performanceTrackingHandler.getSampleCount(),
                        lastFrameDrawCalls,
                        lastFrameRenderedIndices / 1000,
                        lastFrameOcclusionCulledChunks
                    );
                    window.setTitle(this.originalWindowTitle + " | " + perfMetricsString);
                }

                // Update chunk loading/unloading (P3-T7)
                chunkLoadCheckTimer += delta;
                if (chunkLoadCheckTimer >= CHUNK_LOAD_CHECK_INTERVAL) {
                    updateChunkLoading();
                    chunkLoadCheckTimer = 0.0; // Reset timer
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
        // Process input - assuming InputManager updates state internally or via another method
        // Handle F3 key for toggling wireframe mode
        boolean isF3Pressed = inputManager.isKeyPressed(GLFW_KEY_F3);
        if (isF3Pressed && !wasF3Pressed) {
            renderer.toggleWireframeMode();
        }
        wasF3Pressed = isF3Pressed;

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

        if (this.chunkGenerationService != null) {
            LOGGER.info("Attempting to shut down ChunkGenerationService...");
            this.chunkGenerationService.shutdown();
            // Simple wait for a short period. In a real game, might need more robust
            // handling or UI feedback.
            // This is a basic wait, actual termination is handled by WorldThreadPool's
            // awaitTermination.
            // try {
            // Thread.sleep(1000); // Give it a second to process shutdown command and clear
            // queue
            // } catch (InterruptedException e) {
            // LOGGER.warn("Interrupted while waiting for ChunkGenerationService to initiate
            // shutdown.");
            // Thread.currentThread().interrupt();
            // }
            // The ChunkGenerationService.shutdown() calls WorldThreadPool.shutdown(), which
            // has its own awaitTermination logic.
            // So, further waiting here might be redundant or could conflict if not designed
            // carefully.
            // For now, just initiating shutdown is sufficient as per WorldThreadPool's
            // behavior.
            LOGGER.info("ChunkGenerationService shutdown initiated.");
        }

        // Release mouse cursor
        GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        LOGGER.info("Mouse cursor released.");

        try {
            // Clean up ChunkManager by removing all chunks
            LOGGER.info("Cleaning up ChunkManager...");
            // Make a copy to avoid ConcurrentModificationException if removeChunk triggers
            // listeners or internal changes
            List<Chunk> chunksSnapshot = List.copyOf(chunkManager.getAllLoadedChunks());
            for (Chunk chunk : chunksSnapshot) {
                chunkManager.removeChunk(chunk.getPosition());
            }
            LOGGER.info("ChunkManager cleanup complete. Removed {} chunks.", chunksSnapshot.size());

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
        // Since Window creates it, Window should clean it up. Let's verify
        // Window.cleanup()
        // calls inputManager.cleanup(). Yes, Window.java passes handle to InputManager
        // constructor,
        // but GameLoop also creates one? Let's fix GameLoop to use Window's
        // InputManager.
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

    // New method for P3-T7: Dynamic Chunk Loading and Unloading
    private void updateChunkLoading() {
        if (camera == null || chunkManager == null || chunkGenerationService == null) {
            LOGGER.warn("Cannot update chunk loading, essential components are null.");
            return;
        }

        float cameraX = camera.getPosition().x;
        // float cameraY = camera.getPosition().y; // Y position of camera might not be
        // directly used for column center
        float cameraZ = camera.getPosition().z;

        int camChunkX = (int) Math.floor(cameraX / Chunk.SIZE_X);
        // int camChunkY = (int) Math.floor(cameraY / Chunk.SIZE_Y); // Center Y for
        // loading decisions
        int camChunkZ = (int) Math.floor(cameraZ / Chunk.SIZE_Z);

        ChunkPos currentCamXZPos = new ChunkPos(camChunkX, 0, camChunkZ); // Use Y=0 for XZ comparison

        // Only update if camera has moved to a new XZ chunk column
        if (currentCamXZPos.equals(this.lastCameraXZChunkPos)) {
            return;
        }
        LOGGER.debug("Camera moved to new chunk column: {} (was {}), triggering load/unload check.", currentCamXZPos,
                this.lastCameraXZChunkPos);
        this.lastCameraXZChunkPos = currentCamXZPos;

        // --- Chunk Loading ---
        int chunksRequestedThisCycle = 0;
        int chunksAlreadyManagedThisCycle = 0; // For logging how many were skipped because already loaded/queued

        for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
            for (int dz = -CHUNK_LOAD_RADIUS; dz <= CHUNK_LOAD_RADIUS; dz++) {
                int targetChunkColX = camChunkX + dx;
                int targetChunkColZ = camChunkZ + dz;

                for (int targetChunkY = 0; targetChunkY < MAX_WORLD_HEIGHT_CHUNKS; targetChunkY++) {
                    ChunkPos targetPos = new ChunkPos(targetChunkColX, targetChunkY, targetChunkColZ);

                    if (!chunkManager.containsChunk(targetPos)) {
                        // Priority: lower Y, closer XZ = higher priority (lower number)
                        int manhattanDistXZ = Math.abs(dx) + Math.abs(dz);
                        // Y level factor: lower Y levels get higher priority.
                        // MAX_WORLD_HEIGHT_CHUNKS ensures XZ distance has more weight than Y.
                        int priority = manhattanDistXZ * MAX_WORLD_HEIGHT_CHUNKS + targetChunkY;

                        // requestChunkGeneration returns true if successfully queued
                        if (this.chunkGenerationService.requestChunkGeneration(targetPos, priority)) {
                            chunksRequestedThisCycle++;
                        } else {
                            // Could be already queued by the service, or queue is full.
                            // We don't log this every time to avoid spam, requestChunkGeneration handles
                            // its own logging.
                        }
                    } else {
                        chunksAlreadyManagedThisCycle++;
                    }
                }
            }
        }
        if (chunksRequestedThisCycle > 0) {
            LOGGER.debug("Requested {} new chunks for generation. Skipped {} already loaded/managed.",
                    chunksRequestedThisCycle, chunksAlreadyManagedThisCycle);
        }

        // --- Chunk Unloading ---
        // Make sure to not modify the collection while iterating. Get a snapshot.
        List<Chunk> currentlyLoadedChunks = List.copyOf(chunkManager.getAllLoadedChunks());
        List<ChunkPos> chunksToUnload = new ArrayList<>(); // Use java.util.ArrayList
        int unloadRadiusActual = CHUNK_LOAD_RADIUS + CHUNK_UNLOAD_OFFSET;

        for (Chunk loadedChunk : currentlyLoadedChunks) {
            ChunkPos loadedPos = loadedChunk.getPosition();

            int distX = Math.abs(loadedPos.x - camChunkX);
            int distZ = Math.abs(loadedPos.z - camChunkZ);

            // Unload if the chunk column is outside the unload radius
            // Y-coordinate of the chunk is not considered for unload distance; we unload
            // whole columns.
            if (distX > unloadRadiusActual || distZ > unloadRadiusActual) {
                chunksToUnload.add(loadedPos);
            }
        }

        if (!chunksToUnload.isEmpty()) {
            LOGGER.debug(
                    "Attempting to unload {} chunk positions that are out of range (Player at XZ: {},{}, UnloadRadius: {}).",
                    chunksToUnload.size(), camChunkX, camChunkZ, unloadRadiusActual);
            int actualUnloads = 0;
            for (ChunkPos posToUnload : chunksToUnload) {
                // First check if the chunk exists, then remove it.
                // The removeChunk method in ChunkManager is void.
                if (chunkManager.containsChunk(posToUnload)) {
                    chunkManager.removeChunk(posToUnload);
                    actualUnloads++;
                    // Attempt to cancel if it was being generated
                    // This method will be added to ChunkGenerationService
                    this.chunkGenerationService.cancelTask(posToUnload);
                }
            }
            if (actualUnloads > 0) {
                LOGGER.info("Successfully unloaded {} chunks. {} were requested for unload.", actualUnloads,
                        chunksToUnload.size());
            }
        }
    }
}
