package de.heger.voxelengine.renderer.ui;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import static org.lwjgl.opengl.GL20.glBindAttribLocation;

import java.io.IOException;

public class UIShader extends ShaderProgram {
    private static final LoggerFacade LOGGER = LoggerFacade.get(UIShader.class);

    // Uniform names (must match shader file)
    private static final String UNIFORM_PROJECTION_MATRIX = "projection";
    private static final String UNIFORM_MODEL_MATRIX = "model";
    private static final String UNIFORM_COLOR = "uColor";
    private static final String UNIFORM_ALPHA = "uAlpha";
    private static final String UNIFORM_TEXTURE_SAMPLER = "uTexture";

    // Attribute names (must match shader file)
    public static final int ATTRIB_POSITION = 0;
    public static final int ATTRIB_TEX_COORDS = 1;
    public static final String ATTRIB_NAME_POSITION = "aPos";
    public static final String ATTRIB_NAME_TEX_COORDS = "aTexCoords";

    public UIShader() throws IOException {
        super(); // Initialize ShaderProgram (programId etc.)

        try {
            String vertexShaderSource = ShaderProgram.loadShaderSourceFromResources("/shaders/ui.vert");
            String fragmentShaderSource = ShaderProgram.loadShaderSourceFromResources("/shaders/ui.frag");

            super.createVertexShader(vertexShaderSource);
            super.createFragmentShader(fragmentShaderSource);

            // Bind attributes before linking
            // This ensures that aPos is at location 0 and aTexCoords is at location 1
            glBindAttribLocation(this.programId, ATTRIB_POSITION, ATTRIB_NAME_POSITION); // programId needs to be accessible
            glBindAttribLocation(this.programId, ATTRIB_TEX_COORDS, ATTRIB_NAME_TEX_COORDS);

            super.link();

            // Create uniforms after linking
            super.createUniform(UNIFORM_PROJECTION_MATRIX);
            super.createUniform(UNIFORM_MODEL_MATRIX);
            super.createUniform(UNIFORM_COLOR);
            super.createUniform(UNIFORM_ALPHA);
            super.createUniform(UNIFORM_TEXTURE_SAMPLER);

            LOGGER.info("UIShader created and linked successfully.");

        } catch (IOException e) {
            LOGGER.error("Failed to load UI shader files.", e);
            throw e;
        } catch (RuntimeException e) {
            LOGGER.error("RuntimeException during UIShader setup (compilation/linking).", e);
            // Consider cleaning up if partially created, though ShaderProgram.cleanup() handles programId
            super.cleanup(); // Clean up partially created shader program if linking failed
            throw e;
        }
    }

    public void loadProjectionMatrix(Matrix4f matrix) {
        super.setUniform(UNIFORM_PROJECTION_MATRIX, matrix);
    }

    public void loadModelMatrix(Matrix4f matrix) {
        super.setUniform(UNIFORM_MODEL_MATRIX, matrix);
    }

    public void loadColor(Vector4f color) {
        super.setUniform(UNIFORM_COLOR, color);
    }

    public void loadAlpha(float alpha) {
        super.setUniform(UNIFORM_ALPHA, alpha);
    }

    public void connectTextureSampler(int textureUnit) {
        super.setUniform(UNIFORM_TEXTURE_SAMPLER, textureUnit);
    }
} 