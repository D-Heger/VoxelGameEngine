package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3f;
import de.heger.voxelengine.core.math.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateUtilsTest {

    // --- World (int) to Chunk/Local ---

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0,       0, 0, 0",
            "15, 15, 15,     0, 0, 0",
            "16, 0, 0,       1, 0, 0",
            "0, 16, 0,       0, 1, 0",
            "0, 0, 16,       0, 0, 1",
            "31, 31, 31,     1, 1, 1",
            "32, 32, 32,     2, 2, 2",
            "-1, 0, 0,      -1, 0, 0", // Negative coordinates
            "-16, 0, 0,     -1, 0, 0",
            "-17, 0, 0,     -2, 0, 0",
            "-1, -1, -1,   -1, -1, -1",
            "-16, -16, -16, -1, -1, -1",
            "-17, -17, -17, -2, -2, -2"
    })
    void worldToChunkCoords_int(int worldX, int worldY, int worldZ, int expectedChunkX, int expectedChunkY, int expectedChunkZ) {
        Vec3i worldPos = new Vec3i(worldX, worldY, worldZ);
        ChunkPos expectedChunkPos = new ChunkPos(expectedChunkX, expectedChunkY, expectedChunkZ);
        assertEquals(expectedChunkPos, CoordinateUtils.worldToChunkCoords(worldPos));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0,       0, 0, 0",
            "15, 15, 15,     15, 15, 15",
            "16, 0, 0,       0, 0, 0",
            "0, 16, 0,       0, 0, 0",
            "0, 0, 16,       0, 0, 0",
            "17, 1, 1,       1, 1, 1",
            "31, 31, 31,     15, 15, 15",
            "32, 32, 32,     0, 0, 0",
            "-1, 0, 0,       15, 0, 0", // Negative coordinates
            "-16, 0, 0,      0, 0, 0",
            "-17, 0, 0,      15, 0, 0",
            "-1, -1, -1,    15, 15, 15",
            "-16, -16, -16,  0, 0, 0",
            "-17, -17, -17, 15, 15, 15"
    })
    void worldToLocalCoords_int(int worldX, int worldY, int worldZ, int expectedLocalX, int expectedLocalY, int expectedLocalZ) {
        Vec3i worldPos = new Vec3i(worldX, worldY, worldZ);
        Vec3i expectedLocalPos = new Vec3i(expectedLocalX, expectedLocalY, expectedLocalZ);
        assertEquals(expectedLocalPos, CoordinateUtils.worldToLocalCoords(worldPos));
    }

    // --- World (float) to Chunk/Local ---

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0, 0.0,         0, 0, 0",
            "15.9, 15.9, 15.9,       0, 0, 0",
            "16.0, 0.0, 0.0,         1, 0, 0",
            "-0.1, 0.0, 0.0,        -1, 0, 0", // Negative coordinates
            "-15.9, 0.0, 0.0,       -1, 0, 0",
            "-16.0, 0.0, 0.0,       -1, 0, 0",
            "-16.1, 0.0, 0.0,       -2, 0, 0"
    })
    void worldToChunkCoords_float(float worldX, float worldY, float worldZ, int expectedChunkX, int expectedChunkY, int expectedChunkZ) {
        Vec3f worldPos = new Vec3f(worldX, worldY, worldZ);
        ChunkPos expectedChunkPos = new ChunkPos(expectedChunkX, expectedChunkY, expectedChunkZ);
        assertEquals(expectedChunkPos, CoordinateUtils.worldToChunkCoords(worldPos));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0, 0.0,         0, 0, 0",
            "15.9, 15.9, 15.9,       15, 15, 15",
            "16.0, 0.0, 0.0,         0, 0, 0",
            "16.1, 0.1, 0.1,         0, 0, 0", // world block is 16,0,0 -> local 0,0,0
            "-0.1, 0.0, 0.0,        15, 0, 0", // world block is -1, local is 15
            "-15.9, 0.0, 0.0,       0, 0, 0", // world block is -16, local is 0
            "-16.0, 0.0, 0.0,       0, 0, 0", // world block is -16, local is 0
            "-16.1, 0.0, 0.0,       15, 0, 0"  // world block is -17, local is 15
    })
    void worldToLocalCoords_float(float worldX, float worldY, float worldZ, int expectedLocalX, int expectedLocalY, int expectedLocalZ) {
        Vec3f worldPos = new Vec3f(worldX, worldY, worldZ);
        Vec3i expectedLocalPos = new Vec3i(expectedLocalX, expectedLocalY, expectedLocalZ);
        assertEquals(expectedLocalPos, CoordinateUtils.worldToLocalCoords(worldPos));
    }

    // --- Chunk/Local to World ---

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0,    0, 0, 0,      0, 0, 0",
            "0, 0, 0,    15, 15, 15,   15, 15, 15",
            "1, 0, 0,    0, 0, 0,      16, 0, 0",
            "1, 1, 1,    1, 2, 3,      17, 18, 19",
            "-1, 0, 0,   0, 0, 0,     -16, 0, 0",
            "-1, 0, 0,   15, 0, 0,    -1, 0, 0",
            "-2, -1, -1, 15, 15, 15,  -17, -1, -1"
    })
    void localToWorldCoords(int chunkX, int chunkY, int chunkZ, int localX, int localY, int localZ, int expectedWorldX, int expectedWorldY, int expectedWorldZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkY, chunkZ);
        Vec3i localPos = new Vec3i(localX, localY, localZ);
        Vec3i expectedWorldPos = new Vec3i(expectedWorldX, expectedWorldY, expectedWorldZ);
        assertEquals(expectedWorldPos, CoordinateUtils.localToWorldCoords(chunkPos, localPos));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0,      0, 0, 0",
            "1, 0, 0,      16, 0, 0",
            "0, 1, 0,      0, 16, 0",
            "0, 0, 1,      0, 0, 16",
            "1, 2, 3,      16, 32, 48",
            "-1, 0, 0,     -16, 0, 0",
            "-1, -2, -3,   -16, -32, -48"
    })
    void chunkOriginToWorldCoords(int chunkX, int chunkY, int chunkZ, int expectedWorldX, int expectedWorldY, int expectedWorldZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkY, chunkZ);
        Vec3i expectedWorldPos = new Vec3i(expectedWorldX, expectedWorldY, expectedWorldZ);
        assertEquals(expectedWorldPos, CoordinateUtils.chunkOriginToWorldCoords(chunkPos));
    }

    // --- Indexing ---

    @Test
    void localCoordsToIndexAndBack() {
        for (int y = 0; y < CoordinateUtils.CHUNK_SIZE_Y; y++) {
            for (int z = 0; z < CoordinateUtils.CHUNK_SIZE_Z; z++) {
                for (int x = 0; x < CoordinateUtils.CHUNK_SIZE_X; x++) {
                    Vec3i localPos = new Vec3i(x, y, z);
                    int index = CoordinateUtils.localCoordsToIndex(localPos);
                    assertTrue(index >= 0 && index < (16 * 16 * 16), "Index out of bounds: " + index);
                    Vec3i convertedBackPos = CoordinateUtils.indexToLocalCoords(index);
                    assertEquals(localPos, convertedBackPos, "Mismatch for index " + index);
                }
            }
        }
    }

    @Test
    void localCoordsToIndex_SpecificValues() {
        // y * (W*D) + z * W + x = y * 256 + z * 16 + x
        assertEquals(0, CoordinateUtils.localCoordsToIndex(0, 0, 0));       // 0*256 + 0*16 + 0
        assertEquals(15, CoordinateUtils.localCoordsToIndex(15, 0, 0));      // 0*256 + 0*16 + 15
        assertEquals(16, CoordinateUtils.localCoordsToIndex(0, 0, 1));       // 0*256 + 1*16 + 0
        assertEquals(255, CoordinateUtils.localCoordsToIndex(15, 0, 15));     // 0*256 + 15*16 + 15
        assertEquals(256, CoordinateUtils.localCoordsToIndex(0, 1, 0));       // 1*256 + 0*16 + 0
        assertEquals(4095, CoordinateUtils.localCoordsToIndex(15, 15, 15));   // 15*256 + 15*16 + 15
    }

    @Test
    void indexToLocalCoords_SpecificValues() {
        assertEquals(new Vec3i(0, 0, 0), CoordinateUtils.indexToLocalCoords(0));
        assertEquals(new Vec3i(15, 0, 0), CoordinateUtils.indexToLocalCoords(15));
        assertEquals(new Vec3i(0, 0, 1), CoordinateUtils.indexToLocalCoords(16));
        assertEquals(new Vec3i(15, 0, 15), CoordinateUtils.indexToLocalCoords(255));
        assertEquals(new Vec3i(0, 1, 0), CoordinateUtils.indexToLocalCoords(256));
        assertEquals(new Vec3i(15, 15, 15), CoordinateUtils.indexToLocalCoords(4095));
    }

    @Test
    void localCoordsToIndex_OutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.localCoordsToIndex(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.localCoordsToIndex(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.localCoordsToIndex(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.localCoordsToIndex(0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.localCoordsToIndex(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.localCoordsToIndex(0, 0, 16));
    }

    @Test
    void indexToLocalCoords_OutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.indexToLocalCoords(-1));
        assertThrows(IllegalArgumentException.class, () -> CoordinateUtils.indexToLocalCoords(4096));
    }
}
