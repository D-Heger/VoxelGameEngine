package de.heger.voxelengine.platform;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowTest {

    @Disabled("Requires graphical environment - run manually")
    @Test
    void testWindowCreationAndProperties() {
        Window window = new Window(800, 600, "TestWindow", false, false, null);
        assertNotNull(window.getWindowHandle());
        assertEquals(800, window.getWidth());
        assertEquals(600, window.getHeight());
        assertEquals(800f/600f, window.getAspectRatio(), 1e-6f);
        window.cleanup();
    }
}
