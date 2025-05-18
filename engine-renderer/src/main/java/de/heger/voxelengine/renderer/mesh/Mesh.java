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
@Deprecated
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
     * Creates a Mesh from given vertex data (positions, texture coords, normals) and indices.
     * Vertex data should be interleaved: 3 pos, 2 tex, 3 normal.
     *
     * @param vertices Vertex data
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
        int stride = 8 * Float.BYTES; // 3 position floats + 2 texture coord floats + 3 normal floats

        // Position attribute (location = 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);

        // Texture coordinate attribute (location = 1)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);

        // Normal attribute (location = 2)
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);

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
        glDisableVertexAttribArray(2); // Disable normal attribute
        // Delete buffers
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDeleteBuffers(eboId);
        // Delete VAO
        glDeleteVertexArrays(vaoId);
    }

    // Helper method to create a quad mesh for a specific face
    // Vertices array should contain 4 vertices, each with 8 floats (pos, uv, normal)
    // Indices array should contain 6 indices for 2 triangles forming the quad
    private static Mesh createSingleFace(float[] faceVertices, int[] faceIndices) {
        if (faceVertices.length != 4 * 8) { // 4 vertices, 8 floats each
            throw new IllegalArgumentException("Vertex data for a single face must contain 32 floats (4 vertices * (3 pos + 2 uv + 3 normal)).");
        }
        if (faceIndices.length != 6) { // 2 triangles, 3 indices each
            throw new IllegalArgumentException("Index data for a single face must contain 6 integers.");
        }
        return create(faceVertices, faceIndices);
    }

    // Standard indices for a quad with vertices v0, v1, v2, v3 in CCW order
    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    public static Mesh createUpFace() { // Y+
        float[] vertices = {
                // Positions             // TexCoords    // Normals (0,1,0)
                -0.5f, 0.5f,  0.5f,      0.0f, 0.0f,     0.0f, 1.0f, 0.0f, // v0 (front-left on top)
                 0.5f, 0.5f,  0.5f,      1.0f, 0.0f,     0.0f, 1.0f, 0.0f, // v1 (front-right on top)
                 0.5f, 0.5f, -0.5f,      1.0f, 1.0f,     0.0f, 1.0f, 0.0f, // v2 (back-right on top)
                -0.5f, 0.5f, -0.5f,      0.0f, 1.0f,     0.0f, 1.0f, 0.0f  // v3 (back-left on top)
        };
        return createSingleFace(vertices, QUAD_INDICES);
    }

    public static Mesh createDownFace() { // Y-
        float[] vertices = {
                // Positions             // TexCoords    // Normals (0,-1,0)
                -0.5f, -0.5f, -0.5f,     0.0f, 0.0f,     0.0f, -1.0f, 0.0f, // v0 (front-left from below)
                 0.5f, -0.5f, -0.5f,     1.0f, 0.0f,     0.0f, -1.0f, 0.0f, // v1 (front-right from below)
                 0.5f, -0.5f,  0.5f,     1.0f, 1.0f,     0.0f, -1.0f, 0.0f, // v2 (back-right from below)
                -0.5f, -0.5f,  0.5f,     0.0f, 1.0f,     0.0f, -1.0f, 0.0f  // v3 (back-left from below)
        };
        // For downward face, to be CCW from outside (below), order v0,v3,v2 and v0,v2,v1
        // Or, use standard quad indices and ensure vertices are ordered for CCW when viewed from outside.
        // Current: v0(-x,-y,-z), v1(+x,-y,-z), v2(+x,-y,+z), v3(-x,-y,+z)
        // Tri1: (-x,-y,-z)-(+x,-y,-z)-(+x,-y,+z). Cross((1,0,0),(1,0,1)) -> (0,-1,0) -> CW from outside.
        // Need to flip indices or vertex order for bottom face if GL_CULL_FACE GL_BACK is on.
        // If vertices are defined v0, v1, v2, v3:
        // v0(-0.5f, -0.5f,  0.5f) UV(0,0) // Front-Left (when looking towards -Z on the bottom face)
        // v1( 0.5f, -0.5f,  0.5f) UV(1,0) // Front-Right
        // v2( 0.5f, -0.5f, -0.5f) UV(1,1) // Back-Right
        // v3(-0.5f, -0.5f, -0.5f) UV(0,1) // Back-Left
        // This makes it CCW when viewed from below.
        float[] downVertices = {
                -0.5f, -0.5f,  0.5f,     0.0f, 0.0f,      0.0f, -1.0f, 0.0f,
                 0.5f, -0.5f,  0.5f,     1.0f, 0.0f,      0.0f, -1.0f, 0.0f,
                 0.5f, -0.5f, -0.5f,     1.0f, 1.0f,      0.0f, -1.0f, 0.0f,
                -0.5f, -0.5f, -0.5f,     0.0f, 1.0f,      0.0f, -1.0f, 0.0f
        };
        return createSingleFace(downVertices, QUAD_INDICES);
    }

    public static Mesh createNorthFace() { // Z-
        float[] vertices = {
                // Positions             // TexCoords    // Normals (0,0,-1)
                 0.5f, -0.5f, -0.5f,     0.0f, 1.0f,     0.0f, 0.0f, -1.0f, // v0 (bottom-right on face) - V flipped
                -0.5f, -0.5f, -0.5f,     1.0f, 1.0f,     0.0f, 0.0f, -1.0f, // v1 (bottom-left on face)  - V flipped
                -0.5f,  0.5f, -0.5f,     1.0f, 0.0f,     0.0f, 0.0f, -1.0f, // v2 (top-left on face)     - V flipped
                 0.5f,  0.5f, -0.5f,     0.0f, 0.0f,     0.0f, 0.0f, -1.0f  // v3 (top-right on face)    - V flipped
        };
        return createSingleFace(vertices, QUAD_INDICES);
    }

    public static Mesh createSouthFace() { // Z+
        float[] vertices = {
                // Positions             // TexCoords    // Normals (0,0,1)
                -0.5f, -0.5f,  0.5f,     0.0f, 1.0f,     0.0f, 0.0f, 1.0f,  // v0 (bottom-left on face) - V flipped
                 0.5f, -0.5f,  0.5f,     1.0f, 1.0f,     0.0f, 0.0f, 1.0f,  // v1 (bottom-right on face) - V flipped
                 0.5f,  0.5f,  0.5f,     1.0f, 0.0f,     0.0f, 0.0f, 1.0f,  // v2 (top-right on face)    - V flipped
                -0.5f,  0.5f,  0.5f,     0.0f, 0.0f,     0.0f, 0.0f, 1.0f   // v3 (top-left on face)     - V flipped
        };
        return createSingleFace(vertices, QUAD_INDICES);
    }

    public static Mesh createWestFace() { // X-
        float[] vertices = {
                // Positions             // TexCoords    // Normals (-1,0,0)
                -0.5f, -0.5f, -0.5f,     0.0f, 1.0f,    -1.0f, 0.0f, 0.0f,  // v0 (bottom-right on face, view from -X) - V flipped
                -0.5f, -0.5f,  0.5f,     1.0f, 1.0f,    -1.0f, 0.0f, 0.0f,  // v1 (bottom-left on face) - V flipped
                -0.5f,  0.5f,  0.5f,     1.0f, 0.0f,    -1.0f, 0.0f, 0.0f,  // v2 (top-left on face)    - V flipped
                -0.5f,  0.5f, -0.5f,     0.0f, 0.0f,    -1.0f, 0.0f, 0.0f   // v3 (top-right on face)   - V flipped
        };
        return createSingleFace(vertices, QUAD_INDICES);
    }

    public static Mesh createEastFace() { // X+
        float[] vertices = {
                // Positions             // TexCoords    // Normals (1,0,0)
                 0.5f, -0.5f,  0.5f,     0.0f, 1.0f,     1.0f, 0.0f, 0.0f,  // v0 (bottom-left on face, view from +X) - V flipped
                 0.5f, -0.5f, -0.5f,     1.0f, 1.0f,     1.0f, 0.0f, 0.0f,  // v1 (bottom-right on face) - V flipped
                 0.5f,  0.5f, -0.5f,     1.0f, 0.0f,     1.0f, 0.0f, 0.0f,  // v2 (top-right on face)    - V flipped
                 0.5f,  0.5f,  0.5f,     0.0f, 0.0f,     1.0f, 0.0f, 0.0f   // v3 (top-left on face)     - V flipped
        };
        return createSingleFace(vertices, QUAD_INDICES);
    }

    /**
     * Convenience method to create a unit cube centered at origin with texture coordinates.
     */
    public static Mesh createCube() {
        // Define vertices with position (3), texture coords (2), and normals (3)
        float[] vertices = {
                // Position           // TexCoords    // Normals
                // Back face
                -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,    0.0f,  0.0f, -1.0f,
                 0.5f, -0.5f, -0.5f,  1.0f, 0.0f,    0.0f,  0.0f, -1.0f,
                 0.5f,  0.5f, -0.5f,  1.0f, 1.0f,    0.0f,  0.0f, -1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,    0.0f,  0.0f, -1.0f,

                // Front face
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,    0.0f,  0.0f,  1.0f,
                 0.5f, -0.5f,  0.5f,  1.0f, 0.0f,    0.0f,  0.0f,  1.0f,
                 0.5f,  0.5f,  0.5f,  1.0f, 1.0f,    0.0f,  0.0f,  1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,    0.0f,  0.0f,  1.0f,

                // Left face
                -0.5f,  0.5f,  0.5f,  1.0f, 0.0f,   -1.0f,  0.0f,  0.0f,
                -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,   -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,   -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,   -1.0f,  0.0f,  0.0f,

                // Right face
                 0.5f,  0.5f,  0.5f,  1.0f, 0.0f,    1.0f,  0.0f,  0.0f,
                 0.5f,  0.5f, -0.5f,  1.0f, 1.0f,    1.0f,  0.0f,  0.0f,
                 0.5f, -0.5f, -0.5f,  0.0f, 1.0f,    1.0f,  0.0f,  0.0f,
                 0.5f, -0.5f,  0.5f,  0.0f, 0.0f,    1.0f,  0.0f,  0.0f,

                // Bottom face
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,    0.0f, -1.0f,  0.0f,
                 0.5f, -0.5f, -0.5f,  1.0f, 1.0f,    0.0f, -1.0f,  0.0f,
                 0.5f, -0.5f,  0.5f,  1.0f, 0.0f,    0.0f, -1.0f,  0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,    0.0f, -1.0f,  0.0f,

                // Top face
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,    0.0f,  1.0f,  0.0f,
                 0.5f,  0.5f, -0.5f,  1.0f, 1.0f,    0.0f,  1.0f,  0.0f,
                 0.5f,  0.5f,  0.5f,  1.0f, 0.0f,    0.0f,  1.0f,  0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 0.0f,    0.0f,  1.0f,  0.0f
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
