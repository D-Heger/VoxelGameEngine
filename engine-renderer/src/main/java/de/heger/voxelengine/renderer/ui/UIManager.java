package de.heger.voxelengine.renderer.ui;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.ui.font.FontManager;

import java.io.IOException;

public class UIManager {
    private static final LoggerFacade LOGGER = LoggerFacade.get(UIManager.class);

    private FontManager fontManager;
    private UIRenderer uiRenderer;
    private boolean initialized = false;

    public UIManager() {
        // Initialization will be done via init() method to control timing
    }

    public void init(Window window) {
        if (initialized) {
            LOGGER.warn("UIManager is already initialized.");
            return;
        }
        try {
            fontManager = new FontManager(); // Loads default font
            uiRenderer = new UIRenderer(window, fontManager);
            initialized = true;
            LOGGER.info("UIManager initialized successfully.");
        } catch (IOException e) {
            LOGGER.error("Failed to initialize UIManager.", e);
            // Depending on policy, could re-throw or leave UIManager in an uninitialized state
            // For now, it will be uninitialized and subsequent calls might fail.
            initialized = false;
        }
    }

    public void addElement(UIElement element) {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot add element.");
            return;
        }
        uiRenderer.addElement(element);
    }

    public void removeElement(UIElement element) {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot remove element.");
            return;
        }
        uiRenderer.removeElement(element);
    }

    public void clearElements() {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot clear elements.");
            return;
        }
        uiRenderer.clearElements();
    }

    public void render() {
        if (!initialized) {
            // LOGGER.trace("UIManager not initialized or nothing to render."); // Can be too spammy
            return;
        }
        uiRenderer.render();
    }
    
    public void update(float deltaTime) {
        if (!initialized) {
            return;
        }
        uiRenderer.updateElements(deltaTime);
    }
    
    public FontManager getFontManager() {
        if (!initialized) {
            LOGGER.warn("UIManager not initialized. FontManager might be null.");
        }
        return fontManager;
    }
    
    public UIRenderer getUIRenderer() {
        if (!initialized) {
            LOGGER.warn("UIManager not initialized. UIRenderer might be null.");
        }
        return uiRenderer;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void cleanup() {
        if (!initialized) {
            return;
        }
        LOGGER.info("Cleaning up UIManager...");
        if (uiRenderer != null) {
            uiRenderer.cleanup();
        }
        if (fontManager != null) {
            fontManager.cleanup();
        }
        initialized = false;
    }
} 