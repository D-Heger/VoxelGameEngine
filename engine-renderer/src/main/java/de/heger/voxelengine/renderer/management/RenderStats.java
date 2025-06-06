package de.heger.voxelengine.renderer.management;

/**
 * A data object to hold statistics about the rendering process for a single frame.
 */
public class RenderStats {

    private int totalIndicesRendered = 0;
    private int drawCalls = 0;
    private int occlusionCulledChunks = 0;
    private int frustumCulledChunks = 0;

    /**
     * Resets all statistics to zero. Should be called at the beginning of each frame.
     */
    public void reset() {
        totalIndicesRendered = 0;
        drawCalls = 0;
        occlusionCulledChunks = 0;
        frustumCulledChunks = 0;
    }

    /**
     * @return The total number of vertex indices rendered in the last frame.
     */
    public int getTotalIndicesRendered() {
        return totalIndicesRendered;
    }

    /**
     * Adds a number of indices to the total count for the current frame.
     * @param count The number of indices to add.
     */
    public void addIndices(int count) {
        this.totalIndicesRendered += count;
    }

    /**
     * @return The number of draw calls issued in the last frame.
     */
    public int getDrawCalls() {
        return drawCalls;
    }

    /**
     * Increments the draw call count for the current frame by one.
     */
    public void incrementDrawCalls() {
        this.drawCalls++;
    }

    /**
     * @return The number of chunks culled by occlusion culling in the last frame.
     */
    public int getOcclusionCulledChunks() {
        return occlusionCulledChunks;
    }

    /**
     * Sets the number of chunks culled by the occlusion culler.
     * @param count The number of occluded chunks.
     */
    public void setOcclusionCulledChunks(int count) {
        this.occlusionCulledChunks = count;
    }

    /**
     * @return The number of chunks culled by frustum culling in the last frame.
     */
    public int getFrustumCulledChunks() {
        return frustumCulledChunks;
    }

    /**
     * Increments the frustum-culled chunk count for the current frame by one.
     */
    public void incrementFrustumCulledChunks() {
        this.frustumCulledChunks++;
    }
} 