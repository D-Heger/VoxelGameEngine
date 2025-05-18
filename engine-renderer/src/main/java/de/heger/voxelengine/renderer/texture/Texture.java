package de.heger.voxelengine.renderer.texture;

import de.heger.voxelengine.assets.texture.TextureData;
import de.heger.voxelengine.core.logging.LoggerFacade;
import org.lwjgl.stb.STBImage;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_RG;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

import org.lwjgl.opengl.GL12;

public class Texture {

    private static final LoggerFacade LOGGER = LoggerFacade.get(Texture.class);
    private final int textureId;
    private final int width;
    private final int height;

    public Texture(TextureData textureData) {
        if (textureData == null || textureData.data() == null) {
            throw new IllegalArgumentException("TextureData and its data buffer cannot be null.");
        }

        this.width = textureData.width();
        this.height = textureData.height();

        // Generate texture ID
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Set texture parameters - Use NEAREST for pixelated look
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE); // Or GL_REPEAT
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE); // Or GL_REPEAT
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST); // Use mipmaps with nearest filtering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Determine OpenGL format based on channels
        int internalFormat;
        int format;
        switch (textureData.channels()) {
            case 1:
                internalFormat = GL_RED;
                format = GL_RED;
                break;
            case 2:
                internalFormat = GL_RG;
                format = GL_RG;
                break;
            case 3:
                internalFormat = GL_RGB;
                format = GL_RGB;
                // Set unpack alignment to 1 for RGB data, common issue
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                break;
            case 4:
                internalFormat = GL_RGBA;
                format = GL_RGBA;
                break;
            default:
                // Free buffer before throwing
                STBImage.stbi_image_free(textureData.data());
                throw new IllegalArgumentException("Unsupported number of channels: " + textureData.channels());
        }

        // Upload texture data
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, textureData.data());

        // Generate mipmaps
        glGenerateMipmap(GL_TEXTURE_2D);

        // Free the STB image buffer now that data is on the GPU
        STBImage.stbi_image_free(textureData.data());
        LOGGER.debug("Freed STB image buffer for texture ID: {}", textureId);

        // Restore default pixel store alignment if changed
        if (textureData.channels() == 3) {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        }

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);

        LOGGER.info("Created OpenGL texture ID: {} ({}x{})", textureId, width, height);
    }

    // New constructor for raw grayscale pixel data (e.g., font atlas)
    public Texture(int width, int height, java.nio.ByteBuffer pixelData) {
        this.width = width;
        this.height = height;

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        // For fonts, GL_LINEAR can look smoother than GL_NEAREST
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); 
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Upload texture data - STB bakeFontBitmap produces 1-channel (alpha) bitmap
        // We use GL_RED to store it, and swizzle in shader if needed, or treat R as Alpha.
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // Ensure correct alignment for single channel
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, pixelData);

        // No mipmaps needed or desired for font atlases typically.
        // If you wanted mipmaps: glGenerateMipmap(GL_TEXTURE_2D); 
        
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4); // Restore default alignment

        glBindTexture(GL_TEXTURE_2D, 0);
        LOGGER.info("Created OpenGL texture ID: {} ({}x{}) from raw data for font atlas.", textureId, width, height);
    }

    public void bind(int textureUnit) {
        if (textureUnit < 0 || textureUnit > 31) { // Check against GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS limit (usually at least 32)
            LOGGER.warn("Texture unit {} is out of typical range.", textureUnit);
            // Clamp or throw, depending on desired behavior. Clamping for now.
            textureUnit = Math.max(0, Math.min(31, textureUnit));
        }
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        // Unbinding specific unit is tricky, usually just bind 0 to the active unit
        // or rely on the next texture bind to replace it.
        // For simplicity, we can unbind from TEXTURE_2D target on the *last active* unit,
        // but it's often unnecessary if managed correctly.
        // glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
        LOGGER.debug("Deleted OpenGL texture ID: {}", textureId);
    }

    public int getTextureId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
