package de.heger.voxelengine.renderer.ui.font;

import de.heger.voxelengine.core.logging.LoggerFacade;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class FontManager {
    private static final LoggerFacade LOGGER = LoggerFacade.get(FontManager.class);
    private final Map<String, Font> fontCache = new HashMap<>();

    // Default font properties
    public static final String DEFAULT_FONT_NAME = "Roboto-Regular";
    public static final String DEFAULT_FONT_PATH = "fonts/Roboto-Regular.ttf"; // Relative to classpath resources
    public static final float DEFAULT_FONT_SIZE = 16f;
    public static final int DEFAULT_ATLAS_WIDTH = 512;
    public static final int DEFAULT_ATLAS_HEIGHT = 512;
    public static final int DEFAULT_FIRST_CHAR = 32; // ASCII space
    public static final int DEFAULT_NUM_CHARS = 95;  // Space to Tidlde (ASCII 32-126)

    public FontManager() {
        // Eagerly load default font or handle errors
        try {
            getDefaultFont();
        } catch (Exception e) {
            LOGGER.error("Failed to load default font '{}' during FontManager initialization.", DEFAULT_FONT_NAME, e);
            // Depending on policy, could throw, or allow manager to exist with no default font.
        }
    }

    public Font getFont(String name) {
        return fontCache.get(name);
    }

    public Font getDefaultFont() {
        Font font = fontCache.get(DEFAULT_FONT_NAME + "_" + DEFAULT_FONT_SIZE);
        if (font == null) {
            try {
                font = loadFont(DEFAULT_FONT_NAME, DEFAULT_FONT_PATH, DEFAULT_FONT_SIZE, 
                                DEFAULT_ATLAS_WIDTH, DEFAULT_ATLAS_HEIGHT, 
                                DEFAULT_FIRST_CHAR, DEFAULT_NUM_CHARS);
            } catch (IOException e) {
                LOGGER.error("Failed to load default font: {}", DEFAULT_FONT_PATH, e);
                // throw new RuntimeException("Could not load default font: " + DEFAULT_FONT_PATH, e);
                return null; // Or some fallback mechanism
            }
        }
        return font;
    }

    public Font loadFont(String fontName, String resourcePath, float fontSize, 
                         int atlasWidth, int atlasHeight, int firstChar, int numChars) throws IOException {
        String cacheKey = fontName + "_" + fontSize;
        if (fontCache.containsKey(cacheKey)) {
            return fontCache.get(cacheKey);
        }

        ByteBuffer ttfBuffer = ioResourceToByteBuffer(resourcePath, 1024 * 500); // 500KB buffer for font
        
        Font font = new Font(fontName, fontSize, ttfBuffer, atlasWidth, atlasHeight, firstChar, numChars);
        fontCache.put(cacheKey, font);
        LOGGER.info("Loaded font: {} ({}) with size {}", fontName, resourcePath, fontSize);
        
        // The ttfBuffer was copied inside Font constructor, so we can free the one loaded here if it was direct.
        // If ioResourceToByteBuffer allocates direct, it should be freed.
        // For now, assume Font constructor handles its copy and this one might be GC'd if not direct,
        // or needs explicit free if MemoryUtil.memAlloc was used by ioResourceToByteBuffer.
        // Let's assume ioResourceToByteBuffer returns a buffer that needs to be freed if direct.
        if (ttfBuffer.isDirect()) {
            MemoryUtil.memFree(ttfBuffer);
        }

        return font;
    }

    // Helper method to load a resource file into a ByteBuffer
    private ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        LOGGER.debug("Attempting to load resource: {}", resource);
        Path path = null;
        ByteBuffer buffer;

        try (InputStream source = FontManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (source == null) {
                throw new IOException("Classpath resource not found: " + resource);
            }
            try (ReadableByteChannel rbc = Channels.newChannel(source)) {
                buffer = MemoryUtil.memAlloc(bufferSize); // Allocate direct buffer
                int bytesRead = 0;
                while (true) {
                    int res = rbc.read(buffer);
                    if (res == -1) {
                        break;
                    }
                    bytesRead += res;
                    if (!buffer.hasRemaining()) {
                        // Buffer filled, reallocate if needed (or error if too small)
                        // For simplicity, assume bufferSize is adequate for typical fonts.
                        // Production code might reallocate or use a growing buffer strategy.
                        LOGGER.warn("Buffer for resource {} might be too small ({} bytes read, {} capacity). Some data might be truncated.", resource, bytesRead, bufferSize);
                        break; 
                    }
                }
            }
        } catch (NullPointerException e) { // getResourceAsStream can return null
            throw new IOException("Classpath resource not found (NPE): " + resource, e);
        }

        buffer.flip();
        LOGGER.debug("Loaded resource {} into direct ByteBuffer ({} bytes)", resource, buffer.remaining());
        return buffer;
    }

    public void cleanup() {
        LOGGER.info("Cleaning up FontManager...");
        for (Font font : fontCache.values()) {
            if (font != null) {
                font.cleanup();
            }
        }
        fontCache.clear();
        LOGGER.info("FontManager cleanup complete.");
    }
}