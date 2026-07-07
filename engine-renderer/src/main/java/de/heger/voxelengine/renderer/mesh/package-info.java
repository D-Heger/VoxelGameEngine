/**
 * Turning a cube of blocks into triangles the GPU can draw.
 *
 * <p>This is where voxels become geometry.
 * {@link de.heger.voxelengine.renderer.mesh.ChunkMeshBuilder} walks a chunk and
 * emits only the block faces that are actually visible &mdash; skipping faces
 * buried between two solid blocks &mdash; producing a compact
 * {@link de.heger.voxelengine.renderer.mesh.MeshData} of vertices and indices.
 * {@link de.heger.voxelengine.renderer.mesh.ChunkMesh} wraps that data in the
 * OpenGL buffers (VAO/VBO) used to draw it, and
 * {@link de.heger.voxelengine.renderer.mesh.BufferPool} recycles buffers to keep
 * per-frame allocation and GC pressure down.</p>
 */
package de.heger.voxelengine.renderer.mesh;
