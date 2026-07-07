/**
 * Reading image files into raw pixel data the GPU can use.
 *
 * <p>{@link de.heger.voxelengine.assets.texture.TextureLoader} decodes image
 * files (via STB) into a {@link de.heger.voxelengine.assets.texture.TextureData}
 * holder &mdash; width, height, channel count, and the pixel buffer. Note that
 * this module deliberately stops at the CPU-side data; actually uploading it to
 * OpenGL is the renderer's job. That split keeps asset loading testable and free
 * of a hard dependency on a live GL context.</p>
 */
package de.heger.voxelengine.assets.texture;
