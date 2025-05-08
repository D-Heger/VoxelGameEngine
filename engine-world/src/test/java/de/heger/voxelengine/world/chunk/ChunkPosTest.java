package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkPosTest {

    @Test
    void constructorAndGetters() {
        ChunkPos pos = new ChunkPos(1, -2, 3);
        assertEquals(1, pos.x);
        assertEquals(-2, pos.y);
        assertEquals(3, pos.z);
    }

    @Test
    void fromVec3i() {
        Vec3i vec = new Vec3i(5, 6, 7);
        ChunkPos pos = ChunkPos.fromVec3i(vec);
        assertEquals(5, pos.x);
        assertEquals(6, pos.y);
        assertEquals(7, pos.z);
    }

    @Test
    void toVec3i() {
        ChunkPos pos = new ChunkPos(-1, 0, 1);
        Vec3i vec = pos.toVec3i();
        assertEquals(-1, vec.x);
        assertEquals(0, vec.y);
        assertEquals(1, vec.z);
    }

    @Test
    void testEquals() {
        ChunkPos pos1 = new ChunkPos(1, 2, 3);
        ChunkPos pos2 = new ChunkPos(1, 2, 3);
        ChunkPos pos3 = new ChunkPos(4, 2, 3);
        ChunkPos pos4 = new ChunkPos(1, 4, 3);
        ChunkPos pos5 = new ChunkPos(1, 2, 4);

        assertEquals(pos1, pos2);
        assertNotEquals(pos1, pos3);
        assertNotEquals(pos1, pos4);
        assertNotEquals(pos1, pos5);
        assertNotEquals(pos1, null);
        assertNotEquals(pos1, new Object());
    }

    @Test
    void testHashCode() {
        ChunkPos pos1 = new ChunkPos(1, 2, 3);
        ChunkPos pos2 = new ChunkPos(1, 2, 3);
        ChunkPos pos3 = new ChunkPos(10, -20, 30);
        ChunkPos pos4 = new ChunkPos(10, -20, 30);

        assertEquals(pos1.hashCode(), pos2.hashCode());
        assertEquals(pos3.hashCode(), pos4.hashCode());
        // It's highly likely, but not strictly guaranteed, that different positions have different hash codes
        // assertNotEquals(pos1.hashCode(), pos3.hashCode());
    }

    @Test
    void testToString() {
        ChunkPos pos = new ChunkPos(-5, 0, 10);
        assertEquals("ChunkPos(-5, 0, 10)", pos.toString());
    }
}
