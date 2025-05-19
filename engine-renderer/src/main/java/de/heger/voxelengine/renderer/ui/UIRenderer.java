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

        // Save current GL state that we will change
        boolean depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        int blendSrc = glGetInteger(GL_BLEND_SRC);
        int blendDst = glGetInteger(GL_BLEND_DST);
        //int cullFaceEnabled = glIsEnabled(GL_CULL_FACE);

        // Setup GL state for UI rendering
        if (depthTestEnabled) {
            glDisable(GL_DEPTH_TEST);
        }
        if (!blendEnabled) {
            glEnable(GL_BLEND);
        }
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // Standard alpha blending
        //glDisable(GL_CULL_FACE); // UI elements are typically 2D, culling might not be desired or handled by shader

        uiShader.bind();
        uiShader.loadProjectionMatrix(projectionMatrix);

        for (UIElement element : elementsToRender) {
            if (element.isVisible()) {
                // The element's render method will set its own model matrix, color, alpha, texture
                element.render(this); 
            }
        }

        uiShader.unbind();

        // Restore GL state
        if (depthTestEnabled) {
            glEnable(GL_DEPTH_TEST);
        }
        if (!blendEnabled) {
            glDisable(GL_BLEND);
        } else {
            // Restore previous blend function if we knew it, otherwise default or standard
             glBlendFunc(blendSrc, blendDst); // Restore old blend func
        }
        //if(cullFaceEnabled) glEnable(GL_CULL_FACE);
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
        // Elements are no longer managed here, so no loop to clear/cleanup elements
        // elements.clear(); // To be removed
    }
} 