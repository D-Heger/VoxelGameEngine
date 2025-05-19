package de.heger.voxelengine.renderer.ui;

import org.joml.Vector2f;

public abstract class UIElement {
    protected Vector2f position;
    protected Vector2f size;
    protected boolean visible;
    protected float alpha; // For transparency
    protected boolean mouseOver; // New: To track mouse hover state
    protected boolean focused; // New: To track focus state

    public UIElement(Vector2f position, Vector2f size) {
        this.position = position;
        this.size = size;
        this.visible = true;
        this.alpha = 1.0f;
        this.mouseOver = false; // Initialize mouseOver state
        this.focused = false; // Initialize focused state
    }

    public Vector2f getPosition() {
        return position;
    }

    public void setPosition(Vector2f position) {
        this.position = position;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public Vector2f getSize() {
        return size;
    }

    public void setSize(Vector2f size) {
        this.size = size;
    }

    public void setSize(float width, float height) {
        this.size.set(width, height);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    /**
     * Checks if the given screen coordinates are within the bounds of this UI element.
     * @param mouseX The x-coordinate of the mouse.
     * @param mouseY The y-coordinate of the mouse.
     * @return true if the mouse is over the element, false otherwise.
     */
    public boolean isMouseOver(float mouseX, float mouseY) {
        if (!visible) {
            return false;
        }
        return mouseX >= position.x && mouseX <= position.x + size.x &&
               mouseY >= position.y && mouseY <= position.y + size.y;
    }

    /**
     * Called when the mouse cursor enters the bounds of this element.
     */
    public void onMouseEnter() {
        // Default implementation: subclasses can override
        this.mouseOver = true;
    }

    /**
     * Called when the mouse cursor leaves the bounds of this element.
     */
    public void onMouseLeave() {
        // Default implementation: subclasses can override
        this.mouseOver = false;
    }

    /**
     * Called when the mouse cursor moves while over this element.
     * @param mouseX Current mouse X position.
     * @param mouseY Current mouse Y position.
     * @param deltaX Change in mouse X position since last frame.
     * @param deltaY Change in mouse Y position since last frame.
     */
    public void onMouseMove(float mouseX, float mouseY, float deltaX, float deltaY) {
        // Default implementation: subclasses can override
    }

    /**
     * Called when a mouse button is pressed down while the cursor is over this element.
     * @param button The GLFW mouse button code.
     * @param mouseX Current mouse X position.
     * @param mouseY Current mouse Y position.
     * @return true if the event was handled by this element, false otherwise.
     */
    public boolean onMouseDown(int button, float mouseX, float mouseY) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }

    /**
     * Called when a mouse button is released while the cursor is over this element,
     * or if this element was the one that received the corresponding mouseDown event.
     * @param button The GLFW mouse button code.
     * @param mouseX Current mouse X position (may be outside if dragged).
     * @param mouseY Current mouse Y position (may be outside if dragged).
     * @return true if the event was handled by this element, false otherwise.
     */
    public boolean onMouseUp(int button, float mouseX, float mouseY) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }

    /**
     * Called when a mouse button is clicked (pressed and released) over this element.
     * This is typically triggered if onMouseDown and onMouseUp occur on this element.
     * @param button The GLFW mouse button code.
     * @param mouseX Current mouse X position at the time of the click (usually up).
     * @param mouseY Current mouse Y position at the time of the click (usually up).
     * @return true if the event was handled by this element, false otherwise.
     */
    public boolean onClick(int button, float mouseX, float mouseY) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }

    /**
     * Called when the mouse scroll wheel is used while the cursor is over this element.
     * @param deltaX The horizontal scroll offset.
     * @param deltaY The vertical scroll offset.
     * @return true if the event was handled by this element, false otherwise.
     */
    public boolean onScroll(float deltaX, float deltaY) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }

    /**
     * Updates the UI element.
     * @param deltaTime The time in seconds since the last update.
     */
    public void update(float deltaTime) {
        // Default implementation does nothing
    }

    /**
     * Renders the UI element.
     * @param renderer The UIRenderer instance to use for rendering.
     */
    public abstract void render(UIRenderer renderer);

    /**
     * Cleans up any resources used by the UI element.
     */
    public void cleanup() {
        // Default implementation does nothing
    }

    // --- Focus Management --- 
    /**
     * Checks if this UI element can receive focus (e.g., for text input).
     * @return true if focusable, false otherwise. Default is false.
     */
    public boolean isFocusable() {
        return false;
    }

    /**
     * @return true if this element currently has focus, false otherwise.
     */
    public boolean hasFocus() {
        return focused;
    }

    /**
     * Called when this element gains focus.
     */
    public void onFocus() {
        this.focused = true;
        // Subclasses can override for specific behavior (e.g., show cursor)
    }

    /**
     * Called when this element loses focus.
     */
    public void onBlur() {
        this.focused = false;
        // Subclasses can override for specific behavior (e.g., hide cursor)
    }

    // --- Keyboard & Character Input --- 
    /**
     * Called when a character is typed while this element has focus.
     * @param character The typed character.
     * @return true if the event was handled, false otherwise.
     */
    public boolean onCharTyped(char character) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }

    /**
     * Called when a key is pressed while this element has focus.
     * Useful for handling special keys like Backspace, Enter, Arrow keys.
     * @param key The GLFW key code.
     * @param mods GLFW modifier bits (Shift, Ctrl, Alt, Super).
     * @return true if the event was handled, false otherwise.
     */
    public boolean onKeyPressed(int key, int mods) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }
    
    /**
     * Called when a key is released while this element has focus.
     * @param key The GLFW key code.
     * @param mods GLFW modifier bits (Shift, Ctrl, Alt, Super).
     * @return true if the event was handled, false otherwise.
     */
    public boolean onKeyReleased(int key, int mods) {
        // Default implementation: subclasses can override
        return false; // Indicates event not consumed
    }
} 