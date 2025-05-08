package de.heger.voxelengine.world.chunk;

/**
 * Represents the lifecycle states of a Chunk.
 * The state indicates the processing stage the chunk has reached.
 * Note: The MODIFIED state might be better represented as an orthogonal flag,
 * as a chunk can be modified while being in GENERATED or MESHED state.
 * This enum follows the initial task description.
 */
public enum ChunkState {
    /**
     * The chunk has been created, potentially filled with default data (e.g., air),
     * but has not undergone world generation yet.
     */
    EMPTY,

    /**
     * The chunk's block data has been populated by the world generator.
     */
    GENERATED,

    /**
     * A render mesh has been generated for this chunk based on its block data.
     */
    MESHED,

    /**
     * The chunk is fully loaded and ready for interaction and rendering.
     * (The distinction between MESHED and LOADED might need refinement).
     */
    LOADED,

    /**
     * The chunk's block data has been modified since it was last generated or saved.
     */
    MODIFIED
}
