package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.world.block.BlockRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ChunkTest {

    private ChunkPos testPos;
    private Chunk chunk;
    @BeforeEach
    void setUp() {
        testPos = new ChunkPos(1, 2, 3);
        chunk = new Chunk(testPos);
        BlockRegistry.getInstance();
    }

    @Test
    void constructorInitializesCorrectly() {
        assertEquals(testPos, chunk.getPosition());
        assertEquals(ChunkState.EMPTY, chunk.getState());
        // Check if all blocks are initialized to AIR (assuming 0)
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    assertEquals(BlockRegistry.AIR.getId(), chunk.getBlock(x, y, z),
                            "Block at (" + x + "," + y + "," + z + ") should be air");
                }
            }
        }
        // Check neighbors are null
        for (Direction dir : Direction.values()) {
            assertNull(chunk.getNeighbor(dir));
        }
    }

    @Test
    void getSetBlock() {
        Vec3i localPos = new Vec3i(5, 10, 15);
        short blockId = (short) 42;

        // Initial state
        assertEquals(BlockRegistry.AIR.getId(), chunk.getBlock(localPos));

        // Set block
        chunk.setBlock(localPos, blockId);
        assertEquals(blockId, chunk.getBlock(localPos));
        assertEquals(blockId, chunk.getBlock(5, 10, 15));

        // Set back to air
        chunk.setBlock(5, 10, 15, BlockRegistry.AIR.getId());
        assertEquals(BlockRegistry.AIR.getId(), chunk.getBlock(localPos));
    }

    @Test
    void getSetBlock_OutOfBounds() {
        short blockId = (short) 1;
        // Test various out-of-bounds scenarios
        assertThrows(IllegalArgumentException.class, () -> chunk.getBlock(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> chunk.getBlock(Chunk.SIZE_X, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> chunk.getBlock(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> chunk.getBlock(0, Chunk.SIZE_Y, 0));
        assertThrows(IllegalArgumentException.class, () -> chunk.getBlock(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> chunk.getBlock(0, 0, Chunk.SIZE_Z));

        assertThrows(IllegalArgumentException.class, () -> chunk.setBlock(-1, 0, 0, blockId));
        assertThrows(IllegalArgumentException.class, () -> chunk.setBlock(Chunk.SIZE_X, 0, 0, blockId));
        assertThrows(IllegalArgumentException.class, () -> chunk.setBlock(0, -1, 0, blockId));
        assertThrows(IllegalArgumentException.class, () -> chunk.setBlock(0, Chunk.SIZE_Y, 0, blockId));
        assertThrows(IllegalArgumentException.class, () -> chunk.setBlock(0, 0, -1, blockId));
        assertThrows(IllegalArgumentException.class, () -> chunk.setBlock(0, 0, Chunk.SIZE_Z, blockId));

        assertThrows(NullPointerException.class, () -> chunk.getBlock((Vec3i) null));
        assertThrows(NullPointerException.class, () -> chunk.setBlock((Vec3i) null, blockId));
    }

    @Test
    void getSetState() {
        assertEquals(ChunkState.EMPTY, chunk.getState());
        chunk.setState(ChunkState.GENERATED);
        assertEquals(ChunkState.GENERATED, chunk.getState());
        chunk.setState(ChunkState.MESHED);
        assertEquals(ChunkState.MESHED, chunk.getState());
        assertThrows(NullPointerException.class, () -> chunk.setState(null));
    }

    @Test
    void getSetNeighbor() {
        Chunk neighborNorth = new Chunk(new ChunkPos(1, 2, 2)); // Z-1
        Chunk neighborEast = new Chunk(new ChunkPos(2, 2, 3)); // X+1

        assertNull(chunk.getNeighbor(Direction.NORTH));
        chunk.setNeighbor(Direction.NORTH, neighborNorth);
        assertEquals(neighborNorth, chunk.getNeighbor(Direction.NORTH));

        assertNull(chunk.getNeighbor(Direction.EAST));
        chunk.setNeighbor(Direction.EAST, neighborEast);
        assertEquals(neighborEast, chunk.getNeighbor(Direction.EAST));

        // Set back to null
        chunk.setNeighbor(Direction.NORTH, null);
        assertNull(chunk.getNeighbor(Direction.NORTH));

        assertThrows(NullPointerException.class, () -> chunk.getNeighbor(null));
        assertThrows(NullPointerException.class, () -> chunk.setNeighbor(null, neighborEast));
    }

    @Test
    void serialization() throws IOException {
        // Modify some blocks
        chunk.setBlock(0, 0, 0, (short) 1);
        chunk.setBlock(15, 15, 15, (short) 2);
        chunk.setBlock(5, 6, 7, (short) 3);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Write
        chunk.writeTo(dos);
        dos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);

        // Read
        Chunk deserializedChunk = Chunk.readFrom(dis);
        dis.close();

        // Verify
        assertNotNull(deserializedChunk);
        assertEquals(chunk.getPosition(), deserializedChunk.getPosition());
        // Deserialized chunks are assumed GENERATED for now
        assertEquals(ChunkState.GENERATED, deserializedChunk.getState());

        // Check block data matches
        assertEquals((short) 1, deserializedChunk.getBlock(0, 0, 0));
        assertEquals((short) 2, deserializedChunk.getBlock(15, 15, 15));
        assertEquals((short) 3, deserializedChunk.getBlock(5, 6, 7));
        // Check an unmodified block
        assertEquals(BlockRegistry.AIR.getId(), deserializedChunk.getBlock(1, 1, 1));
    }

    @Test
    void serialization_WrongVersion() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(999); // Write wrong version
        dos.writeInt(testPos.x);
        dos.writeInt(testPos.y);
        dos.writeInt(testPos.z);
        for (int i = 0; i < Chunk.TOTAL_BLOCKS; i++) {
            dos.writeShort(0);
        }
        dos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);

        IOException exception = assertThrows(IOException.class, () -> Chunk.readFrom(dis));
        assertTrue(exception.getMessage().contains("Unsupported chunk serialization version"));
        dis.close();
    }
}
