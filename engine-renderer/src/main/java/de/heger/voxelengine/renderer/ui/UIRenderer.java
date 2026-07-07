package de.heger.voxelengine.renderer.ui;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.ui.elements.BoxElement;
import de.heger.voxelengine.renderer.ui.elements.TextElement;
import de.heger.voxelengine.renderer.ui.elements.ButtonElement;

import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws the UI tree on top of the rendered world.
 *
 * <p>Where {@link UIManager} decides <em>what</em> the interface is and handles
 * input, the {@code UIRenderer} is responsible for <em>painting</em> it. It sets
 * up the orthographic (screen-space) projection, configures the GL state suited to
 * 2D overlay drawing (blending on, depth testing off), and walks the visible
 * elements, dispatching each to its type-specific draw routine via the shared
 * {@link UIShader}. It also rebuilds that projection when the window is
 * resized.</p>
 */
public class UIRenderer {
    private static final LoggerFacade LOGGER = LoggerFacade.get(UIRenderer.class);

    private final UIShader uiShader;
    private final Matrix4f projectionMatrix;

    private float currentGlobalAlpha = 1.0f;
    
    // Cache GL state to avoid expensive queries every frame
    private static class GLStateCache {
        boolean depthTestEnabled = false;
        boolean blendEnabled = false;
        int blendSrc = GL_SRC_ALPHA;
        int blendDst = GL_ONE_MINUS_SRC_ALPHA;
        boolean stateInitialized = false;
        
        void initializeFromGL() {
            if (!stateInitialized) {
                depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
                blendEnabled = glIsEnabled(GL_BLEND);
                if (blendEnabled) {
                    blendSrc = glGetInteger(GL_BLEND_SRC);
                    blendDst = glGetInteger(GL_BLEND_DST);
                }
                stateInitialized = true;
            }
        }
        
        void reset() {
            stateInitialized = false;
        }
    }
    
    /**
     * Represents the rendering state/category of a UI element for batching purposes.
     * Elements with the same RenderState can be batched together efficiently.
     */
    public enum RenderState {
        /** Non-textured solid color elements (BoxElement backgrounds, etc.) */
        SOLID_COLOR(false, false),
        /** Textured elements that use font atlases (TextElement) */
        FONT_TEXTURED(true, false),
        /** Textured elements that use regular image textures (ImageElement, etc.) */
        IMAGE_TEXTURED(true, false),
        /** Elements that require custom render states or can't be easily batched */
        CUSTOM(false, true);
        
        public final boolean usesTexture;
        public final boolean requiresCustomState;
        
        RenderState(boolean usesTexture, boolean requiresCustomState) {
            this.usesTexture = usesTexture;
            this.requiresCustomState = requiresCustomState;
        }
    }
    
    /**
     * Represents a batch of UI elements that can be rendered with the same OpenGL state.
     */
    private static class RenderBatch {
        public final RenderState state;
        public final List<UIElement> elements;
        
        public RenderBatch(RenderState state) {
            this.state = state;
            this.elements = new ArrayList<>();
        }
        
        public void addElement(UIElement element) {
            elements.add(element);
        }
        
        public boolean isEmpty() {
            return elements.isEmpty();
        }
        
        public int size() {
            return elements.size();
        }
    }
    
    private final GLStateCache glStateCache = new GLStateCache();

    public UIRenderer(Window window) throws IOException {
        try {
            this.uiShader = new UIShader();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize UIShader in UIRenderer.", e);
            throw e;
        }
        this.projectionMatrix = new Matrix4f();
        updateProjectionMatrix(window.getWidth(), window.getHeight());
        // Window resize listener should call updateProjectionMatrix
        window.addFramebufferSizeCallback((win, w, h) -> {
            if (w > 0 && h > 0) {
                updateProjectionMatrix(w,h);
                // Reset state cache on resize as context might change
                glStateCache.reset();
            }
        });
    }

    public void updateProjectionMatrix(int width, int height) {
        projectionMatrix.identity();
        // Orthographic projection: 0,0 at top-left corner
        projectionMatrix.ortho(0.0f, (float)width, (float)height, 0.0f, -1.0f, 1.0f);
        LOGGER.debug("UIRenderer projection matrix updated for screen size: {}x{}", width, height);
    }

    public UIShader getUIShader() {
        return uiShader;
    }
    
    public float getCurrentAlpha() {
        return currentGlobalAlpha;
    }

    public void setCurrentAlpha(float alpha) {
        this.currentGlobalAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    public void render(List<UIElement> elementsToRender) {
        if (elementsToRender == null || elementsToRender.isEmpty()) {
            return;
        }

        // Initialize state cache once per session
        glStateCache.initializeFromGL();

        // Setup GL state for UI rendering - only change what we need to
        boolean needRestoreDepthTest = false;
        boolean needRestoreBlend = false;
        boolean needRestoreBlendFunc = false;
        
        if (glStateCache.depthTestEnabled) {
            glDisable(GL_DEPTH_TEST);
            needRestoreDepthTest = true;
        }
        
        if (!glStateCache.blendEnabled) {
            glEnable(GL_BLEND);
            needRestoreBlend = true;
        }
        
        // Check if we need to change blend function
        int currentBlendSrc = glGetInteger(GL_BLEND_SRC);
        int currentBlendDst = glGetInteger(GL_BLEND_DST);
        if (currentBlendSrc != GL_SRC_ALPHA || currentBlendDst != GL_ONE_MINUS_SRC_ALPHA) {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            needRestoreBlendFunc = true;
        }

        uiShader.bind();
        uiShader.loadProjectionMatrix(projectionMatrix);

        // Batch render elements by render state to minimize state changes
        renderElementsBatched(elementsToRender);

        uiShader.unbind();

        // Restore only the GL state we actually changed
        if (needRestoreDepthTest) {
            glEnable(GL_DEPTH_TEST);
        }
        if (needRestoreBlend) {
            glDisable(GL_BLEND);
        }
        if (needRestoreBlendFunc) {
            glBlendFunc(glStateCache.blendSrc, glStateCache.blendDst);
        }
    }
    
    /**
     * Renders elements in batches grouped by their render state to minimize OpenGL state changes.
     * This significantly improves rendering performance by reducing draw calls and texture binding.
     */
    private void renderElementsBatched(List<UIElement> elements) {
        // Categorize elements into render batches
        Map<RenderState, RenderBatch> batches = categorizeElementsIntoBatches(elements);
        
        // Render batches in optimal order:
        // 1. SOLID_COLOR - No texture binding needed
        // 2. FONT_TEXTURED - Font atlas textures
        // 3. IMAGE_TEXTURED - Image textures
        // 4. CUSTOM - Elements requiring special handling
        
        RenderState[] renderOrder = {
            RenderState.SOLID_COLOR,
            RenderState.FONT_TEXTURED, 
            RenderState.IMAGE_TEXTURED,
            RenderState.CUSTOM
        };
        
        for (RenderState state : renderOrder) {
            RenderBatch batch = batches.get(state);
            if (batch != null && !batch.isEmpty()) {
                renderBatch(batch);
            }
        }
    }
    
    /**
     * Categorizes UI elements into render batches based on their rendering requirements.
     */
    private Map<RenderState, RenderBatch> categorizeElementsIntoBatches(List<UIElement> elements) {
        Map<RenderState, RenderBatch> batches = new HashMap<>();
        
        // Initialize batches for each render state
        for (RenderState state : RenderState.values()) {
            batches.put(state, new RenderBatch(state));
        }
        
        // Categorize visible elements
        for (UIElement element : elements) {
            if (element.isVisible()) {
                RenderState state = determineElementRenderState(element);
                batches.get(state).addElement(element);
            }
        }
        
        return batches;
    }
    
    /**
     * Determines the appropriate render state for a given UI element.
     * This replaces the old isNonTexturedElement method with a more robust categorization system.
     */
    private RenderState determineElementRenderState(UIElement element) {
        // Handle known element types explicitly
        if (element instanceof TextElement) {
            return RenderState.FONT_TEXTURED;
        }
        
        if (element instanceof BoxElement) {
            return RenderState.SOLID_COLOR;
        }
        
        if (element instanceof ButtonElement) {
            // ButtonElement is a composite that contains both BoxElement (background) and TextElement (text)
            // The ButtonElement itself manages rendering of its components, so treat as custom
            return RenderState.CUSTOM;
        }
        
        // For extensibility, check class name patterns as fallback
        String className = element.getClass().getSimpleName().toLowerCase();
        if (className.contains("text")) {
            return RenderState.FONT_TEXTURED;
        }
        if (className.contains("image") || className.contains("icon")) {
            return RenderState.IMAGE_TEXTURED;
        }
        if (className.contains("box") || className.contains("panel")) {
            return RenderState.SOLID_COLOR;
        }
        
        // Default to custom for unknown element types
        return RenderState.CUSTOM;
    }
    
    /**
     * Renders all elements in a specific batch with appropriate OpenGL state setup.
     */
    private void renderBatch(RenderBatch batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        LOGGER.trace("Rendering batch of {} elements with state: {}", batch.size(), batch.state);
        
        // Setup OpenGL state for this batch type
        switch (batch.state) {
            case SOLID_COLOR:
                // No texture needed - shader will use solid color mode
                uiShader.loadUseTexture(false);
                break;
                
            case FONT_TEXTURED:
                // Font atlas texture will be bound by individual TextElements
                uiShader.loadUseTexture(true);
                break;
                
            case IMAGE_TEXTURED:
                // Image textures will be bound by individual elements
                uiShader.loadUseTexture(true);
                break;
                
            case CUSTOM:
                // Custom elements handle their own state setup
                break;
        }
        
        // Render all elements in this batch
        for (UIElement element : batch.elements) {
            element.render(this);
        }
    }
    
    public void updateElements(List<UIElement> elementsToUpdate, float deltaTime) {
        if (elementsToUpdate == null) return;
        for (UIElement element : elementsToUpdate) {
            if (element.isVisible()) {
                element.update(deltaTime);
            }
        }
    }

    public void cleanup() {
        LOGGER.info("Cleaning up UIRenderer...");
        if (uiShader != null) {
            uiShader.cleanup();
        }
        glStateCache.reset();
    }
} 