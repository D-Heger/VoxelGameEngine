package de.heger.voxelengine.renderer.ui.debug;

import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.elements.TextElement;
import de.heger.voxelengine.renderer.ui.font.Font;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PerformanceDisplay {

    private final UIManager uiManager;
    private final Font font;
    private final Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f); // White
    private final float fontSize = 16f; // Example font size
    private final float lineSpacing = 2f; // Spacing between lines of text

    private final List<TextElement> textElements = new ArrayList<>();
    private boolean visible = true;

    // Text elements for different metrics
    private TextElement fpsText;
    private TextElement upsText;
    private TextElement avgChunkGenTimeText;
    private TextElement drawCallsText;
    private TextElement renderedIndicesText;
    private TextElement occlusionCulledText;
    private TextElement frustumCulledText;
    private TextElement totalLoadedChunksText;
    private TextElement activeMeshesText;
    private TextElement generationQueueText;
    private TextElement activeGenThreadsText;


    public PerformanceDisplay(UIManager uiManager, Font font) {
        this.uiManager = uiManager;
        this.font = font;
    }

    public void init() {
        float currentY = 20.0f; // Starting Y position for the first text element
        float xPos = 10.0f;

        fpsText = createTextElement("FPS: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        upsText = createTextElement("UPS: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        avgChunkGenTimeText = createTextElement("Avg Gen Time: - ms (0)", xPos, currentY);
        currentY += fontSize + lineSpacing;
        drawCallsText = createTextElement("Draw Calls: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        renderedIndicesText = createTextElement("Rendered Indices: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        occlusionCulledText = createTextElement("Occlusion Culled Chunks: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        frustumCulledText = createTextElement("Frustum Culled Chunks: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        totalLoadedChunksText = createTextElement("Loaded Chunks: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        activeMeshesText = createTextElement("Active Meshes: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        generationQueueText = createTextElement("Gen Queue: -", xPos, currentY);
        currentY += fontSize + lineSpacing;
        activeGenThreadsText = createTextElement("Active Gen Threads: -", xPos, currentY);


        // Set initial visibility
        setVisible(this.visible);
    }

    private TextElement createTextElement(String initialText, float x, float y) {
        TextElement textElement = new TextElement(initialText, font, new Vector2f(x, y), textColor);
        textElements.add(textElement);
        uiManager.addElement(textElement);
        return textElement;
    }

    public void update(PerformanceData data) {
        if (!visible) {
            return;
        }
        fpsText.setText(String.format(Locale.US, "FPS: %d", data.fps));
        upsText.setText(String.format(Locale.US, "UPS: %d", data.ups));
        avgChunkGenTimeText.setText(String.format(Locale.US, "Avg Gen Time: %.2f ms (%d)", data.avgChunkGenTime, data.chunkGenSamples));
        drawCallsText.setText(String.format(Locale.US, "Draw Calls: %d", data.drawCalls));
        renderedIndicesText.setText(String.format(Locale.US, "Rendered Indices: %dk", data.renderedIndices / 1000));
        occlusionCulledText.setText(String.format(Locale.US, "Occlusion Culled: %d", data.occlusionCulledChunks));
        frustumCulledText.setText(String.format(Locale.US, "Frustum Culled: %d", data.frustumCulledChunks));
        totalLoadedChunksText.setText(String.format(Locale.US, "Loaded Chunks: %d", data.totalLoadedChunks));
        activeMeshesText.setText(String.format(Locale.US, "Active Meshes: %d", data.activeMeshes));
        generationQueueText.setText(String.format(Locale.US, "Gen Queue: %d", data.generationQueueSize));
        activeGenThreadsText.setText(String.format(Locale.US, "Active Gen Threads: %d", data.activeGenerationThreads));
    }


    public void setVisible(boolean visible) {
        this.visible = visible;
        for (TextElement element : textElements) {
            element.setVisible(visible);
        }
    }

    public void toggleVisibility() {
        setVisible(!this.visible);
    }

    public boolean isVisible() {
        return visible;
    }

    public void cleanup() {
        for (TextElement element : textElements) {
            uiManager.removeElement(element);
        }
        textElements.clear();
    }

    // Static inner class or a new file for PerformanceData
    public static class PerformanceData {
        public int fps;
        public int ups;
        public double avgChunkGenTime;
        public int chunkGenSamples;
        public int drawCalls;
        public int renderedIndices;
        public int occlusionCulledChunks;
        public int frustumCulledChunks;
        public int totalLoadedChunks;
        public int activeMeshes;
        public int generationQueueSize;
        public int activeGenerationThreads;


        // Builder or constructor for convenience
        public PerformanceData() {} // Default constructor
    }
} 