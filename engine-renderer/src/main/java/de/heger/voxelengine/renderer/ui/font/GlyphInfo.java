package de.heger.voxelengine.renderer.ui.font;

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