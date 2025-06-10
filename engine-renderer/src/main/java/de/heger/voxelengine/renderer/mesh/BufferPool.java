package de.heger.voxelengine.renderer.mesh;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe pool for direct NIO buffers (FloatBuffer, IntBuffer) to reduce
 * allocation overhead and GC pressure during mesh building.
 * This class is implemented as a singleton.
 */
public final class BufferPool {

    private static final BufferPool INSTANCE = new BufferPool();

    // Buffer sizes in elements (floats or ints)
    // Small: ~128 faces of vertex data, ~1024 faces of index data
    private static final int SMALL_VERTICES_CAPACITY = 4096; // 16 KB
    private static final int SMALL_INDICES_CAPACITY = 6144;  // 24 KB

    // Medium: ~512 faces of vertex data, ~4096 faces of index data
    private static final int MEDIUM_VERTICES_CAPACITY = 16384; // 64 KB
    private static final int MEDIUM_INDICES_CAPACITY = 24576;  // 96 KB

    // Large: ~2048 faces of vertex data, ~16384 faces of index data
    private static final int LARGE_VERTICES_CAPACITY = 65536; // 256 KB
    private static final int LARGE_INDICES_CAPACITY = 98304;  // 384 KB

    private final Queue<FloatBuffer> smallFloatBufferPool = new ConcurrentLinkedQueue<>();
    private final Queue<FloatBuffer> mediumFloatBufferPool = new ConcurrentLinkedQueue<>();
    private final Queue<FloatBuffer> largeFloatBufferPool = new ConcurrentLinkedQueue<>();

    private final Queue<IntBuffer> smallIntBufferPool = new ConcurrentLinkedQueue<>();
    private final Queue<IntBuffer> mediumIntBufferPool = new ConcurrentLinkedQueue<>();
    private final Queue<IntBuffer> largeIntBufferPool = new ConcurrentLinkedQueue<>();
    
    // --- Statistics for monitoring ---
    private final AtomicLong floatBorrows = new AtomicLong(0);
    private final AtomicLong floatReleases = new AtomicLong(0);
    private final AtomicLong floatCreations = new AtomicLong(0);
    private final AtomicLong floatPoolMisses = new AtomicLong(0);
    
    private final AtomicLong intBorrows = new AtomicLong(0);
    private final AtomicLong intReleases = new AtomicLong(0);
    private final AtomicLong intCreations = new AtomicLong(0);
    private final AtomicLong intPoolMisses = new AtomicLong(0);


    private BufferPool() {
        // Private constructor for singleton
    }

    public static BufferPool getInstance() {
        return INSTANCE;
    }

    public FloatBuffer borrowFloatBuffer(int minCapacity) {
        floatBorrows.incrementAndGet();
        Queue<FloatBuffer> pool;
        int capacityToCreate;

        if (minCapacity <= SMALL_VERTICES_CAPACITY) {
            pool = smallFloatBufferPool;
            capacityToCreate = SMALL_VERTICES_CAPACITY;
        } else if (minCapacity <= MEDIUM_VERTICES_CAPACITY) {
            pool = mediumFloatBufferPool;
            capacityToCreate = MEDIUM_VERTICES_CAPACITY;
        } else if (minCapacity <= LARGE_VERTICES_CAPACITY) {
            pool = largeFloatBufferPool;
            capacityToCreate = LARGE_VERTICES_CAPACITY;
        } else {
            // Requested buffer is larger than our largest pool category.
            // Create a new buffer on the fly. It will not be pooled upon release.
            floatCreations.incrementAndGet();
            return BufferUtils.createFloatBuffer(minCapacity);
        }

        FloatBuffer buffer = pool.poll();
        if (buffer != null) {
            return buffer.clear();
        } else {
            floatPoolMisses.incrementAndGet();
            floatCreations.incrementAndGet();
            return BufferUtils.createFloatBuffer(capacityToCreate);
        }
    }

    public void releaseFloatBuffer(FloatBuffer buffer) {
        floatReleases.incrementAndGet();
        buffer.clear();
        int capacity = buffer.capacity();

        if (capacity == SMALL_VERTICES_CAPACITY) {
            smallFloatBufferPool.offer(buffer);
        } else if (capacity == MEDIUM_VERTICES_CAPACITY) {
            mediumFloatBufferPool.offer(buffer);
        } else if (capacity == LARGE_VERTICES_CAPACITY) {
            largeFloatBufferPool.offer(buffer);
        }
        // Buffers with non-standard sizes are not pooled and will be garbage collected.
    }

    public IntBuffer borrowIntBuffer(int minCapacity) {
        intBorrows.incrementAndGet();
        Queue<IntBuffer> pool;
        int capacityToCreate;

        if (minCapacity <= SMALL_INDICES_CAPACITY) {
            pool = smallIntBufferPool;
            capacityToCreate = SMALL_INDICES_CAPACITY;
        } else if (minCapacity <= MEDIUM_INDICES_CAPACITY) {
            pool = mediumIntBufferPool;
            capacityToCreate = MEDIUM_INDICES_CAPACITY;
        } else if (minCapacity <= LARGE_INDICES_CAPACITY) {
            pool = largeIntBufferPool;
            capacityToCreate = LARGE_INDICES_CAPACITY;
        } else {
            intCreations.incrementAndGet();
            return BufferUtils.createIntBuffer(minCapacity);
        }
        
        IntBuffer buffer = pool.poll();
        if (buffer != null) {
            return buffer.clear();
        } else {
            intPoolMisses.incrementAndGet();
            intCreations.incrementAndGet();
            return BufferUtils.createIntBuffer(capacityToCreate);
        }
    }

    public void releaseIntBuffer(IntBuffer buffer) {
        intReleases.incrementAndGet();
        buffer.clear();
        int capacity = buffer.capacity();

        if (capacity == SMALL_INDICES_CAPACITY) {
            smallIntBufferPool.offer(buffer);
        } else if (capacity == MEDIUM_INDICES_CAPACITY) {
            mediumIntBufferPool.offer(buffer);
        } else if (capacity == LARGE_INDICES_CAPACITY) {
            largeIntBufferPool.offer(buffer);
        }
    }
    
    public String getStats() {
        return String.format(
            "BufferPool Stats:\n" +
            "  Float - Borrows: %d, Releases: %d, Creations: %d, Misses: %d, InPool(S/M/L): %d/%d/%d\n" +
            "  Int   - Borrows: %d, Releases: %d, Creations: %d, Misses: %d, InPool(S/M/L): %d/%d/%d",
            floatBorrows.get(), floatReleases.get(), floatCreations.get(), floatPoolMisses.get(),
            smallFloatBufferPool.size(), mediumFloatBufferPool.size(), largeFloatBufferPool.size(),
            intBorrows.get(), intReleases.get(), intCreations.get(), intPoolMisses.get(),
            smallIntBufferPool.size(), mediumIntBufferPool.size(), largeIntBufferPool.size()
        );
    }
    
    public void resetStats() {
        floatBorrows.set(0);
        floatReleases.set(0);
        floatCreations.set(0);
        floatPoolMisses.set(0);
        intBorrows.set(0);
        intReleases.set(0);
        intCreations.set(0);
        intPoolMisses.set(0);
    }
} 