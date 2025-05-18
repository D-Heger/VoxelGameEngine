package de.heger.voxelengine.world.chunk;

/**
 * Represents the state of a chunk's mesh.
 */
public enum ChunkMeshState {
    /** The chunk's mesh has not been built yet, or the chunk is entirely empty (e.g. all air). */
    EMPTY,
    /** The chunk's mesh has been built and is current. */
    UP_TO_DATE,
    /** The chunk's data has changed, and its mesh needs to be rebuilt. */
    NEEDS_REBUILD,
    /** The chunk's mesh is currently being built (potentially asynchronously). */
    BUILDING
} 