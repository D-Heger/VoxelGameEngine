package de.heger.voxelengine.platform;

import de.heger.voxelengine.core.logging.LoggerFacade;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.stb.STBImage;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {

    private static final LoggerFacade LOGGER = LoggerFacade.get(Window.class);
    private static boolean glfwInitialized = false;
    private static GLFWErrorCallback errorCallback;

    private long windowHandle;
    private int width;
    private int height;
    private String title;
    private boolean vsyncEnabled;
    private float aspectRatio;
    private InputManager inputManager;

    public Window(int width, int height, String title, boolean vsync, boolean fullscreen, String iconResourcePath) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.vsyncEnabled = vsync;

        initGlfw();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE); // Required for macOS
        glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_TRUE);
        glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
        glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);

        // Create the window (fullscreen if requested)
        long monitorHandle = fullscreen ? glfwGetPrimaryMonitor() : NULL;
        windowHandle = glfwCreateWindow(width, height, title, monitorHandle, NULL);
        if (windowHandle == NULL) {
            LOGGER.error("Failed to create the GLFW window");
            throw new RuntimeException("Failed to create the GLFW window");
        }
        LOGGER.info("GLFW Window created successfully (Handle: {})", windowHandle);

        setupCallbacks();

        // Center the window
        try (MemoryStack stack = stackPush()) {
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vidmode = glfwGetVideoMode(monitor);
            if (vidmode != null) {
                glfwSetWindowPos(
                        windowHandle,
                        (vidmode.width() - width) / 2,
                        (vidmode.height() - height) / 2
                );
            } else {
                 LOGGER.warn("Could not get video mode for primary monitor to center window.");
            }
        }

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(vsync ? 1 : 0);

        // IMPORTANT: Create OpenGL capabilities AFTER making context current
        GL.createCapabilities();
        LOGGER.info("OpenGL Capabilities created.");
        // Log OpenGL version
        LOGGER.info("OpenGL Version: {}", glGetString(GL_VERSION));


        // Set initial viewport
        glViewport(0, 0, width, height);
        this.aspectRatio = (float) width / height;
        this.inputManager = new InputManager(windowHandle);

        // Set window icon
        if (iconResourcePath != null && !iconResourcePath.isEmpty()) {
            setWindowIcon(iconResourcePath);
        }

        // Make the window visible
        glfwShowWindow(windowHandle);
        LOGGER.info("Window is now visible.");
    }

    private static synchronized void initGlfw() {
        if (glfwInitialized) {
            return;
        }
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        // Store the callback to free it later.
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(errorCallback);
        LOGGER.info("Setting up GLFW Error Callback.");

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            LOGGER.error("Unable to initialize GLFW");
            errorCallback.free();
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwInitialized = true;
        LOGGER.info("GLFW initialized successfully.");
    }

    private void setupCallbacks() {
        // Setup resize callback
        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
            if (w > 0 && h > 0) {
                this.width = w;
                this.height = h;
                this.aspectRatio = (float) w / h;
                glViewport(0, 0, w, h);
                LOGGER.debug("Window resized to {}x{}", w, h);
                // TODO: Notify camera or renderer about aspect ratio change if needed
            }
        });

        // Setup close callback
        glfwSetWindowCloseCallback(windowHandle, window -> {
            LOGGER.info("Window close requested.");
            // The loop condition `!window.shouldClose()` will handle this
        });

        // Input callbacks are set up by InputManager
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    public void update() {
        // Poll for window events. The key callback above will only be
        // invoked during this call.
        glfwPollEvents();
    }

    public void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    public void cleanup() {
        LOGGER.info("Cleaning up window resources (Handle: {})...", windowHandle);
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        LOGGER.debug("GLFW window destroyed.");
        // Terminate GLFW and free the error callback
        // This should ideally only happen once when the application exits,
        // potentially managed by the main application class or launcher.
        // For now, we do it here, assuming one main window.
        terminateGlfw(); // terminateGlfw will handle freeing the global callback
    }

    private static synchronized void terminateGlfw() {
        if (!glfwInitialized) {
            return;
        }
        LOGGER.info("Terminating GLFW...");
        glfwTerminate();
        // Free the error callback *after* terminating GLFW
        if (errorCallback != null) {
            glfwSetErrorCallback(null); // Deregister callback *before* freeing
            errorCallback.free();
            errorCallback = null; // Avoid dangling reference
            LOGGER.debug("Freed global GLFW error callback.");
        }
        glfwInitialized = false;
        LOGGER.info("GLFW terminated.");
    }

    private ByteBuffer loadIconResource(String path) throws IOException {
        InputStream stream = getClass().getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Icon resource not found: " + path);
        }
        byte[] bytes = stream.readAllBytes();
        stream.close();
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }

    private void setWindowIcon(String iconResourcePath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer iconByteBuffer = loadIconResource(iconResourcePath);
            ByteBuffer imageData = STBImage.stbi_load_from_memory(iconByteBuffer, w, h, comp, 4); // Request RGBA

            if (imageData == null) {
                LOGGER.warn("Failed to load window icon '{}': {}", iconResourcePath, STBImage.stbi_failure_reason());
                return;
            }

            GLFWImage.Buffer imageBuffer = GLFWImage.mallocStack(1, stack);
            GLFWImage glfwImage = imageBuffer.get(0); // Get the GLFWImage struct from the buffer
            glfwImage.width(w.get(0));
            glfwImage.height(h.get(0));
            glfwImage.pixels(imageData);

            glfwSetWindowIcon(windowHandle, imageBuffer);

            STBImage.stbi_image_free(imageData); // GLFW copies the data, so we can free it.
            LOGGER.info("Window icon set from resource: {}", iconResourcePath);

        } catch (IOException e) {
            LOGGER.warn("Could not load window icon resource '{}': {}", iconResourcePath, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error setting window icon from resource '{}':", iconResourcePath, e);
        }
    }

    // --- Getters ---
    public long getWindowHandle() {
        return windowHandle;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public InputManager getInputManager() {
        return inputManager;
    }
}
