package de.heger.voxelengine.world.chunk;

/**
 * Represents the state of a chunk's mesh.
 */
public enum ChunkMeshState {
    /**
     * The chunk's mesh has not been built yet or needs to be rebuilt.
     */
    NEEDS_REBUILD,

    /**
     * The chunk's mesh geometry data is currently being generated in a background thread.
     * OpenGL mesh objects have not yet been created.
     */
    BUILDING_DATA,

    /**
     * The chunk's mesh geometry data has been generated and is ready for OpenGL mesh creation
     * on the main render thread.
     */
    MESH_DATA_READY,

    /**
     * The chunk's mesh is currently being built or rebuilt (OpenGL objects are being created).
     * This state implies that mesh data generation is complete if it was done asynchronously.
     */
    BUILDING,

    /**
     * The chunk's mesh has been built and is up-to-date.
     */
    UP_TO_DATE,

    /**
     * The chunk's mesh has been built, but it contains no renderable geometry (e.g., a chunk full of air).
     */
    EMPTY
} 