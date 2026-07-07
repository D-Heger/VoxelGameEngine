/**
 * GPU-side textures.
 *
 * <p>Where {@code engine-assets} produces raw pixel data on the CPU, this package
 * takes the final step: {@link de.heger.voxelengine.renderer.texture.Texture}
 * uploads that data into an OpenGL texture object and manages its parameters
 * (filtering, wrapping, mipmaps) and binding. Block textures, the atlas, and font
 * glyph sheets all end up here before they can be sampled by a shader.</p>
 */
package de.heger.voxelengine.renderer.texture;
