package de.heger.voxelengine.renderer.mesh;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import org.lwjgl.opengl.GL11;

/**
 * Mesh encapsulates a VAO, VBO, and EBO for rendering simple shapes.
 */
public class Mesh {
    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int vertexCount;

    private Mesh(int vaoId, int vboId, int eboId, int vertexCount) {
        this.vaoId = vaoId;
        this.vboId = vboId;
        this.eboId = eboId;
        this.vertexCount = vertexCount;
    }

    /**
     * Creates a Mesh from given vertex data (positions, texture coords) and indices.
     *
     * @param vertices Vertex data (e.g., [posX, posY, posZ, texU, texV, ...])
     * @param indices  Triangle indices
     * @return The created Mesh object.
     */
    public static Mesh create(float[] vertices, int[] indices) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // VBO for vertex data
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        // --- Define Vertex Attributes ---
        int stride = 5 * Float.BYTES; // 3 position floats + 2 texture coord floats

        // Position attribute (location = 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);

        // Texture coordinate attribute (location = 1)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES); // Offset by 3 floats

        // EBO for indices
        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        // Unbind VAO
        glBindVertexArray(0);
        // Unbind buffers (optional but good practice)
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        return new Mesh(vao, vbo, ebo, indices.length);
    }

    /**
     * Render the mesh by binding its VAO and issuing draw call.
     */
    public void render() {
        glBindVertexArray(vaoId);
        GL11.glDrawElements(GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    /**
     * Cleanup OpenGL resources.
     */
    public void cleanup() {
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1); // Disable texture coord attribute
        // Delete buffers
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDeleteBuffers(eboId);
        // Delete VAO
        glDeleteVertexArrays(vaoId);
    }

    /**
     * Convenience method to create a unit cube centered at origin with texture coordinates.
     */
    public static Mesh createCube() {
        // Define vertices with position (3) and texture coords (2)
        float[] vertices = {
                // Position           // TexCoords
                // Back face
                -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,
                 0.5f, -0.5f, -0.5f,  1.0f, 0.0f,
                 0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,

                // Front face
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
                 0.5f, -0.5f,  0.5f,  1.0f, 0.0f,
                 0.5f,  0.5f,  0.5f,  1.0f, 1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,

                // Left face
                -0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,

                // Right face
                 0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                 0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                 0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                 0.5f, -0.5f,  0.5f,  0.0f, 0.0f,

                // Bottom face
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                 0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
                 0.5f, -0.5f,  0.5f,  1.0f, 0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,

                // Top face
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,
                 0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                 0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 0.0f
        };

        int[] indices = {
                // Back face (negative Z)
                0, 2, 1, 
                2, 0, 3,
                // Front face (positive Z)
                4, 6, 7,
                6, 4, 5,
                // Left face (negative X)
                8, 10, 11,
                10, 8, 9,
                // Right face (positive X)
                12, 14, 13,
                14, 12, 15,
                // Bottom face (negative Y)
                16, 18, 19,
                18, 16, 17,
                // Top face (positive Y)
                20, 22, 21,
                22, 20, 23
        };

        return create(vertices, indices);
    }
}
