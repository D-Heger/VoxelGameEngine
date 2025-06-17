package de.heger.voxelengine.renderer.debug;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Responsible for rendering a simple gray semi-transparent outline around a single block.
 */
public class BlockOutlineRenderer {

    private static final LoggerFacade logger = LoggerFacade.get(BlockOutlineRenderer.class);
    private static final float DEFAULT_LINE_WIDTH = 4.0f;

    private ShaderProgram shader;
    private int vao;
    private int vbo;
    private float lineWidth = DEFAULT_LINE_WIDTH;

    public void init() throws Exception {
        shader = new ShaderProgram();
        shader.createVertexShader(ShaderProgram.loadShaderSourceFromResources("/shaders/outline.vert"));
        shader.createFragmentShader(ShaderProgram.loadShaderSourceFromResources("/shaders/outline.frag"));
        shader.link();
        shader.createUniform("mvp");
        shader.createUniform("color");

        // Create cube line mesh (unit cube 0..1)
        float[] verts = createCubeLineVertices();
        FloatBuffer buffer = memAllocFloat(verts.length);
        buffer.put(verts).flip();

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        // Unbind
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        memFree(buffer);

        logger.info("BlockOutlineRenderer initialized.");
    }

    private float[] createCubeLineVertices() {
        // Slightly inset cube (epsilon) so that the outer half of the line width lies exactly
        // on the block faces and the inner half expands inward.  Epsilon ~= one screen pixel in
        // world units is tricky to compute offline, so we choose a very small value that avoids
        // noticeable gaps while ensuring the outline does not bleed outside the block.
        final float e = 0.002f; // inset amount in block space (2 mm of a 1 m block)
        return new float[]{
                // Bottom square
                e,e,e, 1-e,e,e,
                1-e,e,e, 1-e,e,1-e,
                1-e,e,1-e, e,e,1-e,
                e,e,1-e, e,e,e,
                // Top square
                e,1-e,e, 1-e,1-e,e,
                1-e,1-e,e, 1-e,1-e,1-e,
                1-e,1-e,1-e, e,1-e,1-e,
                e,1-e,1-e, e,1-e,e,
                // Vertical lines
                e,e,e, e,1-e,e,
                1-e,e,e, 1-e,1-e,e,
                1-e,e,1-e, 1-e,1-e,1-e,
                e,e,1-e, e,1-e,1-e
        };
    }

    public void render(Matrix4f mvpMatrix) {
        if (shader == null) return;

        shader.bind();
        shader.setUniform("mvp", mvpMatrix);
        shader.setUniform("color", new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1f));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST); // Ensure outline always appears on top

        glLineWidth(lineWidth);

        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);

        glLineWidth(1.0f);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        shader.unbind();
    }

    public void cleanup() {
        if (shader != null) shader.cleanup();
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
    }

    /**
     * Allows external code to adjust outline thickness at runtime.
     */
    public void setLineWidth(float px) {
        this.lineWidth = Math.max(1.0f, px);
    }
} 