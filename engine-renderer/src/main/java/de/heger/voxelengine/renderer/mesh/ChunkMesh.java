package de.heger.voxelengine.renderer.mesh;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL11.glDrawElements;

/**
 * Represents the renderable geometry for a single chunk.
 * It encapsulates a VAO, VBO, and EBO for all visible faces within that chunk.
 * 
 * DIRECT MEMORY OPTIMIZATIONS:
 * - Uses direct ByteBuffers (via BufferUtils.createFloatBuffer/createIntBuffer)
 * for zero-copy OpenGL uploads
 * - Accepts pre-flipped buffers to avoid unnecessary buffer operations
 * - Buffers are consumed directly by glBufferData() without intermediate array
 * copying
 * - Memory is allocated off-heap for better GC performance
 */
public class ChunkMesh {

    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int vertexCount;
    private final boolean isEmpty;

    /**
     * Creates a new ChunkMesh from the given vertex and index data.
     * Vertex data is expected to be interleaved: 3 position floats, 2 UV floats, 3
     * normal floats.
     * 
     * Note: The buffers should be positioned at the start of the data to upload and
     * have their
     * limit set to the end of the data. The buffers will be consumed (position will
     * advance).
     *
     * @param vertexBuffer The vertex data as a FloatBuffer. If null or empty, the
     *                     mesh will be marked as empty.
     * @param indexBuffer  The index data as an IntBuffer. If null or empty (and
     *                     vertices is not), the mesh will be marked as empty.
     */
    public ChunkMesh(FloatBuffer vertexBuffer, IntBuffer indexBuffer) {
        if (vertexBuffer == null || !vertexBuffer.hasRemaining() || indexBuffer == null
                || !indexBuffer.hasRemaining()) {
            this.isEmpty = true;
            this.vaoId = 0;
            this.vboId = 0;
            this.eboId = 0;
            this.vertexCount = 0;
            return;
        }

        this.isEmpty = false;
        this.vertexCount = indexBuffer.remaining();

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // VBO for vertex data
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // Define Vertex Attributes (3 pos, 2 tex, 3 normal)
        int stride = (3 + 2 + 3) * Float.BYTES;

        // Position attribute (location = 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);

        // Texture coordinate attribute (location = 1)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);

        // Normal attribute (location = 2)
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, (3 + 2) * Float.BYTES);

        // EBO for indices
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

        // Unbind VAO (and VBO/EBO implicitly by binding 0)
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Renders the chunk mesh.
     * If the mesh is empty, this method does nothing.
     * Assumes the correct VAO has already been bound by the caller.
     */
    public void render() {
        if (isEmpty) {
            return;
        }
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
    }

    /**
     * Cleans up the OpenGL resources (VAO, VBO, EBO) allocated by this mesh.
     * If the mesh was empty, this method does nothing.
     */
    public void cleanup() {
        if (isEmpty) {
            return;
        }
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vboId);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDeleteBuffers(eboId);

        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }

    /**
     * Checks if this mesh contains any geometry to render.
     *
     * @return true if the mesh is empty, false otherwise.
     */
    public boolean isEmpty() {
        return isEmpty;
    }

    /**
     * Gets the OpenGL handle for the Vertex Array Object.
     *
     * @return The VAO ID.
     */
    public int getVaoId() {
        return vaoId;
    }

    /**
     * Gets the number of indices in this mesh (used for glDrawElements count).
     * 
     * @return The number of indices.
     */
    public int getIndexCount() {
        return vertexCount;
    }
}