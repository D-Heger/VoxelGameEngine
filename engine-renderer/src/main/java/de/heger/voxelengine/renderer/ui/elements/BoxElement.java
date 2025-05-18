package de.heger.voxelengine.renderer.ui.elements;

import de.heger.voxelengine.core.logging.LoggerFacade;
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

public class BoxElement extends UIElement {
    private static final LoggerFacade LOGGER = LoggerFacade.get(BoxElement.class);

    private Vector4f color;
    private int vaoId = -1, vboId = -1, eboId = -1;
    private int indexCount = 0;
    private final Matrix4f modelMatrix = new Matrix4f();

    public BoxElement(Vector2f position, Vector2f size, Vector4f color) {
        super(position, size);
        this.color = color;
        buildMesh();
    }

    public Vector4f getColor() {
        return color;
    }

    public void setColor(Vector4f color) {
        this.color = color;
    }

    public void setSize(Vector2f size) {
        if (!this.size.equals(size)) {
            this.size.set(size);
            buildMesh(); // Rebuild mesh if size changes
        }
    }

    private void buildMesh() {
        cleanupMesh();

        float w = this.size.x;
        float h = this.size.y;

        //     Position    TexCoords (can be dummy, but shader expects them)
        float[] vertices = {
            0, 0, 0, 0, // Top-left
            0, h,  0, 1, // Bottom-left
            w, h,  1, 1, // Bottom-right
            w, 0, 1, 0  // Top-right
        };

        int[] indices = {
            0, 1, 2, // First triangle
            0, 2, 3  // Second triangle
        };

        indexCount = indices.length;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer verticesBuffer = stack.mallocFloat(vertices.length);
            verticesBuffer.put(vertices).flip();

            IntBuffer indicesBuffer = stack.mallocInt(indices.length);
            indicesBuffer.put(indices).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            eboId = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            // Position attribute
            glVertexAttribPointer(UIShader.ATTRIB_POSITION, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
            glEnableVertexAttribArray(UIShader.ATTRIB_POSITION);
            // Texture coordinate attribute
            glVertexAttribPointer(UIShader.ATTRIB_TEX_COORDS, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
            glEnableVertexAttribArray(UIShader.ATTRIB_TEX_COORDS);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
        LOGGER.trace("Built mesh for BoxElement, VAO: {}, Size: {}x{}", vaoId, w, h);
    }

    @Override
    public void render(UIRenderer renderer) {
        if (!visible || vaoId == -1 || indexCount == 0) {
            return;
        }

        UIShader shader = renderer.getUIShader();
        
        shader.loadUseTexture(false); // Tell shader not to use a texture
        modelMatrix.identity().translate(position.x, position.y, 0);
        shader.loadModelMatrix(modelMatrix);
        shader.loadColor(this.color);
        shader.loadAlpha(this.alpha * renderer.getCurrentAlpha());

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void cleanupMesh() {
        if (vaoId != -1) { glDeleteVertexArrays(vaoId); vaoId = -1; }
        if (vboId != -1) { glDeleteBuffers(vboId); vboId = -1; }
        if (eboId != -1) { glDeleteBuffers(eboId); eboId = -1; }
        indexCount = 0;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cleanupMesh();
        LOGGER.trace("Cleaned up BoxElement");
    }
} 