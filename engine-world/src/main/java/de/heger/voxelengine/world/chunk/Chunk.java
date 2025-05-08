package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.core.utils.Validate;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.block.BlockProperties; // Added import

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a 16x16x16 segment of the world containing block data.
 * Chunks are the fundamental unit for world storage, rendering, and simulation.
 * Block data is stored in a flat short array for efficiency.
 *
 * This class is intended to be thread-safe for block access after initial population,
 * but the population phase itself might require external synchronization.
 * Future additions will include state management and neighbor references.
 */
public class Chunk {

    // Serialization format version
    private static final int SERIALIZATION_VERSION = 1;

    public static final int SIZE_X = CoordinateUtils.CHUNK_SIZE_X;
    public static final int SIZE_Y = CoordinateUtils.CHUNK_SIZE_Y;
    public static final int SIZE_Z = CoordinateUtils.CHUNK_SIZE_Z;
    public static final int TOTAL_BLOCKS = SIZE_X * SIZE_Y * SIZE_Z; // 4096

    private final ChunkPos position;
    // Flat array storing block IDs (short allows 65536 block types)
    // Indexing: y * (SIZE_X * SIZE_Z) + z * SIZE_X + x
    private final short[] blockData;

    // Added for P3-T1.4
    private volatile ChunkState state;

    // Added for P3-T1.5: Neighbor references
    // Index corresponds to Direction.ordinal()
    private final Chunk[] neighbors = new Chunk[6]; // DOWN, UP, NORTH, SOUTH, WEST, EAST

    // Added for P3-T4.6: Reference to the BlockRegistry instance
    private final BlockRegistry blockRegistry = BlockRegistry.getInstance();

    /**
     * Creates a new, empty chunk at the specified position.
     * The block data array is initialized but not populated.
     *
     * @param position The position of this chunk in the chunk grid. Must not be null.
     */
    public Chunk(ChunkPos position) {
        Validate.notNull(position, "Chunk position cannot be null");
        this.position = position;
        // Initialize with air blocks (assuming block ID 0 represents air)
        this.blockData = new short[TOTAL_BLOCKS];
        this.state = ChunkState.EMPTY; // Initialize state (P3-T1.4)
        // Neighbors are initially null
    }

    // Private constructor for deserialization
    private Chunk(ChunkPos position, short[] blockData) {
        this.position = position;
        this.blockData = blockData;
        this.state = ChunkState.GENERATED; // Assume deserialized chunks are generated
    }

    /**
     * Gets the position of this chunk in the world grid.
     *
     * @return The ChunkPos of this chunk.
     */
    public ChunkPos getPosition() {
        return position;
    }

    /**
     * Gets the block ID at the specified local coordinates within this chunk.
     * Coordinates must be within the chunk bounds [0, 15].
     *
     * @param localX The local x-coordinate (0-15).
     * @param localY The local y-coordinate (0-15).
     * @param localZ The local z-coordinate (0-15).
     * @return The block ID (short) at that position.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     */
    public short getBlock(int localX, int localY, int localZ) {
        int index = CoordinateUtils.localCoordsToIndex(localX, localY, localZ); // Handles bounds checking
        return blockData[index];
    }

    /**
     * Gets the block ID at the specified local coordinates within this chunk.
     * Coordinates must be within the chunk bounds [0, 15].
     *
     * @param localPos The local coordinates (Vec3i, components 0-15). Must not be null.
     * @return The block ID (short) at that position.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     * @throws NullPointerException if localPos is null.
     */
    public short getBlock(Vec3i localPos) {
        Validate.notNull(localPos, "Local position cannot be null");
        return getBlock(localPos.x, localPos.y, localPos.z);
    }

    /**
     * Gets the BlockProperties for the block at the specified local coordinates.
     * Coordinates must be within the chunk bounds [0, 15].
     * This method consults the BlockRegistry.
     *
     * @param localX The local x-coordinate (0-15).
     * @param localY The local y-coordinate (0-15).
     * @param localZ The local z-coordinate (0-15).
     * @return The BlockProperties for the block, or BlockRegistry.AIR if the ID is invalid or registry not initialized.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     */
    public BlockProperties getBlockProperties(int localX, int localY, int localZ) {
        short blockId = getBlock(localX, localY, localZ);
        BlockProperties properties = blockRegistry.getBlock(blockId);
        // Return AIR as a safe default if lookup fails (e.g., invalid ID, registry not ready)
        return properties != null ? properties : BlockRegistry.AIR;
    }

    /**
     * Gets the BlockProperties for the block at the specified local coordinates.
     * Coordinates must be within the chunk bounds [0, 15].
     * This method consults the BlockRegistry.
     *
     * @param localPos The local coordinates (Vec3i, components 0-15). Must not be null.
     * @return The BlockProperties for the block, or BlockRegistry.AIR if the ID is invalid or registry not initialized.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     * @throws NullPointerException if localPos is null.
     */
    public BlockProperties getBlockProperties(Vec3i localPos) {
        Validate.notNull(localPos, "Local position cannot be null");
        return getBlockProperties(localPos.x, localPos.y, localPos.z);
    }

    /**
     * Checks if the block at the specified local coordinates is air.
     * Coordinates must be within the chunk bounds [0, 15].
     *
     * @param localX The local x-coordinate (0-15).
     * @param localY The local y-coordinate (0-15).
     * @param localZ The local z-coordinate (0-15).
     * @return True if the block is air, false otherwise.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     */
    public boolean isAir(int localX, int localY, int localZ) {
        // Direct ID check is faster than getting properties
        return getBlock(localX, localY, localZ) == BlockRegistry.AIR.getId();
    }

    /**
     * Checks if the block at the specified local coordinates is air.
     * Coordinates must be within the chunk bounds [0, 15].
     *
     * @param localPos The local coordinates (Vec3i, components 0-15). Must not be null.
     * @return True if the block is air, false otherwise.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     * @throws NullPointerException if localPos is null.
     */
    public boolean isAir(Vec3i localPos) {
        Validate.notNull(localPos, "Local position cannot be null");
        return isAir(localPos.x, localPos.y, localPos.z);
    }

    /**
     * Checks if the block at the specified local coordinates is solid.
     * Coordinates must be within the chunk bounds [0, 15].
     * This method consults the BlockRegistry.
     *
     * @param localX The local x-coordinate (0-15).
     * @param localY The local y-coordinate (0-15).
     * @param localZ The local z-coordinate (0-15).
     * @return True if the block is solid, false otherwise.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     */
    public boolean isSolid(int localX, int localY, int localZ) {
        // No need to handle null properties explicitly, getBlockProperties returns AIR which is not solid.
        return getBlockProperties(localX, localY, localZ).isSolid();
    }

    /**
     * Checks if the block at the specified local coordinates is solid.
     * Coordinates must be within the chunk bounds [0, 15].
     * This method consults the BlockRegistry.
     *
     * @param localPos The local coordinates (Vec3i, components 0-15). Must not be null.
     * @return True if the block is solid, false otherwise.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     * @throws NullPointerException if localPos is null.
     */
    public boolean isSolid(Vec3i localPos) {
        Validate.notNull(localPos, "Local position cannot be null");
        return isSolid(localPos.x, localPos.y, localPos.z);
    }

    /**
     * Sets the block ID at the specified local coordinates within this chunk.
     * Coordinates must be within the chunk bounds [0, 15].
     * Note: This method is not inherently thread-safe during concurrent modification.
     * Synchronization should be handled externally if needed (e.g., during world generation).
     *
     * @param localX The local x-coordinate (0-15).
     * @param localY The local y-coordinate (0-15).
     * @param localZ The local z-coordinate (0-15).
     * @param blockId The new block ID to set.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     */
    public void setBlock(int localX, int localY, int localZ, short blockId) {
        int index = CoordinateUtils.localCoordsToIndex(localX, localY, localZ); // Handles bounds checking
        // Consider thread safety if state change needs atomicity with block change
        if (blockData[index] != blockId) {
            blockData[index] = blockId;
            // TODO: Mark chunk as modified (for P3-T1.7) - This might involve setting state to MODIFIED
            // setState(ChunkState.MODIFIED); // Or use a separate boolean flag
        }
    }

    /**
     * Sets the block ID at the specified local coordinates within this chunk.
     * Coordinates must be within the chunk bounds [0, 15].
     * Note: This method is not inherently thread-safe during concurrent modification.
     * Synchronization should be handled externally if needed (e.g., during world generation).
     *
     * @param localPos The local coordinates (Vec3i, components 0-15). Must not be null.
     * @param blockId The new block ID to set.
     * @throws IndexOutOfBoundsException if coordinates are out of bounds.
     * @throws NullPointerException if localPos is null.
     */
    public void setBlock(Vec3i localPos, short blockId) {
        Validate.notNull(localPos, "Local position cannot be null");
        setBlock(localPos.x, localPos.y, localPos.z, blockId);
    }

    // --- Chunk State Management (P3-T1.4) ---

    /**
     * Gets the current lifecycle state of the chunk.
     *
     * @return The current ChunkState.
     */
    public ChunkState getState() {
        return state;
    }

    /**
     * Sets the lifecycle state of the chunk.
     * This method should be used carefully, ensuring state transitions are valid.
     * Consider making this package-private or protected if only world management systems should change state.
     * Marked as volatile for visibility across threads, but state transitions might need stronger synchronization.
     *
     * @param newState The new ChunkState.
     */
    public void setState(ChunkState newState) {
        Validate.notNull(newState, "New chunk state cannot be null");
        // TODO: Add validation logic for valid state transitions if needed
        this.state = newState;
    }

    // --- Neighbor Management (P3-T1.5) ---

    /**
     * Gets the neighboring chunk in the specified direction.
     *
     * @param direction The direction of the neighbor. Must not be null.
     * @return The neighboring Chunk, or null if it's not set or doesn't exist.
     */
    public Chunk getNeighbor(Direction direction) {
        Validate.notNull(direction, "Direction cannot be null");
        return neighbors[direction.getIndex()];
    }

    /**
     * Sets the neighboring chunk in the specified direction.
     * This is typically managed by the world loading system.
     *
     * @param direction The direction of the neighbor. Must not be null.
     * @param neighbor The neighboring Chunk (can be null).
     */
    public void setNeighbor(Direction direction, Chunk neighbor) {
        Validate.notNull(direction, "Direction cannot be null");
        // Optional: Add validation to check if the neighbor's position matches?
        // ChunkPos expectedNeighborPos = this.position.toVec3i().add(direction.getOffset()).toChunkPos();
        // if (neighbor != null && !neighbor.getPosition().equals(expectedNeighborPos)) {
        //     throw new IllegalArgumentException("Neighbor position mismatch");
        // }
        neighbors[direction.getIndex()] = neighbor;
    }

    // --- Serialization (P3-T1.6) ---

    /**
     * Writes the chunk data to the given DataOutputStream.
     * Format:
     * - int: version (SERIALIZATION_VERSION)
     * - int: position.x
     * - int: position.y
     * - int: position.z
     * - short[TOTAL_BLOCKS]: blockData
     *
     * @param out The output stream to write to. Must not be null.
     * @throws IOException If an I/O error occurs.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        Validate.notNull(out, "Output stream cannot be null");

        out.writeInt(SERIALIZATION_VERSION);
        out.writeInt(position.x);
        out.writeInt(position.y);
        out.writeInt(position.z);

        // Write block data directly
        // Consider compression here in the future if needed (e.g., GZIPOutputStream)
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            out.writeShort(blockData[i]);
        }
    }

    /**
     * Reads chunk data from the given DataInputStream and creates a new Chunk instance.
     * Assumes the stream follows the format defined in writeTo().
     *
     * @param in The input stream to read from. Must not be null.
     * @return A new Chunk instance populated with data from the stream.
     * @throws IOException If an I/O error occurs or the format is invalid.
     */
    public static Chunk readFrom(DataInputStream in) throws IOException {
        Validate.notNull(in, "Input stream cannot be null");

        int version = in.readInt();
        if (version != SERIALIZATION_VERSION) {
            throw new IOException("Unsupported chunk serialization version: " + version +
                                ". Expected: " + SERIALIZATION_VERSION);
        }

        int x = in.readInt();
        int y = in.readInt();
        int z = in.readInt();
        ChunkPos pos = new ChunkPos(x, y, z);

        short[] data = new short[TOTAL_BLOCKS];
        // Consider decompression here if writeTo uses compression
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            data[i] = in.readShort();
        }

        // Use the private constructor
        return new Chunk(pos, data);
    }
}
