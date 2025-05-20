package de.heger.voxelengine.renderer.ui.menus.debug;

import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.elements.TextElement;
import de.heger.voxelengine.renderer.ui.elements.BoxElement;
import de.heger.voxelengine.renderer.ui.elements.ButtonElement;
import de.heger.voxelengine.renderer.ui.font.Font;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PerformanceMenu {

    private final UIManager uiManager;
    private final Font font;
    private final Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f); // White
    private final float textScale = 0.3f; // Scale factor for the text elements
    private final float lineSpacing = 2f; // Additional spacing between lines of text (on top of font's own linegap)
    private final float textBlockPadding = 5.0f; // Padding around the text block inside the background box

    private final List<TextElement> textElements = new ArrayList<>();
    private BoxElement backgroundBox;
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
    private TextElement timeOfDayText;


    public PerformanceMenu(UIManager uiManager, Font font) {
        this.uiManager = uiManager;
        this.font = font;
    }

    public void init() {
        // These are the start coordinates for the TextElements themselves (their baselines)
        float visualTextTopY = 20.0f; // Desired Y coordinate for the top of the first line of text
        float textX = 10.0f;

        float scaledAscent = font.getAscent() * textScale;
        float scaledLineHeight = font.getLineHeight() * textScale;

        float firstTextBaselineY = visualTextTopY + scaledAscent; 

        int numTextElements = 12; // Increased for the new time display

        // Calculate dimensions for the text block content itself
        // Estimate max text width. A more accurate way would be to render all text once, get max width.
        float estimatedMaxTextStringWidthAtScale1 = 680f; // Estimated width if scale were 1.0
        float textContentWidth = estimatedMaxTextStringWidthAtScale1 * textScale; 

        // Height of the text block content from the top of the first line to bottom of the last line
        float textContentHeight = (numTextElements * scaledLineHeight) + ((numTextElements - 1) * lineSpacing);
        if (numTextElements == 1) textContentHeight = scaledLineHeight;

        // Background Box positioning and sizing
        // Box top-left X: textX is where text starts, so box is textX - padding
        float boxX = textX - textBlockPadding;
        // Box top-left Y: based on the visual top of the text content area
        float boxY = visualTextTopY - textBlockPadding;
        
        float boxWidth = textContentWidth + (2 * textBlockPadding);
        float boxHeight = textContentHeight + (2 * textBlockPadding);
        Vector4f boxColor = new Vector4f(0.216f, 0.0f, 0.116f, 0.8f);
        
        backgroundBox = new BoxElement(new Vector2f(boxX, boxY), new Vector2f(boxWidth, boxHeight), boxColor);
        uiManager.addElement(backgroundBox);

        // Initialize text elements
        float currentTextBaselineY = firstTextBaselineY;

        fpsText = createTextElement("FPS: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        upsText = createTextElement("UPS: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        avgChunkGenTimeText = createTextElement("Avg Gen Time: - ms (0)", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        drawCallsText = createTextElement("Draw Calls: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        renderedIndicesText = createTextElement("Rendered Indices: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        occlusionCulledText = createTextElement("Occlusion Culled Chunks: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        frustumCulledText = createTextElement("Frustum Culled Chunks: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        totalLoadedChunksText = createTextElement("Loaded Chunks: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        activeMeshesText = createTextElement("Active Meshes: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        generationQueueText = createTextElement("Gen Queue: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        activeGenThreadsText = createTextElement("Active Gen Threads: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        timeOfDayText = createTextElement("Time: --:--", textX, currentTextBaselineY);

        // Set initial visibility
        setVisible(this.visible);
    }

    private TextElement createTextElement(String initialText, float x, float y) {
        TextElement textElement = new TextElement(initialText, font, new Vector2f(x, y), textColor, textScale);
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

        // Update time of day text
        if (timeOfDayText != null) {
            float normalizedTime = data.normalizedTimeOfDay;
            int totalMinutesInDay = (int) (normalizedTime * 24 * 60);
            int hours = (totalMinutesInDay / 60) % 24;
            int minutes = totalMinutesInDay % 60;
            timeOfDayText.setText(String.format(Locale.US, "Time: %02d:%02d", hours, minutes));
        }
    }


    public void setVisible(boolean visible) {
        this.visible = visible;
        if (backgroundBox != null) {
            backgroundBox.setVisible(visible);
        }
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
        if (backgroundBox != null) {
            uiManager.removeElement(backgroundBox);
            backgroundBox.cleanup();
            backgroundBox = null;
        }
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
        public float normalizedTimeOfDay;


        // Builder or constructor for convenience
        public PerformanceData() {} // Default constructor
    }
} 