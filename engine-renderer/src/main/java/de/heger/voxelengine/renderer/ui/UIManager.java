package de.heger.voxelengine.renderer.ui;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.ui.font.FontManager;
import de.heger.voxelengine.platform.InputManager;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class UIManager {
    private static final LoggerFacade LOGGER = LoggerFacade.get(UIManager.class);

    private FontManager fontManager;
    private UIRenderer uiRenderer;
    private boolean initialized = false;

    private final List<UIElement> elements;
    private UIElement hoveredElement = null;
    private UIElement pressedElement = null;
    private UIElement focusedElement = null;
    private boolean uiHasFocus = false;

    public UIManager() {
        this.elements = new ArrayList<>();
    }

    public void init(Window window) {
        if (initialized) {
            LOGGER.warn("UIManager is already initialized.");
            return;
        }
        try {
            fontManager = new FontManager();
            uiRenderer = new UIRenderer(window);
            initialized = true;
            LOGGER.info("UIManager initialized successfully.");
        } catch (IOException e) {
            LOGGER.error("Failed to initialize UIManager.", e);
            initialized = false;
        }
    }

    public void addElement(UIElement element) {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot add element.");
            return;
        }
        if (element != null && !elements.contains(element)) {
            elements.add(element);
        }
    }

    public void removeElement(UIElement element) {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot remove element.");
            return;
        }
        elements.remove(element);
        if (hoveredElement == element) hoveredElement = null;
        if (pressedElement == element) pressedElement = null;
        if (focusedElement == element) focusedElement = null;
    }

    public void clearElements() {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot clear elements.");
            return;
        }
        elements.clear();
        hoveredElement = null;
        pressedElement = null;
        if (focusedElement != null) {
            focusedElement.onBlur();
            focusedElement = null;
        }
    }
    
    public void setUiHasFocus(boolean uiHasFocus) {
        this.uiHasFocus = uiHasFocus;
        if (!uiHasFocus) {
            if (hoveredElement != null) {
                hoveredElement.onMouseLeave();
                hoveredElement = null;
            }
            pressedElement = null;
            if (focusedElement != null) {
                focusedElement.onBlur();
                focusedElement = null;
            }
        }
    }

    public boolean uiHasFocus() {
        return uiHasFocus;
    }

    public void processInput(InputManager inputManager, Window window) {
        if (!initialized || !uiHasFocus) {
            return;
        }

        float mouseX = (float) inputManager.getMouseX();
        float mouseY = (float) inputManager.getMouseY();
        float deltaMouseX = (float) inputManager.getDeltaMouseX();
        float deltaMouseY = (float) inputManager.getDeltaMouseY();
        float scrollX = (float) inputManager.getScrollDeltaX();
        float scrollY = (float) inputManager.getScrollDeltaY();

        UIElement currentTopElement = null;
        List<UIElement> reversedElements = new ArrayList<>(elements);
        Collections.reverse(reversedElements);

        for (UIElement element : reversedElements) {
            if (element.isVisible() && element.isMouseOver(mouseX, mouseY)) {
                currentTopElement = element;
                break;
            }
        }

        if (currentTopElement != hoveredElement) {
            if (hoveredElement != null) {
                hoveredElement.onMouseLeave();
            }
            hoveredElement = currentTopElement;
            if (hoveredElement != null) {
                hoveredElement.onMouseEnter();
            }
        }

        if (hoveredElement != null && (deltaMouseX != 0 || deltaMouseY != 0)) {
            hoveredElement.onMouseMove(mouseX, mouseY, deltaMouseX, deltaMouseY);
        }

        if (hoveredElement != null && (scrollX != 0 || scrollY != 0)) {
            if (hoveredElement.onScroll(scrollX, scrollY)) {
            }
        }

        for (int button = 0; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
            if (inputManager.isMouseButtonJustPressed(button)) {
                if (hoveredElement != null) {
                    pressedElement = hoveredElement;
                    if (hoveredElement.isFocusable()) {
                        setFocusedElement(hoveredElement);
                    }
                    if (pressedElement.onMouseDown(button, mouseX, mouseY)) {
                        break;
                    }
                } else {
                    setFocusedElement(null);
                }
            }

            if (inputManager.isMouseButtonJustReleased(button)) {
                if (pressedElement != null) {
                    boolean consumed = pressedElement.onMouseUp(button, mouseX, mouseY);
                    if (hoveredElement == pressedElement && pressedElement.isMouseOver(mouseX,mouseY)) {
                         if (pressedElement.onClick(button, mouseX, mouseY)) {
                            consumed = true; 
                         }
                    }
                    pressedElement = null;
                    if (consumed) {
                        break;
                    }
                }
            }
        }
    }

    private void setFocusedElement(UIElement element) {
        if (focusedElement == element) return;

        if (focusedElement != null) {
            focusedElement.onBlur();
        }
        focusedElement = element;
        if (focusedElement != null) {
            focusedElement.onFocus();
            LOGGER.debug("UI Element focused: {}", focusedElement.getClass().getSimpleName());
        }
    }

    public void processKeyboardInput(InputManager inputManager) {
        if (!initialized || !uiHasFocus || focusedElement == null) {
            return;
        }

        Character typedChar;
        while ((typedChar = inputManager.pollTypedCharacter()) != null) {
            if (focusedElement.onCharTyped(typedChar)) {
            }
        }

        for (int key = 0; key <= GLFW.GLFW_KEY_LAST; key++) {
            if (inputManager.isKeyPressed(key)) {
                if (focusedElement.onKeyPressed(key, 0)) {
                }
            }
        }
    }

    public void render() {
        if (!initialized) {
            // LOGGER.trace("UIManager not initialized or nothing to render."); // Can be too spammy
            return;
        }
        uiRenderer.render(elements); // Pass elements to UIRenderer
    }
    
    public void update(float deltaTime) {
        if (!initialized) {
            return;
        }
        // Elements are updated directly by UIManager now
        for (UIElement element : elements) {
            if (element.isVisible()) {
                element.update(deltaTime);
            }
        }
    }
    
    public FontManager getFontManager() {
        if (!initialized) {
            LOGGER.warn("UIManager not initialized. FontManager might be null.");
        }
        return fontManager;
    }
    
    public UIRenderer getUIRenderer() {
        if (!initialized) {
            LOGGER.warn("UIManager not initialized. UIRenderer might be null.");
        }
        return uiRenderer;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void cleanup() {
        if (!initialized) {
            return;
        }
        LOGGER.info("Cleaning up UIManager...");
        if (uiRenderer != null) {
            uiRenderer.cleanup();
        }
        for (UIElement element : elements) {
            element.cleanup();
        }
        elements.clear();
        hoveredElement = null;
        pressedElement = null;

        if (fontManager != null) {
            fontManager.cleanup();
        }
        initialized = false;
    }
} 