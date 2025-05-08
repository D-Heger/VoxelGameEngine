package de.heger.voxelengine.core.collections;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastUtilCollectionsTest {

    @Test
    void testNewIntList() {
        IntArrayList list = FastUtilCollections.newIntList();
        assertTrue(list.isEmpty());
        list.add(5);
        assertEquals(1, list.size());
        assertEquals(5, list.getInt(0));
    }

    @Test
    void testNewIntListWithSize() {
        IntArrayList list = FastUtilCollections.newIntList(10);
        assertEquals(0, list.size());
        list.add(3);
        assertEquals(1, list.size());
    }

    @Test
    void testNewLongList() {
        LongArrayList list = FastUtilCollections.newLongList();
        assertTrue(list.isEmpty());
        list.add(7L);
        assertEquals(1, list.size());
        assertEquals(7L, list.getLong(0));
    }

    @Test
    void testNewByteList() {
        ByteArrayList list = FastUtilCollections.newByteList();
        assertTrue(list.isEmpty());
        list.add((byte)9);
        assertEquals(1, list.size());
        assertEquals((byte)9, list.getByte(0));
    }

    @Test
    void testNewMaps() {
        Int2ObjectMap<String> intMap = FastUtilCollections.newInt2ObjectMap();
        intMap.put(1, "one");
        assertEquals("one", intMap.get(1));

        Long2ObjectMap<String> longMap = FastUtilCollections.newLong2ObjectMap();
        longMap.put(2L, "two");
        assertEquals("two", longMap.get(2L));

        Object2IntMap<String> objMap = FastUtilCollections.newObject2IntMap();
        objMap.put("three", 3);
        assertEquals(3, objMap.getInt("three"));
    }
}
