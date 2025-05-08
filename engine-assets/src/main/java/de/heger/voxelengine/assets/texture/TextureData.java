package de.heger.voxelengine.assets.texture;

import java.nio.ByteBuffer;

/**
 * Holds raw texture data loaded from a file.
 *
 * @param width    The width of the texture in pixels.
 * @param height   The height of the texture in pixels.
 * @param channels The number of color channels (e.g., 3 for RGB, 4 for RGBA).
 * @param data     A ByteBuffer containing the raw pixel data.
 */
public record TextureData(int width, int height, int channels, ByteBuffer data) {
    // No additional methods needed for a simple record.
}
