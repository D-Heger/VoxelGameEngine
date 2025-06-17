package de.heger.voxelengine.renderer.ui.elements;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIRenderer;
import de.heger.voxelengine.renderer.ui.UIShader;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Simple UI element that renders an X-shaped crosshair in the centre of the screen.
 * It uses GL_LINES to draw two diagonal lines inside a 16×16 pixel square.
 * <p>
 * The element is non-interactive – it never receives mouse events.
 */
public class CrosshairElement extends UIElement {

    private static final LoggerFacade LOGGER = LoggerFacade.get(CrosshairElement.class);

    // Default crosshair size in pixels (width = height).
    private static final int DEFAULT_SIZE_PX = 16;

    // Colour (semi-transparent grey)
    private static final Vector4f DEFAULT_COLOR = new Vector4f(0.75f, 0.75f, 0.75f, 0.7f);

    private final Window window;

    private int vaoId = -1;
    private int vboId = -1;
    private int eboId = -1;
    private int indexCount = 0;

    private final Matrix4f modelMatrix = new Matrix4f();
    private final Vector2f screenPos = new Vector2f();

    private Vector4f color = new Vector4f(DEFAULT_COLOR);

    public CrosshairElement(Window window) {
        super();
        this.window = window;
        // The element has no parent, so absolute positioning is used. The size is fixed.
        super.setSize(DEFAULT_SIZE_PX, DEFAULT_SIZE_PX);
        super.setVisible(true);
        super.setAlpha(DEFAULT_COLOR.w);
        buildMesh();
    }

    private void buildMesh() {
        cleanupMesh();

        float w = this.size.x; // 16
        float h = this.size.y; // 16

        /*
         * Vertex layout: [x, y, u, v] – dummy texture coords because UIShader expects them.
         * Four vertices – corners of the square. Two lines will connect (0,0)-(w,h) and (0,h)-(w,0).
         */
        float[] vertices = {
                0f, 0f, 0f, 0f,      // Top-left (index 0)
                w, h, 1f, 1f,        // Bottom-right (index 1)
                0f, h, 0f, 1f,       // Bottom-left (index 2)
                w, 0f, 1f, 0f        // Top-right (index 3)
        };

        int[] indices = {
                0, 1, // First diagonal
                2, 3  // Second diagonal
        };

        this.indexCount = indices.length;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer vb = stack.mallocFloat(vertices.length);
            vb.put(vertices).flip();

            IntBuffer ib = stack.mallocInt(indices.length);
            ib.put(indices).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);

            eboId = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);

            // Position attribute (vec2)
            glVertexAttribPointer(UIShader.ATTRIB_POSITION, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
            glEnableVertexAttribArray(UIShader.ATTRIB_POSITION);
            // Dummy tex-coords attribute (vec2)
            glVertexAttribPointer(UIShader.ATTRIB_TEX_COORDS, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
            glEnableVertexAttribArray(UIShader.ATTRIB_TEX_COORDS);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }

        LOGGER.trace("CrosshairElement mesh built (VAO: {}, size: {}x{})", vaoId, w, h);
    }

    @Override
    public void render(UIRenderer renderer) {
        if (!visible || vaoId == -1 || indexCount == 0) {
            return;
        }

        // Calculate screen centre position each frame in case the window was resized.
        int winW = window.getWidth();
        int winH = window.getHeight();
        float posX = (winW - size.x) * 0.5f;
        float posY = (winH - size.y) * 0.5f;
        screenPos.set(posX, posY);

        UIShader shader = renderer.getUIShader();
        shader.loadUseTexture(false);
        shader.loadColor(color);
        shader.loadAlpha(this.alpha * renderer.getCurrentAlpha());

        modelMatrix.identity().translate(screenPos.x, screenPos.y, 0f);
        shader.loadModelMatrix(modelMatrix);

        // Render two diagonal lines.
        glBindVertexArray(vaoId);
        // Slightly thicker line for visibility.
        glLineWidth(2f);
        glDrawElements(GL_LINES, indexCount, GL_UNSIGNED_INT, 0);
        // Reset to default to avoid impacting other draws.
        glLineWidth(1f);
        glBindVertexArray(0);
    }

    @Override
    public boolean isMouseOver(float mouseX, float mouseY) {
        // Crosshair is non-interactive.
        return false;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cleanupMesh();
        LOGGER.trace("CrosshairElement cleaned up");
    }

    private void cleanupMesh() {
        if (vaoId != -1) {
            glDeleteVertexArrays(vaoId);
            vaoId = -1;
        }
        if (vboId != -1) {
            glDeleteBuffers(vboId);
            vboId = -1;
        }
        if (eboId != -1) {
            glDeleteBuffers(eboId);
            eboId = -1;
        }
        indexCount = 0;
    }

    // Optional: allow external colour change
    public void setColor(Vector4f newColor) {
        this.color.set(newColor);
    }
} 