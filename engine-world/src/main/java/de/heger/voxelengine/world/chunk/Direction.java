package de.heger.voxelengine.world.chunk;

import de.heger.voxelengine.core.math.Vec3i;

/**
 * Represents the six cardinal directions plus UP and DOWN.
 * Useful for navigating the chunk grid and accessing neighbors.
 */
public enum Direction {
    // Order matters for potential array indexing using ordinal()
    DOWN(0, new Vec3i(0, -1, 0)),  // Y-
    UP(1, new Vec3i(0, 1, 0)),    // Y+
    NORTH(2, new Vec3i(0, 0, -1)), // Z-
    SOUTH(3, new Vec3i(0, 0, 1)),  // Z+
    WEST(4, new Vec3i(-1, 0, 0)),  // X-
    EAST(5, new Vec3i(1, 0, 0));   // X+

    private final int index;
    private final Vec3i offset;

    Direction(int index, Vec3i offset) {
        this.index = index;
        this.offset = offset;
    }

    /**
     * Gets the index of this direction (0-5).
     * Useful for indexing arrays.
     * @return The index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets the unit vector representing the offset in this direction.
     * @return The offset vector (Vec3i).
     */
    public Vec3i getOffset() {
        return offset;
    }

    /**
     * Gets the opposite direction.
     * @return The opposite Direction.
     */
    public Direction getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /**
     * Gets a Direction from its index (0-5).
     * @param index The index.
     * @return The corresponding Direction.
     * @throws IllegalArgumentException if the index is invalid.
     */
    public static Direction fromIndex(int index) {
        return switch (index) {
            case 0 -> DOWN;
            case 1 -> UP;
            case 2 -> NORTH;
            case 3 -> SOUTH;
            case 4 -> WEST;
            case 5 -> EAST;
            default -> throw new IllegalArgumentException("Invalid direction index: " + index);
        };
    }
}
