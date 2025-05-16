package de.heger.voxelengine.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration data for the Voxel Game Engine launcher.
 */
public class Config {
    @JsonProperty
    private String windowTitle = "Voxel Game Engine";
    @JsonProperty
    private int width = 1280;
    @JsonProperty
    private int height = 720;
    @JsonProperty
    private boolean vsync = true;
    @JsonProperty
    private boolean fullscreen = false;
    @JsonProperty
    private float viewDistance = 200.0f;

    public Config() {
        // Default constructor with default values
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isVsync() {
        return vsync;
    }

    public void setVsync(boolean vsync) {
        this.vsync = vsync;
    }

    public boolean isFullscreen()
    {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen)
    {
        this.fullscreen = fullscreen;
    }

    public float getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(float viewDistance) {
        this.viewDistance = viewDistance;
    }
}
