package de.heger.voxelengine.assets.texture;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import de.heger.voxelengine.core.logging.LoggerFacade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TextureLoader {

    private static final LoggerFacade LOGGER = LoggerFacade.get(TextureLoader.class);

    public TextureData loadTexture(String resourcePath) {
        LOGGER.debug("Loading texture resource: {}", resourcePath);
        ByteBuffer imageBuffer;
        try {
            // Try loading from classpath first
            imageBuffer = ioResourceToByteBuffer(resourcePath, 8 * 1024); // 8KB buffer initial size
        } catch (IOException e) {
            LOGGER.error("Failed to load texture resource from classpath: {}", resourcePath, e);
            return null; // Or throw a custom exception
        }

        if (imageBuffer == null) {
            LOGGER.error("Could not read texture resource to buffer: {}", resourcePath);
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // Decode the image
            ByteBuffer decodedImage = STBImage.stbi_load_from_memory(imageBuffer, w, h, channels, 0);
            MemoryUtil.memFree(imageBuffer); // Free the intermediate buffer

            if (decodedImage == null) {
                LOGGER.error("Failed to load texture image using STB: {}. Reason: {}", resourcePath, STBImage.stbi_failure_reason());
                return null;
            }

            int width = w.get(0);
            int height = h.get(0);
            int numChannels = channels.get(0);

            LOGGER.info("Loaded texture '{}': {}x{} ({} channels)", resourcePath, width, height, numChannels);

            // Note: The caller (Texture class) is responsible for freeing the decodedImage buffer
            // using STBImage.stbi_image_free() after uploading to OpenGL.
            return new TextureData(width, height, numChannels, decodedImage);
        } catch (Exception e) {
            LOGGER.error("Exception during texture loading: {}", resourcePath, e);
            // Ensure intermediate buffer is freed if an exception occurs before STB load
            // (though stbi_load_from_memory should handle its input buffer)
            // If decodedImage was allocated but an exception happened after, it might leak.
            // However, the record holds the reference, and the Texture class will free it.
            return null;
        }
    }

    // Helper method to read resource to ByteBuffer (from LWJGL examples)
    private ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        ByteBuffer buffer;
        Path path = Paths.get(resource); // Try filesystem path first

        if (Files.isReadable(path)) {
            try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                buffer = MemoryUtil.memAlloc((int) fc.size() + 1);
                while (fc.read(buffer) != -1) {
                    // Loop
                }
            }
        } else {
            // Try classpath
            try (
                    InputStream source = TextureLoader.class.getClassLoader().getResourceAsStream(resource);
                    ReadableByteChannel rbc = Channels.newChannel(source)
            ) {
                if (source == null) {
                    throw new IOException("Classpath resource not found: " + resource);
                }
                buffer = MemoryUtil.memAlloc(bufferSize);
                while (true) {
                    int bytes = rbc.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() == 0) {
                        buffer = MemoryUtil.memRealloc(buffer, buffer.capacity() * 2);
                    }
                }
            }
        }

        buffer.flip();
        return buffer;
    }
}
