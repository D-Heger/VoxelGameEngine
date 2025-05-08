package de.heger.voxelengine.renderer.shader;

import de.heger.voxelengine.core.logging.LoggerFacade;
import org.junit.jupiter.api.*;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Platform;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_TRUE;

/**
 * Tests for the {@link ShaderProgram} class.
 * Requires an OpenGL context, so GLFW is initialized headlessly.
 */
class ShaderProgramTest {

    private static final LoggerFacade logger = LoggerFacade.get(ShaderProgramTest.class);
    private static long window; // Hidden window handle

    private ShaderProgram shaderProgram;

    @BeforeAll
    static void setupOpenGL() {
        logger.info("Setting up headless OpenGL context for ShaderProgramTest...");
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW for headless operation
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Hidden window
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3); // Request OpenGL 3.3
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        if (Platform.get() == Platform.MACOSX) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE); // Required on Mac
        }

        // Create the hidden window
        window = glfwCreateWindow(100, 100, "ShaderProgramTest Hidden Window", 0, 0);
        if (window == 0) {
            glfwTerminate();
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // IMPORTANT: Initialize OpenGL capabilities AFTER making context current
        GL.createCapabilities();
        logger.info("Headless OpenGL context created successfully.");
        logger.info("OpenGL Version: {}", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
    }

    @AfterAll
    static void tearDownOpenGL() {
        logger.info("Tearing down headless OpenGL context...");
        if (window != 0) {
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        glfwSetErrorCallback(null).free(); // Free the error callback
        logger.info("OpenGL context torn down.");
    }

    @BeforeEach
    void setUp() {
        // Create a new shader program before each test
        shaderProgram = new ShaderProgram();
        assertNotNull(shaderProgram, "ShaderProgram should be created");
    }

    @AfterEach
    void tearDown() {
        // Clean up the shader program after each test
        if (shaderProgram != null) {
            shaderProgram.cleanup();
        }
    }

    @Test
    void testSuccessfulShaderCompilationAndLinking() {
        assertDoesNotThrow(() -> {
            String vertexSource = ShaderProgram.loadShaderSourceFromResources("/shaders/test.vert");
            String fragmentSource = ShaderProgram.loadShaderSourceFromResources("/shaders/test.frag");

            shaderProgram.createVertexShader(vertexSource);
            shaderProgram.createFragmentShader(fragmentSource);
            shaderProgram.link();
        }, "Valid shaders should compile and link without exceptions.");
    }

    @Test
    void testVertexShaderCompilationError() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            String invalidVertexSource = ShaderProgram.loadShaderSourceFromResources("/shaders/invalid_compile.vert");
            shaderProgram.createVertexShader(invalidVertexSource); // Should throw here
        }, "Compiling invalid vertex shader should throw RuntimeException.");

        assertTrue(exception.getMessage().contains("Error compiling Shader code"),
                   "Exception message should indicate a compilation error.");
        logger.info("Successfully caught expected vertex shader compilation error: {}", exception.getMessage());
    }

    @Test
    void testFragmentShaderCompilationError() {
         // Need a valid vertex shader first before compiling fragment shader
         assertDoesNotThrow(() -> {
             String vertexSource = ShaderProgram.loadShaderSourceFromResources("/shaders/test.vert");
             shaderProgram.createVertexShader(vertexSource);
         });

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
             // Assuming a hypothetical invalid_compile.frag exists with a syntax error
             // For now, let's reuse the invalid vertex shader source as fragment source to trigger an error
             String invalidFragmentSource = "#version 330 core\nout vec4 FragColor\n\nvoid main() { FragColor = vec4(1.0, 0.5, 0.2, 1.0) // Missing semicolon }";
             shaderProgram.createFragmentShader(invalidFragmentSource); // Should throw here
        }, "Compiling invalid fragment shader should throw RuntimeException.");

        assertTrue(exception.getMessage().contains("Error compiling Shader code"),
                   "Exception message should indicate a compilation error.");
        logger.info("Successfully caught expected fragment shader compilation error: {}", exception.getMessage());
    }


    @Test
    void testShaderLinkingError() {
        assertDoesNotThrow(() -> {
            // Compile valid vertex shader
            String vertexSource = ShaderProgram.loadShaderSourceFromResources("/shaders/test.vert");
            shaderProgram.createVertexShader(vertexSource);

            // Compile fragment shader with mismatched input/output
            String invalidLinkFragmentSource = ShaderProgram.loadShaderSourceFromResources("/shaders/invalid_link.frag");
            shaderProgram.createFragmentShader(invalidLinkFragmentSource);
        }, "Compilation of individual shaders should succeed.");

        // Linking should fail
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            shaderProgram.link(); // Should throw here
        }, "Linking shaders with mismatched interfaces should throw RuntimeException.");

        assertTrue(exception.getMessage().contains("Error linking Shader code"),
                   "Exception message should indicate a linking error.");
         logger.info("Successfully caught expected shader linking error: {}", exception.getMessage());
    }

     @Test
     void testLoadShaderSourceFromResourcesNotFound() {
         IOException exception = assertThrows(IOException.class, () -> {
             ShaderProgram.loadShaderSourceFromResources("/shaders/non_existent_shader.glsl");
         }, "Loading a non-existent resource should throw IOException.");

         assertTrue(exception.getMessage().contains("Shader resource not found"),
                    "Exception message should indicate resource not found.");
     }
}
