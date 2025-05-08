package de.heger.voxelengine.core.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec3iTest {
    @Test
    void testAddAndSub() {
        Vec3i a = new Vec3i(1, 2, 3);
        Vec3i b = new Vec3i(4, 5, 6);
        assertEquals(new Vec3i(5, 7, 9), a.add(b));
        assertEquals(new Vec3i(-3, -3, -3), a.sub(b));
    }

    @Test
    void testMulDiv() {
        Vec3i v = new Vec3i(2, -4, 6);
        assertEquals(new Vec3i(4, -8, 12), v.mul(2));
        assertEquals(new Vec3i(1, -2, 3), v.div(2));
    }

    @Test
    void testDistance() {
        Vec3i a = new Vec3i(0, 0, 0);
        Vec3i b = new Vec3i(3, 4, 12);
        assertEquals(9 + 16 + 144, a.distanceSquared(b));
        assertEquals(Math.sqrt(169), a.distance(b), 1e-9);
    }

    @Test
    void testEqualsHashCodeToString() {
        Vec3i v1 = new Vec3i(7, 8, 9);
        Vec3i v2 = new Vec3i(7, 8, 9);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertTrue(v1.toString().contains("7, 8, 9"));
    }
}
