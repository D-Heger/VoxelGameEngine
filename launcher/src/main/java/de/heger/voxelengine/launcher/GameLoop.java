package de.heger.voxelengine.launcher;

import de.heger.voxelengine.core.config.Config;
import de.heger.voxelengine.core.config.ConfigManager;
import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.InputManager;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.Renderer;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.font.Font;
import de.heger.voxelengine.renderer.ui.font.FontManager;
import de.heger.voxelengine.renderer.ui.menus.DebugMenu;
import de.heger.voxelengine.renderer.ui.menus.PauseMenu;
import de.heger.voxelengine.renderer.ui.menus.SettingsMenu;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.generation.NoiseTerrainGenerator;
import de.heger.voxelengine.world.generation.TerrainGenerator;
import de.heger.voxelengine.world.generation.service.ChunkGenerationService;
import de.heger.voxelengine.world.generation.thread.LoggingTaskResultHandler;
import de.heger.voxelengine.world.generation.thread.PerformanceTrackingTaskResultHandler;
import de.heger.voxelengine.world.generation.thread.TaskResultHandler;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

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

    private static final float DAY_NIGHT_CYCLE_DURATION_SECONDS = 600.0f; // 10 minutes
    private double gameTimeInSeconds = DAY_NIGHT_CYCLE_DURATION_SECONDS * 0.5; // Start at midday
    private boolean isTimeOfDayEnabled = true;
    private boolean wasF4Pressed = false;

    private float currentNormalizedTimeOfDay = 0.5f; // To store the latest calculated normalized time

    private PerformanceTrackingTaskResultHandler performanceTrackingHandler;
    private int currentFps = 0;
    private int currentUps = 0;

    private boolean wasF3Pressed = false;
    private boolean wasF2Pressed = false;
    private boolean wasEscapePressed = false;

    private UIManager uiManager;
    private DebugMenu debugMenu;
    private boolean performanceDisplayWasVisible = true;
    private PauseMenu pauseMenu;
    private SettingsMenu settingsMenu;
    private boolean isPaused = false;
    private boolean isSettingsMenuOpen = false;
    private DebugMenu.DebugData debugData;
    private Config config;

    // Track previous window dimensions for proper resize detection
    private int previousWindowWidth = -1;
    private int previousWindowHeight = -1;
    private volatile boolean needsMenuRebuild = false;

    public GameLoop(String windowTitle, int width, int height, boolean vsync, boolean fullscreen, float viewDistance) {
        LOGGER.info("Initializing game loop...");

        config = ConfigManager.load();
        if (config == null) {
            LOGGER.warn("Failed to load config.json, creating default config.");
            config = new Config();
            // Optionally save the new default config immediately
            // ConfigManager.save(config);
        }
        // Use config values for window creation and camera
        window = new Window(width, height, windowTitle, config.isVsync(), fullscreen, "/window.png");
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
        camera.setViewDistance(config.getViewDistance()); // Use config view distance
        LOGGER.info("Renderer initialized with view distance: {}", config.getViewDistance());

        uiManager = new UIManager();
        uiManager.init(window);

        if (!uiManager.isInitialized()) {
            LOGGER.error("UIManager failed to initialize. UI features will be disabled.");
        } else {
            FontManager actualFontManager = uiManager.getFontManager();
            Font defaultFont = actualFontManager.getDefaultFont();

            if (defaultFont == null) {
                LOGGER.error(
                        "Default font not available from UIManager's FontManager. Performance display cannot be created.");
            } else {
                debugMenu = new DebugMenu(uiManager, defaultFont);
                debugMenu.init();
                debugMenu.setVisible(true);
                performanceDisplayWasVisible = true;
            }

            if (defaultFont != null) {
                pauseMenu = new PauseMenu(uiManager, window, defaultFont);
                pauseMenu.setOnContinueAction(this::togglePauseState);
                pauseMenu.setOnSettingsAction(this::toggleSettingsMenu);
                pauseMenu.setOnQuitAction(() -> {
                    running = false;
                    LOGGER.info("Quit action from pause menu.");
                });

                settingsMenu = new SettingsMenu(uiManager, window, defaultFont, config, camera);
                settingsMenu.setOnBackAction(() -> {
                    if (settingsMenu.isVisible()) {
                        settingsMenu.hide();
                    }
                    if (pauseMenu != null && !pauseMenu.isVisible()) {
                        pauseMenu.show();
                    }
                    isSettingsMenuOpen = false; // Ensure this flag is correctly managed
                });

            } else {
                LOGGER.error("Default font not available, PauseMenu and SettingsMenu cannot be created.");
            }
        }
        debugData = new DebugMenu.DebugData();

        TerrainGenerator noiseTerrainGen = new NoiseTerrainGenerator(1337, 50, 40);
        TaskResultHandler loggingHandler = new LoggingTaskResultHandler();
        this.performanceTrackingHandler = new PerformanceTrackingTaskResultHandler(loggingHandler);

        int horizontalDim = (CHUNK_LOAD_RADIUS * 2) + 1;
        int calculatedMaxTasks = horizontalDim * horizontalDim * MAX_WORLD_HEIGHT_CHUNKS;
        int queueCapacity = Math.max(256, (int) (calculatedMaxTasks * 1.5));
        LOGGER.info("Calculated ChunkGenerationService queue capacity: {} for CHUNK_LOAD_RADIUS {}", queueCapacity,
                CHUNK_LOAD_RADIUS);

        int corePoolSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        int maxPoolSize = Math.max(corePoolSize, Runtime.getRuntime().availableProcessors() - 1);
        if (maxPoolSize <= corePoolSize)
            maxPoolSize = corePoolSize + 1;
        int keepAliveSeconds = 60;

        this.chunkGenerationService = new ChunkGenerationService(
                noiseTerrainGen,
                this.performanceTrackingHandler,
                corePoolSize, maxPoolSize, keepAliveSeconds,
                queueCapacity);
        LOGGER.info("ChunkGenerationService initialized.");
        LOGGER.info("Generating {} initial chunks", (CHUNK_LOAD_RADIUS * 2 + 1)
                * (CHUNK_LOAD_RADIUS * 2 + 1) * MAX_WORLD_HEIGHT_CHUNKS);

        GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        LOGGER.info("Mouse cursor captured.");

        this.lastCameraXZChunkPos = new ChunkPos(Integer.MIN_VALUE, 0, Integer.MIN_VALUE);

        // Register window resize callback to handle menu reconstruction when window dimensions change significantly
        window.addFramebufferSizeCallback(this::handleWindowResize);

        // Initialize previous window dimensions for resize detection
        this.previousWindowWidth = window.getWidth();
        this.previousWindowHeight = window.getHeight();

        LOGGER.info("Game loop initialized.");
    }

    public void run() {
        LOGGER.info("Starting game loop...");
        running = true;

        updateChunkLoading();

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

                if (uiManager != null && uiManager.isInitialized()) {
                    uiManager.processInput(inputManager, window);
                    uiManager.processKeyboardInput(inputManager);
                }

                input();

                while (accumulator >= timeU) {
                    update((float) timeU);
                    accumulator -= timeU;
                    updates++;
                }

                if (uiManager != null && uiManager.isInitialized()) {
                    uiManager.update((float) delta);
                    if (debugMenu != null && debugMenu.isVisible()) {
                        debugMenu.update(debugData);
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

                    debugData.fps = this.currentFps;
                    debugData.ups = this.currentUps;
                    if (performanceTrackingHandler != null) {
                        debugData.avgChunkGenTime = performanceTrackingHandler.getAverageGenerationTimeMillis();
                        debugData.chunkGenSamples = performanceTrackingHandler.getSampleCount();
                    }
                    debugData.drawCalls = renderer.getDrawCallsLastFrame();
                    debugData.renderedIndices = renderer.getTotalIndicesRenderedLastFrame();
                    debugData.occlusionCulledChunks = renderer.getOcclusionCulledChunksLastFrame();
                    debugData.frustumCulledChunks = renderer.getFrustumCulledChunksLastFrame();
                    debugData.totalLoadedChunks = chunkManager.getLoadedChunkCount();
                    debugData.activeMeshes = renderer.getActiveMeshCount();
                    if (chunkGenerationService != null) {
                        debugData.generationQueueSize = chunkGenerationService.getPendingTaskCount();
                        debugData.activeGenerationThreads = chunkGenerationService.getActiveWorkerCount();
                    }
                    debugData.isTimeOfDayEnabled = isTimeOfDayEnabled;
                    debugData.normalizedTimeOfDay = this.currentNormalizedTimeOfDay; // Pass the time to
                                                                                           // performance data
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception in game loop:", e);
        } finally {
            cleanup();
        }
    }

    private void input() {
        boolean isF4Pressed = inputManager.isKeyPressed(GLFW_KEY_F4);
        if (isF4Pressed && !wasF4Pressed) {
            toggleTimeOfDay();
        }
        wasF4Pressed = isF4Pressed;


        boolean isF3Pressed = inputManager.isKeyPressed(GLFW_KEY_F3);
        if (isF3Pressed && !wasF3Pressed) {
            renderer.toggleWireframeMode();
        }
        wasF3Pressed = isF3Pressed;

        boolean isF2Pressed = inputManager.isKeyPressed(GLFW_KEY_F2);
        if (isF2Pressed && !wasF2Pressed) {
            if (debugMenu != null && !isPaused) {
                debugMenu.toggleVisibility();
                performanceDisplayWasVisible = debugMenu.isVisible();
                LOGGER.debug("Performance display visibility toggled to: {}", debugMenu.isVisible());
            }
        }
        wasF2Pressed = isF2Pressed;

        boolean isEscapePressed = inputManager.isKeyPressed(GLFW_KEY_ESCAPE);
        if (isEscapePressed && !wasEscapePressed) {
            if (isSettingsMenuOpen) {
                toggleSettingsMenu();
            } else {
                togglePauseState();
            }
        }
        wasEscapePressed = isEscapePressed;

        if (!isPaused && (uiManager == null || !uiManager.isInitialized() || !uiManager.uiHasFocus())) {
            double deltaX = inputManager.getDeltaMouseX();
            double deltaY = inputManager.getDeltaMouseY();
            if (deltaX != 0 || deltaY != 0) {
                camera.processMouseMovement(deltaX, deltaY);
            }
        }
    }

    private void update(float deltaTime) {
        if (!isPaused) {
            chunkLoadCheckTimer += deltaTime;
            if (chunkLoadCheckTimer >= CHUNK_LOAD_CHECK_INTERVAL) {
                updateChunkLoading();
                chunkLoadCheckTimer = 0.0;
            }

            if (isTimeOfDayEnabled) {
                // Update game time for day/night cycle
                gameTimeInSeconds += deltaTime;
                this.currentNormalizedTimeOfDay = (float) (gameTimeInSeconds % DAY_NIGHT_CYCLE_DURATION_SECONDS)
                        / DAY_NIGHT_CYCLE_DURATION_SECONDS;
                if (renderer != null) {
                    renderer.updateTimeOfDay(this.currentNormalizedTimeOfDay);
                }
            }
            if (uiManager != null && uiManager.isInitialized() && uiManager.uiHasFocus()) {
                // UI has focus (e.g. a text input in a future menu), game world movement might
                // be paused
                // This case should ideally not happen if isPaused is true and handles focus.
                // However, if a different UI element (not pause menu) takes focus, this might
                // be relevant.
            } else {
                camera.processKeyboard(inputManager, deltaTime);
            }
        } else { // Game is paused
            // UI has focus during pause state
            // Window resize handling is now managed by the framebuffer callback system
            // which properly rebuilds menus when needed, following OpenGL best practices
        }
        
        // Check for delayed menu rebuild after window resize
        if (needsMenuRebuild) {
            LOGGER.debug("Executing menu rebuild after window resize");
            rebuildMenusIfVisible();
            needsMenuRebuild = false;
        }
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

        if (debugMenu != null) {
            debugMenu.cleanup();
        }
        if (pauseMenu != null) {
            pauseMenu.cleanup();
        }
        if (settingsMenu != null) {
            settingsMenu.cleanup();
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
            LOGGER.debug("Requested {} new chunks for generation. {} were already loaded/queued in radius.",
                    chunksRequestedThisCycle, chunksAlreadyManagedThisCycle);
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
                    // Ensure renderer resources are freed for this chunk.
                    renderer.releaseChunkResources(posToUnload);

                    chunkManager.removeChunk(posToUnload); // Remove from game logic management
                    actualUnloads++;
                    this.chunkGenerationService.cancelTask(posToUnload); // Cancel any pending generation
                }
            }
            if (actualUnloads > 0) {
                LOGGER.debug("Successfully unloaded {} chunks. {} were requested for unload.", actualUnloads,
                        chunksToUnload.size());
            }
        }
    }

    private void togglePauseState() {
        isPaused = !isPaused;
        if (uiManager != null && uiManager.isInitialized()) {
            uiManager.setUiHasFocus(isPaused);
            if (isPaused) {
                LOGGER.info("Game paused.");
                GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                if (pauseMenu != null) {
                    pauseMenu.show();
                }
                if (settingsMenu != null && settingsMenu.isVisible()) { // Hide settings if open
                    settingsMenu.hide();
                    isSettingsMenuOpen = false;
                }
                // If performance display was visible, hide it
                if (debugMenu != null && debugMenu.isVisible()) {
                    performanceDisplayWasVisible = true;
                    debugMenu.setVisible(false);
                }
            } else {
                LOGGER.info("Game resumed.");
                GLFW.glfwSetInputMode(window.getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                if (pauseMenu != null) {
                    pauseMenu.hide();
                }
                // If performance display was visible before pausing, show it again
                if (debugMenu != null && performanceDisplayWasVisible) {
                    debugMenu.setVisible(true);
                }
            }
        }
    }

    private void toggleSettingsMenu() {
        isSettingsMenuOpen = !isSettingsMenuOpen;
        if (isSettingsMenuOpen) {
            LOGGER.info("Opening settings menu.");
            if (pauseMenu != null && pauseMenu.isVisible()) {
                pauseMenu.hide();
            }
            if (settingsMenu != null) {
                settingsMenu.show();
            }
        } else {
            LOGGER.info("Closing settings menu, returning to pause menu.");
            if (settingsMenu != null && settingsMenu.isVisible()) {
                settingsMenu.hide();
            }
            if (pauseMenu != null && !pauseMenu.isVisible()) { // Check if pause menu is not already visible
                pauseMenu.show();
            }
        }
    }

    public void toggleTimeOfDay() {
        isTimeOfDayEnabled = !isTimeOfDayEnabled;
        LOGGER.info("Time of day toggled to: {}", isTimeOfDayEnabled);
    }

    private void handleWindowResize(long windowHandle, int width, int height) {
        LOGGER.debug("Window resize callback triggered: {}x{} (previous: {}x{})", width, height, previousWindowWidth, previousWindowHeight);
        
        // Only rebuild menus if they exist and window dimensions are valid
        if (width <= 0 || height <= 0) {
            LOGGER.warn("Invalid window dimensions received in resize callback: {}x{}", width, height);
            return;
        }
        
        // Check if we need to rebuild UI components due to significant size changes
        boolean shouldRebuild = shouldRebuildMenus(width, height);
        
        if (shouldRebuild) {
            LOGGER.info("Window dimensions changed significantly from {}x{} to {}x{}, scheduling menu rebuild...", 
                       previousWindowWidth, previousWindowHeight, width, height);
            needsMenuRebuild = true;
        }
        
        // Update tracking variables for next comparison
        this.previousWindowWidth = width;
        this.previousWindowHeight = height;
    }
    
    private boolean shouldRebuildMenus(int newWidth, int newHeight) {
        // If we don't have previous dimensions yet, don't rebuild
        if (previousWindowWidth <= 0 || previousWindowHeight <= 0) {
            return false;
        }
        
        // Calculate absolute and percentage changes from previous dimensions
        int widthDiff = Math.abs(newWidth - previousWindowWidth);
        int heightDiff = Math.abs(newHeight - previousWindowHeight);
        double widthChange = (double)widthDiff / previousWindowWidth;
        double heightChange = (double)heightDiff / previousWindowHeight;
        
        // Rebuild if there's a significant change (> 10%) OR a large absolute change (> 50 pixels)
        // This handles both percentage-based changes for larger windows and absolute changes for smaller windows
        boolean significantChange = widthChange > 0.1 || heightChange > 0.1 || widthDiff > 50 || heightDiff > 50;
        
        // Also rebuild if aspect ratio changes significantly
        double oldAspectRatio = (double)previousWindowWidth / previousWindowHeight;
        double newAspectRatio = (double)newWidth / newHeight;
        boolean aspectRatioChanged = Math.abs(newAspectRatio - oldAspectRatio) > 0.1;
        
        LOGGER.debug("Size change analysis: width={}px ({}%) height={}px ({}%) aspect ratio change={}", 
                    widthDiff, String.format("%.1f", widthChange * 100), 
                    heightDiff, String.format("%.1f", heightChange * 100), 
                    aspectRatioChanged);
        
        return significantChange || aspectRatioChanged;
    }
    
    private void scheduleMenuRebuild() {
        // This method could be expanded to use a proper scheduler, 
        // but for now we'll handle it in the update loop
        // The actual rebuild will happen in the update method after the delay
    }

    private void rebuildMenusIfVisible() {
        if (uiManager == null || !uiManager.isInitialized()) {
            return;
        }
        
        // Store current menu state
        boolean pauseMenuWasVisible = pauseMenu != null && pauseMenu.isVisible();
        boolean settingsMenuWasVisible = settingsMenu != null && settingsMenu.isVisible();
        
        // Clean up existing menus following OpenGL best practices
        if (pauseMenu != null) {
            pauseMenu.cleanup(); // This frees OpenGL resources
            pauseMenu = null;
        }
        
        if (settingsMenu != null) {
            settingsMenu.cleanup(); // This frees OpenGL resources  
            settingsMenu = null;
        }
        
        // Recreate menus with current window dimensions
        FontManager fontManager = uiManager.getFontManager();
        Font defaultFont = fontManager != null ? fontManager.getDefaultFont() : null;
        
        if (defaultFont != null) {
            // Recreate PauseMenu
            pauseMenu = new PauseMenu(uiManager, window, defaultFont);
            pauseMenu.setOnContinueAction(this::togglePauseState);
            pauseMenu.setOnSettingsAction(this::toggleSettingsMenu);
            pauseMenu.setOnQuitAction(() -> {
                running = false;
                LOGGER.info("Quit action from pause menu.");
            });
            
            // Recreate SettingsMenu
            settingsMenu = new SettingsMenu(uiManager, window, defaultFont, config, camera);
            settingsMenu.setOnBackAction(() -> {
                if (settingsMenu.isVisible()) {
                    settingsMenu.hide();
                }
                if (pauseMenu != null && !pauseMenu.isVisible()) {
                    pauseMenu.show();
                }
                isSettingsMenuOpen = false;
            });
            
            // Restore menu visibility state
            if (pauseMenuWasVisible && pauseMenu != null) {
                pauseMenu.show();
                LOGGER.debug("Restored PauseMenu visibility after rebuild");
            }
            
            if (settingsMenuWasVisible && settingsMenu != null) {
                settingsMenu.show();
                LOGGER.debug("Restored SettingsMenu visibility after rebuild");
            }
            
            LOGGER.info("Successfully rebuilt menus with new window dimensions");
        } else {
            LOGGER.error("Cannot rebuild menus: default font is null");
        }
    }
}
