package de.heger.voxelengine.renderer.ui;

import org.joml.Vector2f;

public abstract class UIElement {
    protected Vector2f position;
    protected Vector2f size;
    protected boolean visible;
    protected float alpha; // For transparency

    public UIElement(Vector2f position, Vector2f size) {
        this.position = position;
        this.size = size;
        this.visible = true;
        this.alpha = 1.0f;
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
} 