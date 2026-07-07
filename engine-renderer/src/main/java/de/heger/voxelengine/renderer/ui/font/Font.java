package de.heger.voxelengine.renderer.ui.font;

import de.heger.voxelengine.renderer.texture.Texture;

import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * A single loaded font, baked into a glyph atlas ready for rendering.
 *
 * <p>On creation the font's TrueType data is rasterised (via STB) at a fixed
 * size into one texture atlas, and the position and metrics of each character
 * are recorded as {@link GlyphInfo}. Text rendering then looks up glyphs by
 * character and reads the vertical metrics (ascent, descent, line gap) held
 * here to lay out lines. Fonts are typically obtained and cached through
 * {@link FontManager}, not constructed directly.</p>
 */
public class Font {
    private final String fontName;
    private final float fontSize;
    private Texture fontAtlasTexture;
    private final Map<Character, GlyphInfo> glyphs;
    private final STBTTBakedChar.Buffer bakedCharData;
    private final int ascent, descent, lineGap;
    private final float scale;

    public Font(String fontName, float fontSize, ByteBuffer ttfBuffer, int atlasWidth, int atlasHeight, int firstChar, int numChars) {
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.glyphs = new HashMap<>();
        this.bakedCharData = STBTTBakedChar.malloc(numChars);

        ByteBuffer bitmap = ByteBuffer.allocateDirect(atlasWidth * atlasHeight);
        ByteBuffer ttfCopy = ByteBuffer.allocateDirect(ttfBuffer.capacity());
        ttfCopy.put(ttfBuffer);
        ttfCopy.flip();

        int bakeResult = stbtt_BakeFontBitmap(ttfCopy, fontSize, bitmap, atlasWidth, atlasHeight, firstChar, bakedCharData);
        if (bakeResult <= 0) {
            throw new RuntimeException("Failed to bake font bitmap for " + fontName + ". Bake result: " + bakeResult);
        }

        this.fontAtlasTexture = new Texture(atlasWidth, atlasHeight, bitmap);

        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        ttfBuffer.rewind();
        if (!stbtt_InitFont(fontInfo, ttfBuffer)) {
            throw new RuntimeException("Failed to initialize font info for " + fontName);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pAscent = stack.mallocInt(1);
            IntBuffer pDescent = stack.mallocInt(1);
            IntBuffer pLineGap = stack.mallocInt(1);

            stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap);
            this.scale = stbtt_ScaleForPixelHeight(fontInfo, fontSize);
            // Use precise metrics for vertical alignment
            ascent = Math.round(pAscent.get(0) * scale);
            descent = Math.round(pDescent.get(0) * scale);
            lineGap = Math.round(pLineGap.get(0) * scale);
        }

        for (int i = 0; i < numChars; ++i) {
            char c = (char) (firstChar + i);
            STBTTBakedChar bakedC = bakedCharData.get(i);
            GlyphInfo glyph = new GlyphInfo(
                bakedC.xadvance(),
                bakedC.xoff(),
                bakedC.yoff() + ascent,
                bakedC.x1() - bakedC.x0(),
                bakedC.y1() - bakedC.y0(),
                (float) bakedC.x0() / atlasWidth,
                (float) bakedC.y0() / atlasHeight,
                (float) bakedC.x1() / atlasWidth,
                (float) bakedC.y1() / atlasHeight
            );
            glyphs.put(c, glyph);
        }
    }

    public GlyphInfo getGlyph(char c) {
        return glyphs.get(c);
    }

    public Texture getFontAtlasTexture() {
        return fontAtlasTexture;
    }

    public float getFontSize() {
        return fontSize;
    }

    public String getFontName() {
        return fontName;
    }

    public STBTTBakedChar.Buffer getBakedCharData() { return bakedCharData; }

    public int getAtlasWidth() { return fontAtlasTexture.getWidth(); }
    public int getAtlasHeight() { return fontAtlasTexture.getHeight(); }

    public int getAscent() { return ascent; }
    public int getDescent() { return descent; }
    public int getLineGap() { return lineGap; }
    public float getLineHeight() { return (float)(ascent - descent + lineGap); }
    public float getScale() { return scale; }

    public float getTextWidth(String text, int firstChar) {
        float width = 0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.floats(0.0f);
            FloatBuffer y = stack.floats(0.0f);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int charIndexInBakedBuffer = c - firstChar;

                if (charIndexInBakedBuffer >= 0 && charIndexInBakedBuffer < bakedCharData.capacity()) {
                    stbtt_GetBakedQuad(bakedCharData, getAtlasWidth(), getAtlasHeight(), charIndexInBakedBuffer, x, y, null, false);
                    
                    GlyphInfo gi = getGlyph(c);
                    if (gi != null) {
                        if (i < text.length() -1) {
                             width += gi.xadvance;
                        } else {
                             width = x.get(0); 
                        }
                    } else {
                         width = x.get(0);
                    }
                } else {
                }
            }
        }
        return width;
    }

    public void cleanup() {
        if (fontAtlasTexture != null) {
            fontAtlasTexture.cleanup();
            fontAtlasTexture = null;
        }
        if (bakedCharData != null) {
            bakedCharData.free();
        }
    }
} 