package de.heger.voxelengine.renderer.ui;

import org.joml.Vector2f;
import de.heger.voxelengine.renderer.ui.layout.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class UIElement {
    protected Vector2f position;
    protected Vector2f size;
    protected boolean visible;
    protected float alpha; // For transparency
    protected boolean mouseOver; // To track mouse hover state
    protected boolean focused; // To track focus state
    
    // Performance optimization: track if bounds have changed
    private boolean boundsDirty = true;
    private float cachedLeft, cachedRight, cachedTop, cachedBottom;

    // --- New properties from UI-P1-T1.1 ---
    protected UIElement parent = null;
    protected List<UIElement> children = new ArrayList<>();
    protected Vector2f anchorPoint = new Vector2f(0.0f, 0.0f); // Default: Top-left
    protected PositioningMode positioningMode = PositioningMode.ABSOLUTE;
    protected Insets margin = new Insets();
    protected Insets padding = new Insets();
    protected Vector2f minSize = new Vector2f(0, 0);
    protected Vector2f maxSize = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
    protected Vector2f preferredSize = new Vector2f(-1, -1); // -1 means not set/calculate
    protected LayoutManager layout = null;
    protected boolean needsLayoutUpdate = true;
    protected boolean needsRenderUpdate = true;
    // --- End of new properties ---

    // --- New constructor ---
    public UIElement() {
        this.position = new Vector2f(0,0);
        this.size = new Vector2f(0,0);
        this.visible = true;
        this.alpha = 1.0f;
        this.mouseOver = false;
        this.focused = false;
        this.boundsDirty = true;
        this.needsLayoutUpdate = true;
        this.needsRenderUpdate = true;
        // Initialize new fields
        this.children = new ArrayList<>();
        this.anchorPoint = new Vector2f(0.0f, 0.0f);
        this.positioningMode = PositioningMode.ABSOLUTE;
        this.margin = new Insets();
        this.padding = new Insets();
        this.minSize = new Vector2f(0, 0);
        this.maxSize = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        this.preferredSize = new Vector2f(-1, -1);
    }
    // --- End of new constructor ---

    public UIElement(Vector2f position, Vector2f size) {
        this.position = new Vector2f(position); // Defensive copy
        this.size = new Vector2f(size); // Defensive copy
        this.visible = true;
        this.alpha = 1.0f;
        this.mouseOver = false;
        this.focused = false;
        this.boundsDirty = true;
    }

    public Vector2f getPosition() {
        return new Vector2f(position); // Return defensive copy
    }

    public void setPosition(Vector2f position) {
        if (!this.position.equals(position)) {
            this.position.set(position);
            this.boundsDirty = true;
        }
    }

    public void setPosition(float x, float y) {
        if (this.position.x != x || this.position.y != y) {
            this.position.set(x, y);
            this.boundsDirty = true;
        }
    }

    public Vector2f getSize() {
        return new Vector2f(size); // Return defensive copy
    }

    public void setSize(Vector2f size) {
        if (!this.size.equals(size)) {
            this.size.set(size);
            this.boundsDirty = true;
        }
    }

    public void setSize(float width, float height) {
        if (this.size.x != width || this.size.y != height) {
            this.size.set(width, height);
            this.boundsDirty = true;
            this.needsLayoutUpdate = true;
            this.needsRenderUpdate = true;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        this.needsRenderUpdate = true;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        this.needsRenderUpdate = true;
    }

    /**
     * Updates cached bounds if they're dirty. This optimization avoids
     * recalculating bounds on every mouse over check.
     */
    private void updateCachedBounds() {
        if (boundsDirty) {
            cachedLeft = position.x;
            cachedRight = position.x + size.x;
            cachedTop = position.y;
            cachedBottom = position.y + size.y;
            boundsDirty = false;
        }
    }

    /**
     * Checks if the given screen coordinates are within the bounds of this UI element.
     * Optimized version that caches bounds calculations.
     * @param mouseX The x-coordinate of the mouse.
     * @param mouseY The y-coordinate of the mouse.
     * @return true if the mouse is over the element, false otherwise.
     */
    public boolean isMouseOver(float mouseX, float mouseY) {
        if (!visible) {
            return false;
        }
        updateCachedBounds();
        return mouseX >= cachedLeft && mouseX <= cachedRight &&
               mouseY >= cachedTop && mouseY <= cachedBottom;
    }

    /**
     * Called when the mouse cursor enters the bounds of this element.
     */
    public void onMouseEnter() {
        this.mouseOver = true;
    }

    /**
     * Called when the mouse cursor leaves the bounds of this element.
     */
    public void onMouseLeave() {
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
        return false; // Default: event not consumed
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
        return false; // Default: event not consumed
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
        return false; // Default: event not consumed
    }

    /**
     * Called when the mouse scroll wheel is used while the cursor is over this element.
     * @param deltaX The horizontal scroll offset.
     * @param deltaY The vertical scroll offset.
     * @return true if the event was handled by this element, false otherwise.
     */
    public boolean onScroll(float deltaX, float deltaY) {
        return false; // Default: event not consumed
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
        // Reset state
        mouseOver = false;
        focused = false;
        boundsDirty = true;
        needsLayoutUpdate = true;
        needsRenderUpdate = true;
        // Clean up children recursively
        for (UIElement child : new ArrayList<>(children)) { // Iterate over a copy to allow modification
            removeChild(child); // This will also call child.cleanup() which is good.
        }
        children.clear(); // Ensure list is empty after children are cleaned up and removed
        if (parent != null && parent.children.contains(this)) {
            parent.removeChild(this); // Ensure this element is removed from its parent's list if not already
        }
        parent = null;
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
        this.needsRenderUpdate = true;
        // Subclasses can override for specific behavior (e.g., show cursor)
    }

    /**
     * Called when this element loses focus.
     */
    public void onBlur() {
        this.focused = false;
        this.needsRenderUpdate = true;
        // Subclasses can override for specific behavior (e.g., hide cursor)
    }

    // --- Keyboard & Character Input --- 
    /**
     * Called when a character is typed while this element has focus.
     * @param character The typed character.
     * @return true if the event was handled, false otherwise.
     */
    public boolean onCharTyped(char character) {
        return false; // Default: event not consumed
    }

    /**
     * Called when a key is pressed while this element has focus.
     * Useful for handling special keys like Backspace, Enter, Arrow keys.
     * @param key The GLFW key code.
     * @param mods GLFW modifier bits (Shift, Ctrl, Alt, Super).
     * @return true if the event was handled, false otherwise.
     */
    public boolean onKeyPressed(int key, int mods) {
        return false; // Default: event not consumed
    }
    
    /**
     * Called when a key is released while this element has focus.
     * @param key The GLFW key code.
     * @param mods GLFW modifier bits (Shift, Ctrl, Alt, Super).
     * @return true if the event was handled, false otherwise.
     */
    public boolean onKeyReleased(int key, int mods) {
        return false; // Default: event not consumed
    }

    // --- Getters and Setters for new properties (UI-P1-T1.1) ---
    public UIElement getParent() {
        return parent;
    }

    // Internal setter, parent is managed by addChild/removeChild
    protected void setParent(UIElement parent) {
        this.parent = parent;
    }

    public List<UIElement> getChildren() {
        return new ArrayList<>(children); // Return a copy for external use
    }

    public Vector2f getAnchorPoint() {
        return new Vector2f(anchorPoint); // Return a copy
    }

    public void setAnchorPoint(Vector2f anchorPoint) {
        if (anchorPoint != null && !this.anchorPoint.equals(anchorPoint)) {
            this.anchorPoint.set(anchorPoint);
            this.needsLayoutUpdate = true;
        }
    }
    public void setAnchorPoint(float x, float y) {
        if (this.anchorPoint.x != x || this.anchorPoint.y != y) {
            this.anchorPoint.set(x, y);
            this.needsLayoutUpdate = true;
        }
    }

    public PositioningMode getPositioningMode() {
        return positioningMode;
    }

    public void setPositioningMode(PositioningMode positioningMode) {
        if (this.positioningMode != positioningMode) {
            this.positioningMode = positioningMode;
            this.needsLayoutUpdate = true;
        }
    }

    public Insets getMargin() {
        return margin; // Direct return okay if Insets is mutable and that's intended
                       // or make Insets immutable / provide copy constructor
    }

    public void setMargin(Insets margin) {
        if (margin != null && !this.margin.equals(margin)) {
            this.margin = margin;
            this.needsLayoutUpdate = true;
        }
    }
    public void setMargin(float top, float right, float bottom, float left) {
        if (this.margin.top != top || this.margin.right != right || this.margin.bottom != bottom || this.margin.left != left) {
            this.margin = new Insets(top, right, bottom, left);
            this.needsLayoutUpdate = true;
        }
    }

    public Insets getPadding() {
        return padding; // Similar to margin
    }

    public void setPadding(Insets padding) {
        if (padding != null && !this.padding.equals(padding)) {
            this.padding = padding;
            this.needsLayoutUpdate = true;
        }
    }
    public void setPadding(float top, float right, float bottom, float left) {
        if (this.padding.top != top || this.padding.right != right || this.padding.bottom != bottom || this.padding.left != left) {
            this.padding = new Insets(top, right, bottom, left);
            this.needsLayoutUpdate = true;
        }
    }

    public Vector2f getMinSize() {
        return new Vector2f(minSize); // Return a copy
    }

    public void setMinSize(Vector2f minSize) {
        if (minSize != null && !this.minSize.equals(minSize)) {
            this.minSize.set(minSize.x < 0 ? 0 : minSize.x, minSize.y < 0 ? 0 : minSize.y);
            this.needsLayoutUpdate = true;
        }
    }
    public void setMinSize(float width, float height) {
        if (this.minSize.x != width || this.minSize.y != height) {
            this.minSize.set(width < 0 ? 0 : width, height < 0 ? 0 : height);
            this.needsLayoutUpdate = true;
        }
    }

    public Vector2f getMaxSize() {
        return new Vector2f(maxSize); // Return a copy
    }

    public void setMaxSize(Vector2f maxSize) {
        if (maxSize != null && !this.maxSize.equals(maxSize)) {
            this.maxSize.set(maxSize.x < 0 ? Float.MAX_VALUE : maxSize.x, maxSize.y < 0 ? Float.MAX_VALUE : maxSize.y);
            this.needsLayoutUpdate = true;
        }
    }
    public void setMaxSize(float width, float height) {
        if (this.maxSize.x != width || this.maxSize.y != height) {
            this.maxSize.set(width < 0 ? Float.MAX_VALUE : width, height < 0 ? Float.MAX_VALUE : height);
            this.needsLayoutUpdate = true;
        }
    }

    public Vector2f getPreferredSize() {
        // If preferred size is not explicitly set, and we have a layout manager,
        // it might be calculated. For now, just return the stored value or a default if not set.
        if (preferredSize.x >= 0 && preferredSize.y >= 0) {
            return new Vector2f(preferredSize); // Return a copy
        }
        // If no layout manager, preferred size could be based on content (e.g. TextElement)
        // This logic will be refined in calculatePreferredSize() or by LayoutManagers.
        return new Vector2f(size); // Fallback to current size if not set, this will be improved
    }

    public void setPreferredSize(Vector2f preferredSize) {
        if (preferredSize != null && !this.preferredSize.equals(preferredSize)) {
            this.preferredSize.set(preferredSize);
            this.needsLayoutUpdate = true;
        }
    }
    public void setPreferredSize(float width, float height) {
        if (this.preferredSize.x != width || this.preferredSize.y != height) {
            this.preferredSize.set(width, height);
            this.needsLayoutUpdate = true;
        }
    }

    public LayoutManager getLayout() {
        return layout;
    }

    public void setLayout(LayoutManager layout) {
        if (this.layout != layout) {
            this.layout = layout;
            this.needsLayoutUpdate = true;
        }
    }

    public boolean isNeedsLayoutUpdate() {
        return needsLayoutUpdate;
    }

    public void setNeedsLayoutUpdate(boolean needsLayoutUpdate) {
        this.needsLayoutUpdate = needsLayoutUpdate;
        if (needsLayoutUpdate && parent != null) {
            parent.setNeedsLayoutUpdate(true); // Propagate to parent
        }
    }

    public boolean isNeedsRenderUpdate() {
        return needsRenderUpdate;
    }

    public void setNeedsRenderUpdate(boolean needsRenderUpdate) {
        this.needsRenderUpdate = needsRenderUpdate;
         if (needsRenderUpdate && parent != null && parent.isNeedsRenderUpdate() != needsRenderUpdate) {
            // Propagate render update only if this element is part of a larger composited element usually
            // For now, let's assume individual elements manage their render state primarily.
            // parent.setNeedsRenderUpdate(true); // Avoid recursive updates if not careful
        }
    }

    // --- Core Methods from UI-P1-T1.2 ---

    public void addChild(UIElement child) {
        if (child != null && !children.contains(child)) {
            if (child.parent != null) {
                child.parent.removeChild(child); // Remove from previous parent
            }
            children.add(child);
            child.setParent(this);
            setNeedsLayoutUpdate(true);
            setNeedsRenderUpdate(true);
        }
    }

    public void removeChild(UIElement child) {
        if (child != null && children.remove(child)) {
            child.setParent(null);
            child.cleanup(); // Clean up the removed child
            setNeedsLayoutUpdate(true);
            setNeedsRenderUpdate(true);
        }
    }

    /**
     * Updates the layout of this element and its children.
     * This is a stub and will be expanded in UI-P1-T3.
     * The layout manager (if any) will be called here to arrange children.
     * This method should also recalculate the element's own size if it depends on children (e.g. "wrap_content").
     */
    public void updateLayout() {
        if (layout != null) {
            // Call layout manager to arrange children
            layout.arrangeChildren(this, children); // children is a protected field

            // Call layout manager to calculate preferred size and update own size if it should wrap content
            // This logic depends on how preferredSize and actual size are meant to interact.
            // If preferredSize is (-1,-1) (i.e., wrap content), then set own size to what layout manager calculates.
            if (preferredSize.x < 0 || preferredSize.y < 0) { // Check if size is meant to be dynamic (wrap_content like)
                Vector2f calculatedPreferredSize = layout.calculatePreferredSize(this, children);
                // We should only set the size if it truly means "wrap content".
                // If preferredSize was explicitly set to a value, that value should be used by parent layout managers,
                // but this element itself might still have a fixed size.
                // For now, let's assume if layout exists and preferredSize isn't explicitly set, this element's size becomes the calculated one.
                // This might need to be more nuanced, e.g. a specific flag for "wrapContentWidth/Height".

                float newWidth = (preferredSize.x < 0) ? calculatedPreferredSize.x : this.size.x; // only update if not explicitly set
                float newHeight = (preferredSize.y < 0) ? calculatedPreferredSize.y : this.size.y; // only update if not explicitly set

                // Ensure new size respects min/max constraints of this container
                newWidth = Math.max(minSize.x, Math.min(newWidth, maxSize.x));
                newHeight = Math.max(minSize.y, Math.min(newHeight, maxSize.y));

                if (this.size.x != newWidth || this.size.y != newHeight) {
                    this.size.set(newWidth, newHeight);
                    // Setting size will mark boundsDirty = true and needsLayoutUpdate = true (though we are in updateLayout)
                    // and needsRenderUpdate = true.
                }
            }
        }
        // After own layout, update children
        for (UIElement child : children) {
            if (child.isNeedsLayoutUpdate()) {
                child.updateLayout();
            }
        }
        needsLayoutUpdate = false; // Mark as updated
        boundsDirty = true; // Bounds likely changed
        needsRenderUpdate = true; // Usually layout change means render update too
    }

    /**
     * Calculates the final screen position of this element, considering its parent,
     * anchor point, and positioning mode.
     * @return The computed absolute screen position (top-left). Vector2f should be new instance or use an out-param.
     */
    public Vector2f getComputedPosition() {
        if (needsLayoutUpdate) {
            // This is a simplified view; ideally, parent would call updateLayout on children.
            // For now, ensure this element's layout is resolved before computing position.
            // updateLayout(); // Recursive call risk if not managed carefully. Layout pass should handle this.
        }

        Vector2f basePosition = new Vector2f(this.position); // Start with local position

        if (parent != null && positioningMode == PositioningMode.RELATIVE_TO_PARENT) {
            Vector2f parentComputedPosition = parent.getComputedPosition();
            Vector2f parentPaddingOffset = new Vector2f(parent.padding.left, parent.padding.top);
            basePosition.add(parentComputedPosition).add(parentPaddingOffset);
        }
        // ABSOLUTE positioning uses this.position directly as screen coordinates (or relative to viewport later)

        // Adjust for margin (affects spacing relative to siblings or parent content edge)
        // This simple model assumes margin pushes the element itself.
        // More complex layouts might handle margin spacing within the layout manager.
        basePosition.add(this.margin.left, this.margin.top);

        // Anchor point adjustment: The computed position is where the anchor point of the element should be.
        // So, we need to shift the element *back* by its anchorPoint * its own size.
        // This logic is complex if size is also computed. Assume size is known for now.
        // Vector2f computedSize = getComputedSize(); // Potential recursion
        // basePosition.sub(computedSize.x * anchorPoint.x, computedSize.y * anchorPoint.y);

        return basePosition; // Note: This is a simplified version. Full version in UI-P1-T3.
    }

    /**
     * Calculates the final size of this element, considering its content, children, layout, and min/max constraints.
     * @return The computed size. Vector2f should be new instance or use an out-param.
     */
    public Vector2f getComputedSize() {
        if (needsLayoutUpdate) {
            // updateLayout(); // Similar to getComputedPosition, layout pass should handle this.
        }
        Vector2f currentSize = new Vector2f(this.size); // Start with base size

        if (layout != null && preferredSize.x < 0 && preferredSize.y < 0) {
            // If layout manager exists and preferredSize is not set, layout manager might determine size.
            // This would typically be called by the layout manager itself during arrangeChildren.
            // For now, this is a placeholder.
            // currentSize.set(layout.calculatePreferredSize(this, children));
        } else if (preferredSize.x >= 0 && preferredSize.y >= 0) {
            currentSize.set(preferredSize);
        }

        // Apply min/max constraints
        currentSize.x = Math.max(minSize.x, Math.min(currentSize.x, maxSize.x));
        currentSize.y = Math.max(minSize.y, Math.min(currentSize.y, maxSize.y));

        return currentSize; // Note: This is a simplified version. Full version in UI-P1-T3.
    }

    /**
     * Returns the actual screen-space bounding box of the element.
     * This considers the computed position and computed size.
     * Result is an array [left, top, right, bottom].
     * @return float array [left, top, right, bottom] or a Rect class if available.
     */
    public float[] getBoundingBox() {
        Vector2f computedPos = getComputedPosition();
        Vector2f computedSize = getComputedSize();

        // Update cached bounds used by isMouseOver
        cachedLeft = computedPos.x;
        cachedTop = computedPos.y;
        cachedRight = computedPos.x + computedSize.x;
        cachedBottom = computedPos.y + computedSize.y;
        boundsDirty = false; // Bounds are now fresh

        return new float[]{cachedLeft, cachedTop, cachedRight, cachedBottom};
    }

    // --- End of Core Methods ---
} 