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

/**
 * The central hub of the UI toolkit.
 *
 * <p>The {@code UIManager} owns the flat list of top-level
 * {@link UIElement}s that make up the interface, initialises the font and render
 * subsystems, and drives them each frame. It is also the input router: it
 * tracks which element the mouse is over, which is currently pressed, and which
 * holds keyboard focus, dispatching events to the right widget and reporting
 * whether the UI or the game world should consume the current input.</p>
 *
 * <p>To keep per-frame allocation low it caches a reversed view of its element
 * list (so hit-testing can walk front-to-back) and only rebuilds that cache
 * when the element order changes.</p>
 */
public class UIManager {
    private static final LoggerFacade LOGGER = LoggerFacade.get(UIManager.class);

    private FontManager fontManager;
    private UIRenderer uiRenderer;
    private boolean initialized = false;

    private final List<UIElement> elements;
    // Cache reversed elements list to avoid allocations every frame
    private final List<UIElement> reversedElementsCache;
    private boolean elementsOrderDirty = true;
    
    private UIElement hoveredElement = null;
    private UIElement pressedElement = null;
    private UIElement focusedElement = null;
    private boolean uiHasFocus = false;

    public UIManager() {
        this.elements = new ArrayList<>();
        this.reversedElementsCache = new ArrayList<>();
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
            elementsOrderDirty = true; // Mark cache as dirty
        }
    }

    public void removeElement(UIElement element) {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot remove element.");
            return;
        }
        if (elements.remove(element)) {
            elementsOrderDirty = true; // Mark cache as dirty
        }
        
        // Clean up references safely
        if (hoveredElement == element) {
            hoveredElement.onMouseLeave();
            hoveredElement = null;
        }
        if (pressedElement == element) pressedElement = null;
        if (focusedElement == element) {
            focusedElement.onBlur();
            focusedElement = null;
        }
    }

    public void clearElements() {
        if (!initialized) {
            LOGGER.error("UIManager not initialized. Cannot clear elements.");
            return;
        }
        elements.clear();
        reversedElementsCache.clear();
        elementsOrderDirty = true;
        
        // Clean up references
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
    
    private void updateReversedElementsCache() {
        if (elementsOrderDirty) {
            reversedElementsCache.clear();
            reversedElementsCache.addAll(elements);
            Collections.reverse(reversedElementsCache);
            elementsOrderDirty = false;
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

        // Update reversed elements cache if needed
        updateReversedElementsCache();

        UIElement currentTopElement = null;
        for (UIElement element : reversedElementsCache) {
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
                // Event consumed
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
                // Event consumed
            }
        }

        for (int key = 0; key <= GLFW.GLFW_KEY_LAST; key++) {
            if (inputManager.isKeyPressed(key)) {
                if (focusedElement.onKeyPressed(key, 0)) {
                    // Event consumed
                }
            }
        }
    }

    public void render() {
        if (!initialized) {
            return;
        }
        uiRenderer.render(elements);
    }
    
    public void update(float deltaTime) {
        if (!initialized) {
            return;
        }
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
        reversedElementsCache.clear();
        hoveredElement = null;
        pressedElement = null;

        if (fontManager != null) {
            fontManager.cleanup();
        }
        initialized = false;
    }
} 