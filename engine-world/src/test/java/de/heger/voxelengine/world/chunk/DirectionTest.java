package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DirectionTest {

    @Test
    void getOffset() {
        assertEquals(new Vec3i(0, -1, 0), Direction.DOWN.getOffset());
        assertEquals(new Vec3i(0, 1, 0), Direction.UP.getOffset());
        assertEquals(new Vec3i(0, 0, -1), Direction.NORTH.getOffset());
        assertEquals(new Vec3i(0, 0, 1), Direction.SOUTH.getOffset());
        assertEquals(new Vec3i(-1, 0, 0), Direction.WEST.getOffset());
        assertEquals(new Vec3i(1, 0, 0), Direction.EAST.getOffset());
    }

    @Test
    void getOpposite() {
        assertEquals(Direction.UP, Direction.DOWN.getOpposite());
        assertEquals(Direction.DOWN, Direction.UP.getOpposite());
        assertEquals(Direction.SOUTH, Direction.NORTH.getOpposite());
        assertEquals(Direction.NORTH, Direction.SOUTH.getOpposite());
        assertEquals(Direction.EAST, Direction.WEST.getOpposite());
        assertEquals(Direction.WEST, Direction.EAST.getOpposite());
    }

    @ParameterizedTest
    @CsvSource({
            "0, DOWN",
            "1, UP",
            "2, NORTH",
            "3, SOUTH",
            "4, WEST",
            "5, EAST"
    })
    void fromIndex(int index, Direction expectedDirection) {
        assertEquals(expectedDirection, Direction.fromIndex(index));
        assertEquals(index, expectedDirection.getIndex());
    }

    @Test
    void fromIndex_Invalid() {
        assertThrows(IllegalArgumentException.class, () -> Direction.fromIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> Direction.fromIndex(6));
    }

    @Test
    void getIndex() {
        assertEquals(0, Direction.DOWN.getIndex());
        assertEquals(1, Direction.UP.getIndex());
        assertEquals(2, Direction.NORTH.getIndex());
        assertEquals(3, Direction.SOUTH.getIndex());
        assertEquals(4, Direction.WEST.getIndex());
        assertEquals(5, Direction.EAST.getIndex());
    }
}
