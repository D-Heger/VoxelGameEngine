package de.heger.voxelengine.renderer.ui.elements;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIRenderer;
import de.heger.voxelengine.renderer.ui.UIShader;
import de.heger.voxelengine.renderer.ui.font.Font;
import de.heger.voxelengine.renderer.ui.font.FontManager;
import de.heger.voxelengine.renderer.ui.font.GlyphInfo;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;

public class TextElement extends UIElement {
    private static final LoggerFacade LOGGER = LoggerFacade.get(TextElement.class);

    private String text;
    private Font font;
    private Vector4f color;

    private int vaoId = -1, vboId = -1, eboId = -1;
    private int indexCount = 0; // Number of indices

    private final Matrix4f modelMatrix = new Matrix4f();

    public TextElement(String text, Font font, Vector2f position, Vector4f color) {
        super(position, new Vector2f(0, 0)); 
        this.text = text;
        this.font = font;
        this.color = color;
        if (font == null) {
            LOGGER.warn("TextElement created with null font for text: {}", text);
        }
        buildMesh();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        if (this.text == null || !this.text.equals(text)) {
            this.text = text;
            buildMesh();
        }
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        if (this.font != font) {
            this.font = font;
            if (font == null) {
                 LOGGER.warn("TextElement font set to null for text: {}", text);
            }
            buildMesh();
        }
    }

    public Vector4f getColor() {
        return color;
    }

    public void setColor(Vector4f color) {
        this.color = color;
    }

    private void buildMesh() {
        if (font == null || text == null || text.isEmpty()) {
            cleanupMesh(); 
            this.size.set(0,0);
            return;
        }
        cleanupMesh();

        int numChars = text.length();
        if (numChars == 0) {
            this.size.set(0,0);
            return;
        }

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;
        float calculatedWidth = 0;
        float calculatedHeight = font.getLineHeight(); // Use line height for vertical alignment and clarity

        STBTTBakedChar.Buffer charData = font.getBakedCharData();
        int atlasW = font.getAtlasWidth();
        int atlasH = font.getAtlasHeight();
        int firstBakedChar = FontManager.DEFAULT_FIRST_CHAR; 

        try (MemoryStack stack = MemoryStack.stackPush()) {
            verticesBuffer = stack.mallocFloat(numChars * 4 * 4); 
            indicesBuffer = stack.mallocInt(numChars * 6);    

            FloatBuffer xPos = stack.floats(0.0f); // Represents current cursor X
            FloatBuffer yPos = stack.floats(0.0f); // Represents current cursor Y (baseline)

            for (int i = 0; i < numChars; i++) {
                char character = text.charAt(i);
                int charIndexInBakedBuffer = character - firstBakedChar;

                if (charIndexInBakedBuffer < 0 || charIndexInBakedBuffer >= charData.capacity()) {
                    GlyphInfo spaceGlyph = font.getGlyph(' ');
                    if (spaceGlyph != null) {
                         xPos.put(0, xPos.get(0) + spaceGlyph.xadvance);
                    } else { 
                         xPos.put(0, xPos.get(0) + font.getFontSize() / 2); 
                    }
                    continue;
                }

                STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
                // Note: stbtt_GetBakedQuad advances xpos and ypos internally.
                // It treats y as top-down, so yoff is typically positive downwards.
                stbtt_GetBakedQuad(charData, atlasW, atlasH, charIndexInBakedBuffer, xPos, yPos, q, false); // false for non-power-of-two textures
                
                // q.x0, q.y0 etc are screen coords. y is from top-left. 
                // Font ascent is height from baseline to top. yPos is baseline from top. We need to adjust glyphs to be positioned relative to (0,0) of the TextElement.
                // STBTTBakedChar yoff is from baseline, positive downwards.
                // Ascent is from baseline, positive upwards.
                // Target: final quad y coords should be relative to the TextElement's (0,0) top-left.
                // If yPos starts at 0 (baseline), q.y0 is like yPos_baseline + glyph_y_offset_from_baseline.
                // We want final y relative to overall text box top. Font line height is ascent - descent + lineGap.
                // Ascent is positive. q.y0 is distance from baseline to top of char. For top-left, this is fine if yPos is 0.
                float final_y0 = q.y0(); //q.y0() already incorporates yPos and yoffset from stbtt_bakedchar
                float final_y1 = q.y1();

                // Top-left
                verticesBuffer.put(q.x0()).put(final_y0).put(q.s0()).put(q.t0());
                // Bottom-left
                verticesBuffer.put(q.x0()).put(final_y1).put(q.s0()).put(q.t1());
                // Bottom-right
                verticesBuffer.put(q.x1()).put(final_y1).put(q.s1()).put(q.t1());
                // Top-right
                verticesBuffer.put(q.x1()).put(final_y0).put(q.s1()).put(q.t0());

                int baseIndex = i * 4;
                indicesBuffer.put(baseIndex + 0).put(baseIndex + 1).put(baseIndex + 2);
                indicesBuffer.put(baseIndex + 0).put(baseIndex + 2).put(baseIndex + 3);
            }
            calculatedWidth = xPos.get(0); 
            // The actual height of rendered text might be different from font.getLineHeight() if some chars are taller.
            // For simplicity, using font.getLineHeight(). A more accurate way would be to track min/max Y of rendered quads.
            // STBTTBakedChar yoff and yoff2 can give bbox. We used y0 and y1 from GetBakedQuad.
            // Height should be max(y1) - min(y0) over all quads, or more simply, font line height.
            calculatedHeight = font.getLineHeight();
        }

        verticesBuffer.flip();
        indicesBuffer.flip();

        if (verticesBuffer.remaining() == 0) {
            cleanupMesh();
            this.size.set(0,0);
            return;
        }

        indexCount = indicesBuffer.remaining();

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

        glVertexAttribPointer(UIShader.ATTRIB_POSITION, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(UIShader.ATTRIB_POSITION);
        glVertexAttribPointer(UIShader.ATTRIB_TEX_COORDS, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(UIShader.ATTRIB_TEX_COORDS);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        this.size.set(calculatedWidth, calculatedHeight);
        LOGGER.trace("Built mesh for text \"{}\", VAO: {}, Indices: {}, Size: {}x{}", 
                     this.text, vaoId, indexCount, calculatedWidth, calculatedHeight);
    }

    @Override
    public void render(UIRenderer renderer) {
        if (!visible || vaoId == -1 || font == null || indexCount == 0) {
            return;
        }
        // UIRenderer will be responsible for shader binding, projection matrix etc.
        UIShader shader = renderer.getUIShader(); 
        // shader.bind(); // UIRenderer should manage this

        shader.loadUseTexture(true); // Tell shader to use a texture
        modelMatrix.identity().translate(position.x, position.y, 0);
        shader.loadModelMatrix(modelMatrix);
        shader.loadColor(color);
        shader.loadAlpha(this.alpha * renderer.getCurrentAlpha()); 

        font.getFontAtlasTexture().bind(0); 
        shader.connectTextureSampler(0);

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void cleanupMesh() {
        if (vaoId != -1) { glDeleteVertexArrays(vaoId); vaoId = -1; }
        if (vboId != -1) { glDeleteBuffers(vboId); vboId = -1; }
        if (eboId != -1) { glDeleteBuffers(eboId); eboId = -1; }
        indexCount = 0;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cleanupMesh();
        LOGGER.trace("Cleaned up TextElement for text: {}", text);
    }
} 