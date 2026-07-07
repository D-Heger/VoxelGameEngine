package de.heger.voxelengine.renderer.ui.font;

/**
 * Layout and atlas information for a single character in a {@link Font}.
 *
 * <p>Everything needed to place and texture one glyph: how far to advance the
 * pen afterwards ({@code xadvance}), the offset and size of the glyph's quad,
 * and the {@code u0,v0}&ndash;{@code u1,v1} texture coordinates into the font
 * atlas. The fields are public and final; this is a small immutable value read
 * on hot text-layout paths.</p>
 */
public class GlyphInfo {
    public final float xadvance;
    public final float xoffset;
    public final float yoffset;
    public final float width;
    public final float height;
    public final float u0, v0, u1, v1; // Texture coordinates

    public GlyphInfo(float xadvance, float xoffset, float yoffset, float width, float height, float u0, float v0, float u1, float v1) {
        this.xadvance = xadvance;
        this.xoffset = xoffset;
        this.yoffset = yoffset;
        this.width = width;
        this.height = height;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }
} 