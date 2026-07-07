package de.heger.voxelengine.renderer.shader;

import de.heger.voxelengine.core.logging.LoggerFacade;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.*;

/**
 * A wrapper around an OpenGL Uniform Buffer Object (UBO).
 *
 * <p>Some data is shared by many shaders and rarely changes within a frame, for
 * example the camera's view/projection matrices or scene lighting parameters.
 * Instead of setting those uniforms on every shader individually, they are
 * packed once into a UBO and bound to a fixed binding point that shaders read
 * from. This class owns that buffer: it allocates GPU storage of a given size,
 * exposes the backing {@link java.nio.ByteBuffer} for filling in data, uploads
 * it, and cleans up the GL resource when done.</p>
 */
public class UniformBuffer {
    private static final LoggerFacade logger = LoggerFacade.get(UniformBuffer.class);

    private final int uboId;
    private final int size;
    private final int bindingPoint;
    private ByteBuffer buffer;

    public UniformBuffer(int size, int bindingPoint) {
        this.size = size;
        this.bindingPoint = bindingPoint;

        this.uboId = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, uboId);
        glBufferData(GL_UNIFORM_BUFFER, size, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, uboId);
        
        // Allocate a direct ByteBuffer for updating the UBO
        this.buffer = MemoryUtil.memAlloc(size);

        logger.debug("Created UBO with ID {}, size {}, and binding point {}", uboId, size, bindingPoint);
    }

    public void bindToShader(ShaderProgram shader, String blockName) {
        int blockIndex = glGetUniformBlockIndex(shader.getProgramId(), blockName);
        if (blockIndex == GL_INVALID_INDEX) {
            logger.warn("Uniform block '{}' not found in shader program {}", blockName, shader.getProgramId());
            return;
        }
        glUniformBlockBinding(shader.getProgramId(), blockIndex, bindingPoint);
        logger.trace("Bound uniform block '{}' to binding point {}", blockName, bindingPoint);
    }

    public void updateData(ByteBuffer data) {
        if (data.remaining() > size) {
            throw new IllegalArgumentException("Data size (" + data.remaining() + ") exceeds UBO capacity (" + size + ")");
        }
        data.rewind();
        
        glBindBuffer(GL_UNIFORM_BUFFER, uboId);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, data);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }
    
    public ByteBuffer getBuffer() {
        buffer.clear();
        return buffer;
    }

    public void cleanup() {
        if (uboId != 0) {
            glDeleteBuffers(uboId);
            logger.debug("Deleted UBO with ID {}", uboId);
        }
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
            buffer = null;
        }
    }

    public int getId() {
        return uboId;
    }
} 