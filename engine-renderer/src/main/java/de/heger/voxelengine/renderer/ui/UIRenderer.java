package de.heger.voxelengine.renderer.ui;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;

import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

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

        // Batch render elements by type to minimize state changes
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
    
    private void renderElementsBatched(List<UIElement> elements) {
        // Simple batching: render all non-textured elements first, then textured ones
        // This reduces texture binding state changes
        
        // First pass: non-textured elements (boxes, etc.)
        for (UIElement element : elements) {
            if (element.isVisible() && isNonTexturedElement(element)) {
                element.render(this);
            }
        }
        
        // Second pass: textured elements (text, etc.)
        for (UIElement element : elements) {
            if (element.isVisible() && !isNonTexturedElement(element)) {
                element.render(this);
            }
        }
    }
    
    private boolean isNonTexturedElement(UIElement element) {
        // Simple heuristic: BoxElement and ButtonElement backgrounds are non-textured
        return element.getClass().getSimpleName().contains("Box");
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