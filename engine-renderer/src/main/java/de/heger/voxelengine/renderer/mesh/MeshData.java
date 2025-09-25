package de.heger.voxelengine.renderer.mesh;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A helper class to store vertex and index data for a part of a chunk mesh,
 * typically grouped by texture. This class is mutable for performance reasons
 * when building meshes and uses direct ByteBuffers for optimal OpenGL performance.
 * 
 * DIRECT MEMORY OPTIMIZATIONS:
 * - Uses direct FloatBuffer/IntBuffer allocated off-heap via BufferUtils
 * - Supports bulk put() operations for better performance than individual element adds
 * - Implements efficient buffer resizing with minimal copying
 * - Returns duplicate buffers to avoid affecting the original write buffers
 * - Thread-safe buffer operations for concurrent mesh building
 */
public class MeshData {
    private FloatBuffer vertexBuffer;
    private IntBuffer indexBuffer;
    int currentIndexOffset; // Tracks the number of vertices added to this specific mesh part for index calculation

    private static final int INITIAL_VERTEX_CAPACITY = 4096; // Corresponds to BufferPool's SMALL_VERTICES_CAPACITY
    private static final int INITIAL_INDEX_CAPACITY = 6144;  // Corresponds to BufferPool's SMALL_INDICES_CAPACITY

    private static final BufferPool bufferPool = BufferPool.getInstance();

    public MeshData() {
        this.vertexBuffer = bufferPool.borrowFloatBuffer(INITIAL_VERTEX_CAPACITY);
        this.indexBuffer = bufferPool.borrowIntBuffer(INITIAL_INDEX_CAPACITY);
        this.currentIndexOffset = 0;
    }

    /**
     * Adds vertex data in bulk for better performance.
     * @param vertices Array of vertex data to add
     * @param offset Starting offset in the array
     * @param length Number of floats to add
     */
    public void addVertices(float[] vertices, int offset, int length) {
        ensureVertexCapacity(length);
        vertexBuffer.put(vertices, offset, length);
    }

    /**
     * Adds vertex data in bulk for better performance.
     * @param vertices Array of vertex data to add
     */
    public void addVertices(float[] vertices) {
        addVertices(vertices, 0, vertices.length);
    }

    /**
     * Adds a single vertex value (for compatibility with existing code).
     * For better performance, use addVertices() for bulk operations.
     */
    public void addVertex(float value) {
        ensureVertexCapacity(1);
        vertexBuffer.put(value);
    }

    /**
     * Adds index data in bulk for better performance.
     * @param indices Array of index data to add
     * @param offset Starting offset in the array
     * @param length Number of ints to add
     */
    public void addIndices(int[] indices, int offset, int length) {
        ensureIndexCapacity(length);
        indexBuffer.put(indices, offset, length);
    }

    /**
     * Adds index data in bulk for better performance.
     * @param indices Array of index data to add
     */
    public void addIndices(int[] indices) {
        addIndices(indices, 0, indices.length);
    }

    /**
     * Adds a single index value (for compatibility with existing code).
     * For better performance, use addIndices() for bulk operations.
     */
    public void addIndex(int value) {
        ensureIndexCapacity(1);
        indexBuffer.put(value);
    }

    private void ensureVertexCapacity(int additionalElements) {
        if (vertexBuffer.remaining() < additionalElements) {
            int requiredCapacity = Math.max(vertexBuffer.capacity() * 2, vertexBuffer.position() + additionalElements);
            FloatBuffer newBuffer = bufferPool.borrowFloatBuffer(requiredCapacity);

            vertexBuffer.flip();
            newBuffer.put(vertexBuffer);
            
            bufferPool.releaseFloatBuffer(vertexBuffer);
            vertexBuffer = newBuffer;
        }
    }

    private void ensureIndexCapacity(int additionalElements) {
        if (indexBuffer.remaining() < additionalElements) {
            int requiredCapacity = Math.max(indexBuffer.capacity() * 2, indexBuffer.position() + additionalElements);
            IntBuffer newBuffer = bufferPool.borrowIntBuffer(requiredCapacity);
            
            indexBuffer.flip();
            newBuffer.put(indexBuffer);
            
            bufferPool.releaseIntBuffer(indexBuffer);
            indexBuffer = newBuffer;
        }
    }

    public void incrementVertexOffset(int count) {
        this.currentIndexOffset += count;
    }

    public boolean isEmpty() {
        return vertexBuffer.position() == 0 || indexBuffer.position() == 0;
    }

    /**
     * Returns a duplicate FloatBuffer ready for OpenGL consumption.
     * The returned buffer is flipped and ready to read, while keeping the original buffer intact.
     */
    public FloatBuffer getVertexBuffer() {
        FloatBuffer readBuffer = vertexBuffer.duplicate();
        readBuffer.flip();
        return readBuffer;
    }

    /**
     * Returns a duplicate IntBuffer ready for OpenGL consumption.
     * The returned buffer is flipped and ready to read, while keeping the original buffer intact.
     */
    public IntBuffer getIndexBuffer() {
        IntBuffer readBuffer = indexBuffer.duplicate();
        readBuffer.flip();
        return readBuffer;
    }

    /**
     * Releases the internal buffers back to the pool.
     * This must be called when the MeshData is no longer needed.
     */
    public void cleanup() {
        if (vertexBuffer != null) {
            bufferPool.releaseFloatBuffer(vertexBuffer);
            vertexBuffer = null;
        }
        if (indexBuffer != null) {
            bufferPool.releaseIntBuffer(indexBuffer);
            indexBuffer = null;
        }
    }
} 