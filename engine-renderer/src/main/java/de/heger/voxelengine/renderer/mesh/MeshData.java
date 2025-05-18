package de.heger.voxelengine.renderer.mesh;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * A helper class to store vertex and index data for a part of a chunk mesh,
 * typically grouped by texture. This class is mutable for performance reasons
 * when building meshes.
 */
class MeshData {
    final FloatArrayList vertexList;
    final IntArrayList indexList;
    int currentIndexOffset; // Tracks the number of vertices added to this specific mesh part for index calculation

    public MeshData() {
        this.vertexList = new FloatArrayList();
        this.indexList = new IntArrayList();
        this.currentIndexOffset = 0;
    }

    public void incrementVertexOffset(int count) {
        this.currentIndexOffset += count;
    }
} 