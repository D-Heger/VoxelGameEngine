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
import java.util.ArrayList;
import java.util.List;

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
    private boolean fullscreen;
    private float aspectRatio;
    private InputManager inputManager;
    
    // OPTIMIZATION: Use single callback field when only one callback, lazy-initialize list
    private FramebufferSizeCallback singleFramebufferCallback;
    private List<FramebufferSizeCallback> framebufferSizeCallbacks;

    // For restoring windowed mode
    private int windowedX, windowedY, windowedWidth, windowedHeight;

    // Functional interface for callbacks
    @FunctionalInterface
    public interface FramebufferSizeCallback {
        void invoke(long windowHandle, int width, int height);
    }

    public Window(int width, int height, String title, boolean vsync, boolean fullscreen, String iconResourcePath) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.vsyncEnabled = vsync;
        this.fullscreen = fullscreen;

        initGlfw();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE); // Required for macOS
        glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE); // Enable OpenGL debug context
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
        setVsync(vsync);

        // IMPORTANT: Create OpenGL capabilities AFTER making context current
        GL.createCapabilities();
        LOGGER.info("OpenGL Capabilities created.");
        // Log OpenGL version
        LOGGER.info("OpenGL Version: {}", glGetString(GL_VERSION));


        // Set initial viewport
        glViewport(0, 0, width, height);
        this.aspectRatio = (float) width / height;
        this.inputManager = new InputManager(windowHandle);

        // Store initial windowed mode details or sensible defaults if starting fullscreen
        if (this.fullscreen) {
            GLFWVidMode primary = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (primary != null) {
                this.windowedWidth = primary.width() / 2;
                this.windowedHeight = primary.height() / 2;
                this.windowedX = (primary.width() - this.windowedWidth) / 2;
                this.windowedY = (primary.height() - this.windowedHeight) / 2;
            } else { 
                this.windowedWidth = 1280;
                this.windowedHeight = 720;
                this.windowedX = 50;
                this.windowedY = 50;
            }
        } else {
            try (MemoryStack stack = stackPush()) {
                IntBuffer currentX = stack.mallocInt(1);
                IntBuffer currentY = stack.mallocInt(1);
                glfwGetWindowPos(windowHandle, currentX, currentY);
                this.windowedX = currentX.get(0);
                this.windowedY = currentY.get(0);
            }
            this.windowedWidth = this.width;
            this.windowedHeight = this.height;
        }

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
        glfwSetFramebufferSizeCallback(windowHandle, (windowHandle, w, h) -> {
            if (w > 0 && h > 0) {
                this.width = w;
                this.height = h;
                this.aspectRatio = (float) w / h;
                glViewport(0, 0, w, h);
                LOGGER.debug("Window resized to {}x{}, aspect ratio: {}", w, h, this.aspectRatio);
                
                // OPTIMIZATION: Notify callbacks efficiently
                if (singleFramebufferCallback != null) {
                    singleFramebufferCallback.invoke(windowHandle, w, h);
                } else if (framebufferSizeCallbacks != null) {
                    for (FramebufferSizeCallback callback : framebufferSizeCallbacks) {
                        callback.invoke(windowHandle, w, h);
                    }
                }
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

            GLFWImage.Buffer imageBuffer = GLFWImage.malloc(1, stack);
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

    public void addFramebufferSizeCallback(FramebufferSizeCallback callback) {
        if (callback == null) {
            return;
        }
        
        // OPTIMIZATION: Use single callback field for common case of one callback
        if (singleFramebufferCallback == null && framebufferSizeCallbacks == null) {
            singleFramebufferCallback = callback;
        } else if (singleFramebufferCallback != null && framebufferSizeCallbacks == null) {
            // Move from single to multiple callbacks
            framebufferSizeCallbacks = new ArrayList<>();
            framebufferSizeCallbacks.add(singleFramebufferCallback);
            framebufferSizeCallbacks.add(callback);
            singleFramebufferCallback = null;
        } else if (framebufferSizeCallbacks != null && !framebufferSizeCallbacks.contains(callback)) {
            framebufferSizeCallbacks.add(callback);
        }
    }

    public void removeFramebufferSizeCallback(FramebufferSizeCallback callback) {
        if (callback == null) {
            return;
        }
        
        if (singleFramebufferCallback == callback) {
            singleFramebufferCallback = null;
        } else if (framebufferSizeCallbacks != null) {
            framebufferSizeCallbacks.remove(callback);
            // OPTIMIZATION: Downgrade to single callback if only one remains
            if (framebufferSizeCallbacks.size() == 1) {
                singleFramebufferCallback = framebufferSizeCallbacks.get(0);
                framebufferSizeCallbacks = null;
            } else if (framebufferSizeCallbacks.isEmpty()) {
                framebufferSizeCallbacks = null;
            }
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

    /**
     * Sets the title of the window.
     * @param newTitle The new title for the window.
     */
    public void setTitle(String newTitle) {
        if (newTitle != null && !newTitle.equals(this.title)) {
            this.title = newTitle;
            if (windowHandle != NULL) { // Ensure window handle is valid
                GLFW.glfwSetWindowTitle(windowHandle, this.title);
                LOGGER.trace("Window title set to: {}", this.title);
            }
        }
    }

    public void setVsync(boolean vsync) {
        this.vsyncEnabled = vsync;
        glfwSwapInterval(vsync ? 1 : 0);
    }

    public boolean isFullscreen() {
        return this.fullscreen;
    }

    public void setFullscreen(boolean desiredFullscreen) {
        if (this.fullscreen == desiredFullscreen) {
            return; // No change
        }

        if (desiredFullscreen) {
            // Switching from Windowed to Fullscreen
            // Save current window position and size (which are the current windowed mode's)
            try (MemoryStack stack = stackPush()) {
                IntBuffer xpos = stack.mallocInt(1);
                IntBuffer ypos = stack.mallocInt(1);
                glfwGetWindowPos(windowHandle, xpos, ypos);
                this.windowedX = xpos.get(0);
                this.windowedY = ypos.get(0);
            }
            this.windowedWidth = this.width; // Current width/height are windowed dimensions
            this.windowedHeight = this.height;

            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vidmode = glfwGetVideoMode(monitor);
            if (vidmode != null) {
                // Go fullscreen with current this.width, this.height.
                // The subsequent call to setSize() from SettingsMenu will finalize the fullscreen resolution.
                glfwSetWindowMonitor(windowHandle, monitor, 0, 0, this.width, this.height, vidmode.refreshRate());
                this.fullscreen = true;
                LOGGER.info("Switched to fullscreen. Resolution to be set by setSize(): {}x{}", this.width, this.height);
            } else {
                LOGGER.error("Failed to get video mode for primary monitor. Cannot switch to fullscreen.");
                return; // Failed to switch
            }
        } else {
            // Switching from Fullscreen to Windowed
            // Restore to previously stored windowedWidth/Height at windowedX/Y.
            // The subsequent setSize() call will set this.width/height to the new target windowed resolution.
            glfwSetWindowMonitor(windowHandle, NULL, this.windowedX, this.windowedY, this.windowedWidth, this.windowedHeight, 0);
            this.fullscreen = false;
            // Update internal width/height to reflect the size we just restored to, before setSize updates them again.
            this.width = this.windowedWidth;
            this.height = this.windowedHeight;
            this.aspectRatio = (float)this.width / this.height;
            LOGGER.info("Switched to windowed mode with previous size: {}x{} at ({},{}), target size via setSize()", this.windowedWidth, this.windowedHeight, this.windowedX, this.windowedY);
        }
        setVsync(this.vsyncEnabled); // Re-apply VSync as monitor change can affect it
    }

    public void setSize(int newWidth, int newHeight) {
        boolean dimensionsActuallyChanged = (this.width != newWidth || this.height != newHeight);

        // Update internal state to the new desired dimensions immediately
        this.width = newWidth;
        this.height = newHeight;
        this.aspectRatio = (float)newWidth / newHeight;

        if (this.fullscreen) {
            // If fullscreen, and dimensions changed, update monitor settings to new resolution
            if (dimensionsActuallyChanged) {
                long monitor = glfwGetPrimaryMonitor();
                GLFWVidMode vidmode = glfwGetVideoMode(monitor);
                if (vidmode != null) {
                    glfwSetWindowMonitor(windowHandle, monitor, 0, 0, this.width, this.height, vidmode.refreshRate());
                    LOGGER.info("Fullscreen resolution changed to: {}x{}", this.width, this.height);
                } else {
                    LOGGER.error("Failed to get video mode for primary monitor. Cannot change fullscreen resolution.");
                }
            }
        } else {
            // If windowed, set window size. glfwSetWindowSize will trigger the framebuffer callback.
            glfwSetWindowSize(windowHandle, this.width, this.height);
            LOGGER.info("Window size requested: {}x{}. Framebuffer callback will confirm actual new size.", this.width, this.height);
        }
        // VSync is typically handled by setFullscreen when monitor changes, or persists in windowed mode.
        // Re-applying here might be redundant but safe. setVsync itself checks current state.
        // setVsync(this.vsyncEnabled); 
    }
}
