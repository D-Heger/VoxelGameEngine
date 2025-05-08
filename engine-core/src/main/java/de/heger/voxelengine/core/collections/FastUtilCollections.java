package de.heger.voxelengine.core.collections;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * A static utility class providing convenient factory methods for commonly used
 * FastUtil primitive collections.
 */
public final class FastUtilCollections {

    // Private constructor to prevent instantiation
    private FastUtilCollections() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // --- List Factories ---

    /**
     * Creates a new empty IntArrayList.
     * @return A new IntArrayList.
     */
    public static IntArrayList newIntList() {
        return new IntArrayList();
    }

    /**
     * Creates a new IntArrayList with the specified expected size.
     * @param expectedSize The expected number of elements.
     * @return A new IntArrayList.
     */
    public static IntArrayList newIntList(int expectedSize) {
        return new IntArrayList(expectedSize);
    }

    /**
     * Creates a new empty LongArrayList.
     * @return A new LongArrayList.
     */
    public static LongArrayList newLongList() {
        return new LongArrayList();
    }

    /**
     * Creates a new LongArrayList with the specified expected size.
     * @param expectedSize The expected number of elements.
     * @return A new LongArrayList.
     */
    public static LongArrayList newLongList(int expectedSize) {
        return new LongArrayList(expectedSize);
    }

    /**
     * Creates a new empty ByteArrayList.
     * @return A new ByteArrayList.
     */
    public static ByteArrayList newByteList() {
        return new ByteArrayList();
    }

    /**
     * Creates a new ByteArrayList with the specified expected size.
     * @param expectedSize The expected number of elements.
     * @return A new ByteArrayList.
     */
    public static ByteArrayList newByteList(int expectedSize) {
        return new ByteArrayList(expectedSize);
    }

    // --- Map Factories ---

    /**
     * Creates a new empty Long2ObjectOpenHashMap.
     * @param <V> The type of the values.
     * @return A new Long2ObjectOpenHashMap.
     */
    public static <V> Long2ObjectMap<V> newLong2ObjectMap() {
        return new Long2ObjectOpenHashMap<>();
    }

    /**
     * Creates a new Long2ObjectOpenHashMap with the specified expected size.
     * @param <V> The type of the values.
     * @param expectedSize The expected number of entries.
     * @return A new Long2ObjectOpenHashMap.
     */
    public static <V> Long2ObjectMap<V> newLong2ObjectMap(int expectedSize) {
        return new Long2ObjectOpenHashMap<>(expectedSize);
    }

    /**
     * Creates a new empty Int2ObjectOpenHashMap.
     * @param <V> The type of the values.
     * @return A new Int2ObjectOpenHashMap.
     */
    public static <V> Int2ObjectMap<V> newInt2ObjectMap() {
        return new Int2ObjectOpenHashMap<>();
    }

    /**
     * Creates a new Int2ObjectOpenHashMap with the specified expected size.
     * @param <V> The type of the values.
     * @param expectedSize The expected number of entries.
     * @return A new Int2ObjectOpenHashMap.
     */
    public static <V> Int2ObjectMap<V> newInt2ObjectMap(int expectedSize) {
        return new Int2ObjectOpenHashMap<>(expectedSize);
    }

    /**
     * Creates a new empty Object2IntOpenHashMap.
     * @param <K> The type of the keys.
     * @return A new Object2IntOpenHashMap.
     */
    public static <K> Object2IntMap<K> newObject2IntMap() {
        return new Object2IntOpenHashMap<>();
    }

    /**
     * Creates a new Object2IntOpenHashMap with the specified expected size.
     * @param <K> The type of the keys.
     * @param expectedSize The expected number of entries.
     * @return A new Object2IntOpenHashMap.
     */
    public static <K> Object2IntMap<K> newObject2IntMap(int expectedSize) {
        return new Object2IntOpenHashMap<>(expectedSize);
    }
}