package de.heger.voxelengine.launcher;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.InputManager;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.Renderer;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.debug.PerformanceDisplay;
import de.heger.voxelengine.renderer.ui.font.Font;
import de.heger.voxelengine.renderer.ui.font.FontManager;
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
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;

public class GameLoop {

    private static final LoggerFacade LOGGER = LoggerFacade.get(GameLoop.class);
    private static final float TARGET_UPS = 60.0f;
    private static final float TARGET_FPS = 60.0f;

    private static final int MAX_WORLD_HEIGHT_CHUNKS = 16;
    private static final int CHUNK_LOAD_RADIUS = 16;
    private static final int CHUNK_UNLOAD_OFFSET = 2; // Unload if further than LOAD_RADIUS + OFFSET
    private static final double CHUNK_LOAD_CHECK_INTERVAL = 0.25;

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
    private int currentFps = 0;
    private int currentUps = 0;

    private boolean wasF3Pressed = false;
    private boolean wasF2Pressed = false;

    private UIManager uiManager;
    private PerformanceDisplay performanceDisplay;
    private PerformanceDisplay.PerformanceData performanceData;

    public GameLoop(String windowTitle, int width, int height, boolean vsync, boolean fullscreen, float viewDistance) {
        LOGGER.info("Initializing game loop...");
        window = new Window(width, height, windowTitle, vsync, fullscreen, "/window.png");
        inputManager = window.getInputManager();

        this.blockRegistry = BlockRegistry.getInstance();
        if (!this.blockRegistry.isInitialized()) {
            this.blockRegistry.finalizeRegistry();
        }
        LOGGER.info("Block registry finalized with {} block types.", blockRegistry.getRegisteredBlockCount());

        chunkManager = ChunkManager.getInstance();

        renderer = new Renderer(window);
        renderer.init();
        camera = renderer.getCamera();
        camera.setViewDistance(viewDistance);
        LOGGER.info("Renderer initialized with view distance: {}", viewDistance);

        uiManager = new UIManager();
        uiManager.init(window);

        if (!uiManager.isInitialized()) {
            LOGGER.error("UIManager failed to initialize. UI features will be disabled.");
        } else {
            FontManager actualFontManager = uiManager.getFontManager();
            Font defaultFont = actualFontManager.getDefaultFont();

            if (defaultFont == null) {
                LOGGER.error("Default font not available from UIManager's FontManager. Performance display cannot be created.");
            } else {
                performanceDisplay = new PerformanceDisplay(uiManager, defaultFont);
                performanceDisplay.init();
                performanceDisplay.setVisible(true);
            }
        }
        performanceData = new PerformanceDisplay.PerformanceData();

        TerrainGenerator noiseTerrainGen = new NoiseTerrainGenerator(1337);
        TaskResultHandler loggingHandler = new LoggingTaskResultHandler();
        this.performanceTrackingHandler = new PerformanceTrackingTaskResultHandler(loggingHandler);

        int horizontalDim = (CHUNK_LOAD_RADIUS * 2) + 1;
        int calculatedMaxTasks = horizontalDim * horizontalDim * MAX_WORLD_HEIGHT_CHUNKS;
        int queueCapacity = Math.max(256, (int) (calculatedMaxTasks * 1.5));
        LOGGER.info("Calculated ChunkGenerationService queue capacity: {} for CHUNK_LOAD_RADIUS {}", queueCapacity, CHUNK_LOAD_RADIUS);

        int corePoolSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        int maxPoolSize = Math.max(corePoolSize, Runtime.getRuntime().availableProcessors() - 1);
        if (maxPoolSize <= corePoolSize) maxPoolSize = corePoolSize + 1;
        int keepAliveSeconds = 60;

        this.chunkGenerationService = new ChunkGenerationService(
                noiseTerrainGen,
                this.performanceTrackingHandler,
                corePoolSize, maxPoolSize, keepAliveSeconds,
                queueCapacity
        );
        LOGGER.info("ChunkGenerationService initialized.");
        LOGGER.info("Generating {} initial chunks", (CHUNK_LOAD_RADIUS * 2 + 1)
                * (CHUNK_LOAD_RADIUS * 2 + 1) * MAX_WORLD_HEIGHT_CHUNKS);

        GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        LOGGER.info("Mouse cursor captured.");

        this.lastCameraXZChunkPos = new ChunkPos(Integer.MIN_VALUE, 0, Integer.MIN_VALUE);

        LOGGER.info("Game loop initialized.");
    }

    public void run() {
        LOGGER.info("Starting game loop...");
        running = true;

        double lastLoopTime = GLFW.glfwGetTime();
        double accumulator = 0.0;
        double timeU = 1.0 / TARGET_UPS;

        int frames = 0;
        int updates = 0;
        double timer = GLFW.glfwGetTime();

        try {
            while (running && !window.shouldClose()) {
                double now = GLFW.glfwGetTime();
                double delta = now - lastLoopTime;
                lastLoopTime = now;
                accumulator += delta;

                inputManager.update();
                input();

                while (accumulator >= timeU) {
                    update((float) timeU);
                    accumulator -= timeU;
                    updates++;
                }
                
                if (uiManager != null && uiManager.isInitialized()) {
                    uiManager.update((float) delta);
                    if (performanceDisplay != null && performanceDisplay.isVisible()){
                         performanceDisplay.update(performanceData);
                    }
                }

                render();
                frames++;
                window.swapBuffers();

                if (GLFW.glfwGetTime() - timer > 1.0) {
                    timer++;
                    this.currentFps = frames;
                    this.currentUps = updates;
                    LOGGER.debug("FPS: {}, UPS: {}", this.currentFps, this.currentUps);
                    frames = 0;
                    updates = 0;

                    performanceData.fps = this.currentFps;
                    performanceData.ups = this.currentUps;
                    if (performanceTrackingHandler != null) {
                        performanceData.avgChunkGenTime = performanceTrackingHandler.getAverageGenerationTimeMillis();
                        performanceData.chunkGenSamples = performanceTrackingHandler.getSampleCount();
                    }
                    performanceData.drawCalls = renderer.getDrawCallsLastFrame();
                    performanceData.renderedIndices = renderer.getTotalIndicesRenderedLastFrame();
                    performanceData.occlusionCulledChunks = renderer.getOcclusionCulledChunksLastFrame();
                    performanceData.frustumCulledChunks = renderer.getFrustumCulledChunksLastFrame();
                    performanceData.totalLoadedChunks = chunkManager.getLoadedChunkCount();
                    performanceData.activeMeshes = renderer.getActiveMeshCount();
                    if (chunkGenerationService != null) {
                        performanceData.generationQueueSize = chunkGenerationService.getPendingTaskCount();
                        performanceData.activeGenerationThreads = chunkGenerationService.getActiveWorkerCount();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception in game loop:", e);
        } finally {
            cleanup();
        }
    }

    private void input() {
        boolean isF3Pressed = inputManager.isKeyPressed(GLFW_KEY_F3);
        if (isF3Pressed && !wasF3Pressed) {
            renderer.toggleWireframeMode();
        }
        wasF3Pressed = isF3Pressed;

        boolean isF2Pressed = inputManager.isKeyPressed(GLFW_KEY_F2);
        if (isF2Pressed && !wasF2Pressed) {
            if (performanceDisplay != null) {
                performanceDisplay.toggleVisibility();
                LOGGER.debug("Performance display visibility toggled to: {}", performanceDisplay.isVisible());
            }
        }
        wasF2Pressed = isF2Pressed;

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
    }

    private void update(float deltaTime) {
        chunkLoadCheckTimer += deltaTime;
        if (chunkLoadCheckTimer >= CHUNK_LOAD_CHECK_INTERVAL) {
            updateChunkLoading();
            chunkLoadCheckTimer = 0.0;
        }
        camera.processKeyboard(inputManager, deltaTime);
    }

    private void render() {
        renderer.clear();
        renderer.renderChunks(chunkManager.getAllLoadedChunks());
        
        if (uiManager != null && uiManager.isInitialized()) {
            uiManager.render();
        }
    }

    private void cleanup() {
        LOGGER.info("Cleaning up game loop...");
        running = false;

        if (performanceDisplay != null) {
            performanceDisplay.cleanup();
        }
        if (uiManager != null && uiManager.isInitialized()) {
            uiManager.cleanup();
        }

        if (chunkGenerationService != null) {
            chunkGenerationService.shutdown();
            LOGGER.info("ChunkGenerationService shut down.");
        }

        if (renderer != null) {
            renderer.cleanup();
            LOGGER.info("Renderer cleaned up.");
        }

        if (chunkManager != null) {
            List<ChunkPos> positionsToClear = chunkManager.getAllLoadedChunks().stream()
                                                .map(Chunk::getPosition)
                                                .collect(Collectors.toList());
            for (ChunkPos pos : positionsToClear) {
                chunkManager.removeChunk(pos);
            }
            LOGGER.info("All chunks removed from ChunkManager. Loaded count: {}", chunkManager.getLoadedChunkCount());
        }

        if (window != null) {
            window.cleanup();
            LOGGER.info("Window destroyed.");
        }

        LOGGER.info("Game loop cleanup complete.");
    }

    private void updateChunkLoading() {
        if (camera == null || chunkManager == null || chunkGenerationService == null) {
            LOGGER.warn("Cannot update chunk loading, essential components are null.");
            return;
        }

        float cameraX = camera.getPosition().x;
        float cameraZ = camera.getPosition().z;

        int camChunkX = (int) Math.floor(cameraX / Chunk.SIZE_X);
        int camChunkZ = (int) Math.floor(cameraZ / Chunk.SIZE_Z);

        ChunkPos currentCamXZPos = new ChunkPos(camChunkX, 0, camChunkZ);

        if (currentCamXZPos.equals(this.lastCameraXZChunkPos)) {
            return;
        }
        LOGGER.debug("Camera moved to new chunk column: {} (was {}), triggering load/unload check.", currentCamXZPos,
                this.lastCameraXZChunkPos);
        this.lastCameraXZChunkPos = currentCamXZPos;

        int chunksRequestedThisCycle = 0;
        int chunksAlreadyManagedThisCycle = 0;

        for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
            for (int dz = -CHUNK_LOAD_RADIUS; dz <= CHUNK_LOAD_RADIUS; dz++) {
                for (int dy = 0; dy < MAX_WORLD_HEIGHT_CHUNKS; dy++) {
                    int targetChunkX = camChunkX + dx;
                    int targetChunkY = dy;
                    int targetChunkZ = camChunkZ + dz;
                    ChunkPos targetPos = new ChunkPos(targetChunkX, targetChunkY, targetChunkZ);

                    if (!chunkManager.containsChunk(targetPos)) {
                        int manhattanDistXZ = Math.abs(dx) + Math.abs(dz);
                        int priority = manhattanDistXZ * MAX_WORLD_HEIGHT_CHUNKS + targetChunkY;

                        if (this.chunkGenerationService.requestChunkGeneration(targetPos, priority)) {
                            chunksRequestedThisCycle++;
                        } else {
                        }
                    } else {
                        chunksAlreadyManagedThisCycle++;
                    }
                }
            }
        }
        if (chunksRequestedThisCycle > 0) {
            LOGGER.debug("Requested {} new chunks for generation. {} were already loaded/queued in radius.", chunksRequestedThisCycle, chunksAlreadyManagedThisCycle);
        }

        List<Chunk> currentlyLoadedChunks = List.copyOf(chunkManager.getAllLoadedChunks());
        List<ChunkPos> chunksToUnload = new ArrayList<>();
        int unloadRadiusActual = CHUNK_LOAD_RADIUS + CHUNK_UNLOAD_OFFSET;

        for (Chunk loadedChunk : currentlyLoadedChunks) {
            ChunkPos loadedPos = loadedChunk.getPosition();
            int distX = Math.abs(loadedPos.x - camChunkX);
            int distZ = Math.abs(loadedPos.z - camChunkZ);

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
                if (chunkManager.containsChunk(posToUnload)) {
                    chunkManager.removeChunk(posToUnload);
                    actualUnloads++;
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
