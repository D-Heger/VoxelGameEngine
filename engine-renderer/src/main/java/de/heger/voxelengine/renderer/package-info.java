/**
 * The top of the rendering stack, where a frame actually gets drawn.
 *
 * <p>{@link de.heger.voxelengine.renderer.Renderer} is the conductor. Each frame
 * it sets up OpenGL state, decides which chunks are worth drawing (with help from
 * the {@link de.heger.voxelengine.renderer.culling} package), uploads the camera
 * and lighting data, and issues the draw calls for every visible chunk mesh. It
 * also drives shadow rendering and hands off to the UI layer for the HUD and
 * menus.</p>
 *
 * <p>The heavy lifting is delegated to focused sub-packages &mdash; meshing,
 * shaders, textures, culling, lighting management, and the UI toolkit &mdash; so
 * this package stays about orchestration rather than detail.</p>
 */
package de.heger.voxelengine.renderer;
