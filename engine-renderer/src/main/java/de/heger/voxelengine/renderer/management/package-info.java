/**
 * The renderer's bookkeepers.
 *
 * <p>The main {@link de.heger.voxelengine.renderer.Renderer} stays readable by
 * delegating whole areas of responsibility to managers in this package:</p>
 * <ul>
 *   <li>{@link de.heger.voxelengine.renderer.management.ChunkMeshManager} &mdash;
 *       owns the GPU meshes for chunks, building and rebuilding them as blocks
 *       change and discarding them when chunks unload.</li>
 *   <li>{@link de.heger.voxelengine.renderer.management.TextureManager} &mdash;
 *       loads textures and packs them into the block atlas.</li>
 *   <li>{@link de.heger.voxelengine.renderer.management.SceneLightingManager}
 *       &mdash; holds the sun direction, colours, and shadow settings for the
 *       scene.</li>
 *   <li>{@link de.heger.voxelengine.renderer.management.RenderStats} &mdash;
 *       counts draw calls, triangles, and visible chunks for the debug overlay.</li>
 * </ul>
 */
package de.heger.voxelengine.renderer.management;
