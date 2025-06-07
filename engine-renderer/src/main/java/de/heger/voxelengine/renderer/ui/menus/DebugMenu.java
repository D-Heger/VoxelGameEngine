package de.heger.voxelengine.renderer.ui.menus;

import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.elements.TextElement;
import de.heger.voxelengine.renderer.ui.elements.BoxElement;
import de.heger.voxelengine.renderer.ui.font.Font;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DebugMenu {

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
    private TextElement timeOfDayEnabledText;
    private TextElement timeOfDayText;
    private TextElement memoryUsageText;
    private TextElement memoryUsagePercentText;
    private TextElement peakMemoryText;
    private TextElement totalAllocationsText;
    private TextElement gcSuggestionsText;
    
    // Lighting performance metrics
    private TextElement lightingRecalcRateText;
    private TextElement lightingAvgTimeText;
    private TextElement lightingCacheHitsText;
    private TextElement lightingThresholdText;


    public DebugMenu(UIManager uiManager, Font font) {
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

        int numTextElements = 25; // Increased for lighting performance metrics + spacing

        // Calculate dimensions for the text block content itself
        // Estimate max text width. A more accurate way would be to render all text once, get max width.
        float estimatedMaxTextStringWidthAtScale1 = 800f; // Increased for longer memory text
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
        
        // Add separator for rendering metrics
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
        
        // Memory monitoring section
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        memoryUsageText = createTextElement("Memory: - MB", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        memoryUsagePercentText = createTextElement("Memory Usage: -%", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        peakMemoryText = createTextElement("Peak Memory: - MB", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        totalAllocationsText = createTextElement("Est. Allocations: - MB", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        gcSuggestionsText = createTextElement("GC Suggestions: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        
        // Lighting performance section
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        lightingRecalcRateText = createTextElement("Lighting Recalc Rate: -%", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        lightingAvgTimeText = createTextElement("Lighting Avg Time: - ms", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        lightingCacheHitsText = createTextElement("Lighting Cache Hits: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        lightingThresholdText = createTextElement("Lighting Threshold: -", textX, currentTextBaselineY);
        currentTextBaselineY += scaledLineHeight + lineSpacing;
        
        // Time of day section
        timeOfDayEnabledText = createTextElement("Day/Night cycle: ", textX, currentTextBaselineY);
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

    public void update(DebugData data) {
        if (!visible) {
            return;
        }
        
        // Basic performance metrics
        fpsText.setText(String.format(Locale.US, "FPS: %d", data.fps));
        upsText.setText(String.format(Locale.US, "UPS: %d", data.ups));
        
        // Chunk generation metrics
        avgChunkGenTimeText.setText(String.format(Locale.US, "Avg Gen Time: %.2f ms (%d)", data.avgChunkGenTime, data.chunkGenSamples));
        
        // Rendering metrics
        drawCallsText.setText(String.format(Locale.US, "Draw Calls: %d", data.drawCalls));
        renderedIndicesText.setText(String.format(Locale.US, "Rendered Indices: %dk", data.renderedIndices / 1000));
        occlusionCulledText.setText(String.format(Locale.US, "Occlusion Culled: %d", data.occlusionCulledChunks));
        frustumCulledText.setText(String.format(Locale.US, "Frustum Culled: %d", data.frustumCulledChunks));
        
        // Chunk management metrics
        totalLoadedChunksText.setText(String.format(Locale.US, "Loaded Chunks: %d", data.totalLoadedChunks));
        activeMeshesText.setText(String.format(Locale.US, "Active Meshes: %d", data.activeMeshes));
        generationQueueText.setText(String.format(Locale.US, "Gen Queue: %d", data.generationQueueSize));
        activeGenThreadsText.setText(String.format(Locale.US, "Active Gen Threads: %d", data.activeGenerationThreads));
        
        // Memory monitoring metrics
        memoryUsageText.setText(String.format(Locale.US, "Memory: %d MB", data.usedMemoryMB));
        memoryUsagePercentText.setText(String.format(Locale.US, "Memory Usage: %.1f%%", data.memoryUsagePercentage));
        peakMemoryText.setText(String.format(Locale.US, "Peak Memory: %d MB", data.peakMemoryMB));
        totalAllocationsText.setText(String.format(Locale.US, "Est. Allocations: %d MB", data.totalAllocationsMB));
        gcSuggestionsText.setText(String.format(Locale.US, "GC Suggestions: %d", data.gcSuggestionCount));
        
        // Lighting performance optimization metrics
        lightingRecalcRateText.setText(String.format(Locale.US, "Lighting Recalc Rate: %.1f%%", data.lightingRecalcRate));
        lightingAvgTimeText.setText(String.format(Locale.US, "Lighting Avg Time: %.3f ms", data.lightingAvgRecalcTimeMs));
        lightingCacheHitsText.setText(String.format(Locale.US, "Lighting Cache Hits: %d", data.lightingCacheHits));
        lightingThresholdText.setText(String.format(Locale.US, "Lighting Threshold: %.3f", data.lightingThreshold));
        
        // Time of day
        timeOfDayEnabledText.setText(String.format(Locale.US, "Day/Night cycle: %s", data.isTimeOfDayEnabled ? "Enabled" : "Disabled"));
        
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
    public static class DebugData {
        // Basic performance
        public int fps;
        public int ups;
        
        // Chunk generation
        public double avgChunkGenTime;
        public int chunkGenSamples;
        
        // Rendering
        public int drawCalls;
        public int renderedIndices;
        public int occlusionCulledChunks;
        public int frustumCulledChunks;
        
        // Chunk management
        public int totalLoadedChunks;
        public int activeMeshes;
        public int generationQueueSize;
        public int activeGenerationThreads;
        
        // Memory monitoring
        public long usedMemoryMB;
        public double memoryUsagePercentage;
        public long peakMemoryMB;
        public long totalAllocationsMB;
        public int gcSuggestionCount;
        
        // Time of day
        public boolean isTimeOfDayEnabled;
        public float normalizedTimeOfDay;
        
        // Lighting performance optimization
        public double lightingRecalcRate;
        public double lightingAvgRecalcTimeMs;
        public long lightingCacheHits;
        public float lightingThreshold;

        // Builder or constructor for convenience
        public DebugData() {} // Default constructor
    }
} 