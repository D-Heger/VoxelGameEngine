package de.heger.voxelengine.platform;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWCharCallback;

import de.heger.voxelengine.core.logging.LoggerFacade;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Basic input manager using GLFW callbacks to track keyboard and mouse state.
 */
public class InputManager {

    private static final LoggerFacade LOGGER = LoggerFacade.get(InputManager.class);
    private final boolean[] keyPressed = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private final boolean[] mouseButtonPressed = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];
    private double mouseX;
    private double mouseY;
    private double lastMouseX;
    private double lastMouseY;
    private double deltaMouseX;
    private double deltaMouseY;
    private boolean firstMouse = true; // Flag to handle initial mouse position

    // New fields for per-frame button state and scroll
    private final boolean[] mouseJustPressed = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseJustReleased = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];
    private double scrollDeltaX;
    private double scrollDeltaY;

    // New: For character input
    private final Queue<Character> typedCharacters = new ConcurrentLinkedQueue<>();

    public InputManager(long windowHandle) {
        // Key callback
        GLFW.glfwSetKeyCallback(windowHandle, new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key >= 0 && key < keyPressed.length) {
                    keyPressed[key] = (action != GLFW.GLFW_RELEASE);
                }
            }
        });
        // Mouse position callback
        GLFW.glfwSetCursorPosCallback(windowHandle, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                if (firstMouse) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    firstMouse = false;
                }

                deltaMouseX = xpos - lastMouseX;
                // Reversed since y-coordinates go from bottom to top in OpenGL
                deltaMouseY = lastMouseY - ypos;

                lastMouseX = xpos;
                lastMouseY = ypos;

                // Update absolute position as well
                mouseX = xpos;
                mouseY = ypos;
            }
        });
        // Mouse button callback
        GLFW.glfwSetMouseButtonCallback(windowHandle, new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button >= 0 && button < mouseButtonPressed.length) {
                    boolean previousState = mouseButtonPressed[button];
                    mouseButtonPressed[button] = (action != GLFW.GLFW_RELEASE);

                    if (!previousState && mouseButtonPressed[button]) { // Just pressed
                        mouseJustPressed[button] = true;
                    } else if (previousState && !mouseButtonPressed[button]) { // Just released
                        mouseJustReleased[button] = true;
                    }
                }
            }
        });

        // Scroll callback - New
        GLFW.glfwSetScrollCallback(windowHandle, new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                scrollDeltaX += xoffset;
                scrollDeltaY += yoffset;
            }
        });

        // Character callback - New
        GLFW.glfwSetCharCallback(windowHandle, new GLFWCharCallback() {
            @Override
            public void invoke(long window, int codepoint) {
                typedCharacters.offer((char) codepoint);
            }
        });
    }

    /**
     * Updates the input state, should be called once per frame before processing input.
     * Resets mouse deltas.
     */
    public void update() {
        // Reset deltas at the start of the frame if they haven't been updated by a callback
        // If a callback happened between the last update() and now, the delta is preserved.
        // If no callback happened, delta becomes 0.
        // This prevents using stale deltas if the mouse didn't move.
        // Note: This simple reset might not be ideal if update() frequency differs
        // significantly from callback frequency. A more robust approach might involve
        // accumulating deltas or using timestamps. For now, this is sufficient.
        deltaMouseX = 0;
        deltaMouseY = 0;

        // Reset per-frame mouse button states and scroll
        for (int i = 0; i < mouseJustPressed.length; i++) {
            mouseJustPressed[i] = false;
        }
        for (int i = 0; i < mouseJustReleased.length; i++) {
            mouseJustReleased[i] = false;
        }
        scrollDeltaX = 0;
        scrollDeltaY = 0;

        // Do not clear typedCharacters here, UIManager will poll it.

        // Poll events *after* resetting deltas, so new callbacks can set them for this frame
        GLFW.glfwPollEvents();
    }


    /**
     * Check if a specific key is currently pressed.
     * @param key GLFW key code
     * @return true if pressed, false otherwise
     */
    public boolean isKeyPressed(int key) {
        return key >= 0 && key < keyPressed.length && keyPressed[key];
    }

    /**
     * Check if a specific mouse button is pressed.
     * @param button GLFW mouse button code
     * @return true if pressed, false otherwise
     */
    public boolean isMouseButtonPressed(int button) {
        return button >= 0 && button < mouseButtonPressed.length && mouseButtonPressed[button];
    }

    /**
     * Check if a specific mouse button was just pressed in this frame.
     * @param button GLFW mouse button code
     * @return true if just pressed, false otherwise
     */
    public boolean isMouseButtonJustPressed(int button) {
        return button >= 0 && button < mouseJustPressed.length && mouseJustPressed[button];
    }

    /**
     * Check if a specific mouse button was just released in this frame.
     * @param button GLFW mouse button code
     * @return true if just released, false otherwise
     */
    public boolean isMouseButtonJustReleased(int button) {
        return button >= 0 && button < mouseJustReleased.length && mouseJustReleased[button];
    }

    /**
     * Returns the current mouse X position within the window.
     */
    public double getMouseX() {
        return mouseX;
    }

    /**
     * Returns the current mouse Y position within the window.
     */
    public double getMouseY() {
        return mouseY;
    }

    /**
     * Returns the change in mouse X position since the last frame.
     */
    public double getDeltaMouseX() {
        return deltaMouseX;
    }

    /**
     * Returns the change in mouse Y position since the last frame.
     */
    public double getDeltaMouseY() {
        return deltaMouseY;
    }

    /**
     * Returns the change in mouse scroll X offset since the last frame.
     */
    public double getScrollDeltaX() {
        return scrollDeltaX;
    }

    /**
     * Returns the change in mouse scroll Y offset since the last frame.
     */
    public double getScrollDeltaY() {
        return scrollDeltaY;
    }

    /**
     * Polls the next typed character from the queue.
     * @return The character, or null if the queue is empty.
     */
    public Character pollTypedCharacter() {
        return typedCharacters.poll();
    }

    /**
     * Clears all currently queued typed characters.
     */
    public void clearTypedCharacters() {
        typedCharacters.clear();
    }

    /**
     * Cleans up the input manager by removing GLFW callbacks.
     * Should be called before the window is destroyed.
     */
    public void cleanup(long windowHandle) {
        LOGGER.debug("Cleaning up InputManager callbacks...");
        GLFW.glfwSetKeyCallback(windowHandle, null);
        GLFW.glfwSetCursorPosCallback(windowHandle, null);
        GLFW.glfwSetMouseButtonCallback(windowHandle, null);
        GLFW.glfwSetScrollCallback(windowHandle, null);
        GLFW.glfwSetCharCallback(windowHandle, null);
        LOGGER.debug("InputManager callbacks removed.");
    }
}
