package de.heger.voxelengine.renderer.ui.elements;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIRenderer;
import de.heger.voxelengine.renderer.ui.UIShader;
import de.heger.voxelengine.renderer.ui.font.Font;
import de.heger.voxelengine.renderer.ui.font.FontManager;
import de.heger.voxelengine.renderer.ui.font.GlyphInfo;
import de.heger.voxelengine.renderer.ui.layout.LayoutManager;
import de.heger.voxelengine.renderer.mesh.MeshData;

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

/**
 * A UI element that draws a string of text using a baked bitmap font.
 *
 * <p>Given a {@link de.heger.voxelengine.renderer.ui.font.Font} and a string,
 * it builds a mesh of one textured quad per glyph (positioned using the font's
 * baked metrics) and renders it in screen space at a configurable scale and
 * colour. It is used by labels, button captions, and the debug overlay.</p>
 */
public class TextElement extends UIElement {
    private static final LoggerFacade LOGGER = LoggerFacade.get(TextElement.class);

    private String text;
    private Font font;
    private Vector4f color;
    private float scale;

    private int vaoId = -1, vboId = -1, eboId = -1;
    private int indexCount = 0; // Number of indices

    private final Matrix4f modelMatrix = new Matrix4f();
    
    // Performance optimization: dirty flags to avoid unnecessary rebuilds
    private boolean meshDirty = true;
    private String lastBuiltText = null;
    private Font lastBuiltFont = null;
    private float lastBuiltScale = -1.0f;

    public TextElement(String text, Font font, Vector2f position, Vector4f color) {
        this(text, font, position, color, 1.0f);
    }

    public TextElement(String text, Font font, Vector2f position, Vector4f color, float scale) {
        super(position, new Vector2f(0, 0)); 
        this.text = text;
        this.font = font;
        this.color = color;
        this.scale = scale;
        this.meshDirty = true;
        if (font == null) {
            LOGGER.warn("TextElement created with null font for text: {}", text);
        }
        buildMeshIfNeeded();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        if (this.text == null || !this.text.equals(text)) {
            this.text = text;
            this.meshDirty = true;
            this.needsLayoutUpdate = true; // Text content change affects layout
            if (this.parent != null) {
                this.parent.setNeedsLayoutUpdate(true);
            }
        }
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        if (this.font != font) {
            this.font = font;
            this.meshDirty = true;
            this.needsLayoutUpdate = true; // Font change affects layout
            if (this.parent != null) {
                this.parent.setNeedsLayoutUpdate(true);
            }
            if (font == null) {
                 LOGGER.warn("TextElement font set to null for text: {}", text);
            }
        }
    }

    public Vector4f getColor() {
        return color;
    }

    public void setColor(Vector4f color) {
        this.color = color;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        if (this.scale != scale && scale > 0) {
            this.scale = scale;
            this.meshDirty = true;
            this.needsLayoutUpdate = true; // Scale change affects layout
            if (this.parent != null) {
                this.parent.setNeedsLayoutUpdate(true);
            }
        }
    }
    
    public void buildMeshIfNeeded() {
        // Check if rebuild is actually needed
        if (!meshDirty && 
            java.util.Objects.equals(text, lastBuiltText) &&
            font == lastBuiltFont &&
            scale == lastBuiltScale) {
            return; // No rebuild needed
        }
        
        buildMesh();
        
        // Update tracking variables
        lastBuiltText = text;
        lastBuiltFont = font;
        lastBuiltScale = scale;
        meshDirty = false;
    }

    private void buildMesh() {
        cleanupMesh();
        if (text == null || text.isEmpty() || font == null) {
            return;
        }

        meshDirty = false;
        lastBuiltText = text;
        lastBuiltFont = font;
        lastBuiltScale = scale;

        MeshData meshData = new MeshData(); // Uses BufferPool for its internal buffers

        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBTTAlignedQuad q = STBTTAlignedQuad.mallocStack(stack);
            FloatBuffer x = stack.floats(0f);
            FloatBuffer y = stack.floats(0f);
            
            STBTTBakedChar.Buffer charData = font.getBakedCharData();
            int atlasWidth = font.getAtlasWidth();
            int atlasHeight = font.getAtlasHeight();
            int firstChar = FontManager.DEFAULT_FIRST_CHAR;

            float textWidth = 0;
            float textHeight = font.getLineHeight() * this.scale;

            // Reusable arrays, moved outside the loop
            float[] vertices = new float[16]; // 4 vertices * 4 floats (pos, uv)
            int[] indices = new int[6];
            int vertexOffset = 0;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < firstChar || c >= firstChar + charData.limit()) {
                    c = '?'; // Fallback for unsupported characters
                }

                stbtt_GetBakedQuad(charData, atlasWidth, atlasHeight, c - firstChar, x, y, q, true);

                float x0 = q.x0() * this.scale;
                float y0 = q.y0() * this.scale;
                float x1 = q.x1() * this.scale;
                float y1 = q.y1() * this.scale;

                // Vertex data for the quad
                vertices[0] = x0;  vertices[1] = y0;  vertices[2] = q.s0(); vertices[3] = q.t0(); // Top-left
                vertices[4] = x0;  vertices[5] = y1;  vertices[6] = q.s0(); vertices[7] = q.t1(); // Bottom-left
                vertices[8] = x1;  vertices[9] = y1;  vertices[10] = q.s1(); vertices[11] = q.t1(); // Bottom-right
                vertices[12] = x1; vertices[13] = y0; vertices[14] = q.s1(); vertices[15] = q.t0(); // Top-right

                meshData.addVertices(vertices);

                // Index data for the quad
                indices[0] = vertexOffset;
                indices[1] = vertexOffset + 1;
                indices[2] = vertexOffset + 2;
                indices[3] = vertexOffset;
                indices[4] = vertexOffset + 2;
                indices[5] = vertexOffset + 3;

                meshData.addIndices(indices);
                vertexOffset += 4;
            }

            textWidth = x.get(0) * this.scale;
            // Update the element's size based on the computed text dimensions
            super.setSize(textWidth, textHeight);

            // Now create the VAO from the MeshData's buffers
            FloatBuffer finalVertexBuffer = meshData.getVertexBuffer();
            IntBuffer finalIndexBuffer = meshData.getIndexBuffer();
            indexCount = finalIndexBuffer.remaining();

            if (indexCount == 0) {
                return; // Nothing to render
            }

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, finalVertexBuffer, GL_STATIC_DRAW);

            eboId = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, finalIndexBuffer, GL_STATIC_DRAW);

            // Position attribute
            glVertexAttribPointer(UIShader.ATTRIB_POSITION, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
            glEnableVertexAttribArray(UIShader.ATTRIB_POSITION);
            // Texture coordinate attribute
            glVertexAttribPointer(UIShader.ATTRIB_TEX_COORDS, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
            glEnableVertexAttribArray(UIShader.ATTRIB_TEX_COORDS);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);

        } finally {
            meshData.cleanup(); // IMPORTANT: Release buffers back to the pool
        }
    }

    @Override
    public void render(UIRenderer renderer) {
        // Ensure mesh is up-to-date before rendering
        buildMeshIfNeeded();
        
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
        // Reset tracking variables
        lastBuiltText = null;
        lastBuiltFont = null;
        lastBuiltScale = -1.0f;
        meshDirty = false;
        LOGGER.trace("Cleaned up TextElement for text: {}", text);
    }
} 