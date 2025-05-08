package de.heger.voxelengine.launcher;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Placeholder tests for GameLoop; requires environment with OpenGL.
 */
class GameLoopTest {

    @Disabled("Requires graphical environment and GLFW initialization - run manually")
    @Test
    void testEscapeStopsLoop() {
        GameLoop loop = new GameLoop("TestGameLoop", 100, 100, false, false);
        // Simulate pressing escape: in a real test, use InputManager mock or GLFW.glfwSetKeyCallback
        // Here we simply exit immediately by closing the window
        loop.run(); // Should exit cleanly without hanging
    }
}
