package de.heger.voxelengine.renderer.shader;

import de.heger.voxelengine.core.logging.LoggerFacade;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL43.*;

/**
 * Base class for GLSL shader programs in the engine.
 *
 * <p>It handles the OpenGL shader lifecycle: loading source (from a file or the
 * classpath), compiling vertex and fragment shaders, linking them into a
 * program, and reporting compile/link errors. Once linked it manages a map of
 * named uniform locations and offers typed {@code setUniform} overloads for
 * the common types (ints, floats, vectors, matrices).</p>
 *
 * <p>Concrete shaders such as the block shader and
 * {@link de.heger.voxelengine.renderer.ui.UIShader} extend this and add their
 * own uniform names and convenience setters.</p>
 */
public class ShaderProgram {

    private static final LoggerFacade logger = LoggerFacade.get(ShaderProgram.class);

    protected final int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private int computeShaderId;
    private final Map<String, Integer> uniforms;

    public ShaderProgram() {
        programId = glCreateProgram();
        if (programId == 0) {
            throw new RuntimeException("Could not create Shader program");
        }
        uniforms = new HashMap<>();
        logger.debug("Created shader program with ID: {}", programId);
    }

    public void createVertexShader(String shaderCode) {
        vertexShaderId = createShader(shaderCode, GL_VERTEX_SHADER);
        logger.debug("Created vertex shader with ID: {}", vertexShaderId);
    }

    public void createFragmentShader(String shaderCode) {
        fragmentShaderId = createShader(shaderCode, GL_FRAGMENT_SHADER);
         logger.debug("Created fragment shader with ID: {}", fragmentShaderId);
    }

    /**
     * Creates and attaches a compute shader to this program.
     * Note: Requires OpenGL 4.3 or the ARB_compute_shader extension.
     *
     * @param shaderCode GLSL source code for the compute shader.
     */
    public void createComputeShader(String shaderCode) {
        computeShaderId = createShader(shaderCode, GL_COMPUTE_SHADER);
        logger.debug("Created compute shader with ID: {}", computeShaderId);
    }

    protected int createShader(String shaderCode, int shaderType) {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new RuntimeException("Error creating shader. Type: " + shaderType);
        }

        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            String infoLog = glGetShaderInfoLog(shaderId, 1024);
            logger.error("Error compiling Shader code: {}", infoLog);
            throw new RuntimeException("Error compiling Shader code: " + infoLog);
        }

        glAttachShader(programId, shaderId);

        return shaderId;
    }

    public void link() {
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
             String infoLog = glGetProgramInfoLog(programId, 1024);
             logger.error("Error linking Shader code: {}", infoLog);
            throw new RuntimeException("Error linking Shader code: " + infoLog);
        }

        // Detach shaders after successful link, they are no longer needed
        if (vertexShaderId != 0) {
            glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            glDetachShader(programId, fragmentShaderId);
        }
        if (computeShaderId != 0) {
            glDetachShader(programId, computeShaderId);
        }

        // Validate the program (optional, but good for debugging)
        glValidateProgram(programId);
        if (glGetProgrami(programId, GL_VALIDATE_STATUS) == 0) {
            String infoLog = glGetProgramInfoLog(programId, 1024);
            logger.warn("Warning validating Shader code: {}", infoLog);
            // Don't throw an exception here, as validation issues might not be fatal
        }
        logger.info("Shader program linked successfully (ID: {})", programId);
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void createUniform(String uniformName) {
        int uniformLocation = glGetUniformLocation(programId, uniformName);
        if (uniformLocation < 0) {
             // Don't throw an exception, uniform might not be used or optimized out
            logger.warn("Could not find uniform '{}' in shader program {}", uniformName, programId);
            // Store -1 to indicate it wasn't found, prevent repeated lookups
             uniforms.put(uniformName, -1);
        } else {
            uniforms.put(uniformName, uniformLocation);
            logger.trace("Created uniform '{}' with location {} in shader program {}", uniformName, uniformLocation, programId);
        }
    }

    public int getProgramId() {
        return programId;
    }

     private int getUniformLocation(String uniformName) {
        Integer location = uniforms.get(uniformName);
        if (location == null) {
             // This shouldn't happen if createUniform was called, but as a safeguard
             logger.error("Uniform '{}' has not been created for shader program {}", uniformName, programId);
             throw new RuntimeException("Uniform '" + uniformName + "' has not been created!");
        }
         if (location == -1) {
             // Log a warning if trying to set a uniform that wasn't found during creation
             logger.warn("Attempting to set uniform '{}' which was not found or optimized out in shader program {}.", uniformName, programId);
         }
        return location;
    }


    public void setUniform(String uniformName, org.joml.Matrix4f value) {
        int uniformLocation = getUniformLocation(uniformName);
        if (uniformLocation != -1) {
            // Dump the matrix into a float buffer
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(uniformLocation, false, value.get(stack.mallocFloat(16)));
            }
        }
    }

     public void setUniform(String uniformName, org.joml.Vector3f value) {
        int uniformLocation = getUniformLocation(uniformName);
         if (uniformLocation != -1) {
            glUniform3f(uniformLocation, value.x, value.y, value.z);
         }
    }

    public void setUniform(String uniformName, org.joml.Vector4f value) {
        int uniformLocation = getUniformLocation(uniformName);
        if (uniformLocation != -1) {
            glUniform4f(uniformLocation, value.x, value.y, value.z, value.w);
        }
    }

    public void setUniform(String uniformName, int value) {
         int uniformLocation = getUniformLocation(uniformName);
         if (uniformLocation != -1) {
            glUniform1i(uniformLocation, value);
         }
    }

     public void setUniform(String uniformName, float value) {
         int uniformLocation = getUniformLocation(uniformName);
         if (uniformLocation != -1) {
            glUniform1f(uniformLocation, value);
         }
    }

    public void setUniform(String uniformName, boolean value) {
        int uniformLocation = getUniformLocation(uniformName);
        if (uniformLocation != -1) {
            glUniform1i(uniformLocation, value ? 1 : 0);
        }
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            // Shaders are detached in link(), but we can delete them explicitly if needed
            if (vertexShaderId != 0) glDeleteShader(vertexShaderId);
            if (fragmentShaderId != 0) glDeleteShader(fragmentShaderId);
            if (computeShaderId != 0) glDeleteShader(computeShaderId);
            glDeleteProgram(programId);
            logger.debug("Deleted shader program with ID: {}", programId);
        }
    }

    // --- Static Helper Methods ---

    /**
     * Reads a shader file from the classpath resources.
     * @param resourcePath The path within the resources folder (e.g., "/shaders/default.vert")
     * @return The content of the shader file as a String.
     * @throws IOException If the resource cannot be found or read.
     */
    public static String loadShaderSourceFromResources(String resourcePath) throws IOException {
        logger.debug("Loading shader source from resource: {}", resourcePath);
        try (InputStream inputStream = ShaderProgram.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Shader resource not found: " + resourcePath);
            }
            // It's generally safer to read all bytes and then convert,
            // especially for potentially large files or varying encodings.
            byte[] bytes = inputStream.readAllBytes();
            String source = new String(bytes, StandardCharsets.UTF_8);
             logger.trace("Successfully loaded shader source from {}", resourcePath);
            return source;
        } catch (IOException e) {
            logger.error("Failed to load shader source from resource: {}", resourcePath, e);
            throw e; // Re-throw the exception
        }
    }

     /**
      * Reads a shader file from the file system.
      * @param filePath The absolute path to the shader file.
      * @return The content of the shader file as a String.
      * @throws IOException If the file cannot be found or read.
      */
     public static String loadShaderSourceFromFile(String filePath) throws IOException {
         logger.debug("Loading shader source from file: {}", filePath);
         try {
             String source = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
             logger.trace("Successfully loaded shader source from {}", filePath);
             return source;
         } catch (IOException e) {
             logger.error("Failed to load shader source from file: {}", filePath, e);
             throw e; // Re-throw the exception
         }
     }
}
