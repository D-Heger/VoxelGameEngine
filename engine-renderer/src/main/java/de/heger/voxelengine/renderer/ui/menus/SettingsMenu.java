package de.heger.voxelengine.renderer.ui.menus;

import de.heger.voxelengine.core.config.Config;
import de.heger.voxelengine.core.config.ConfigManager;
import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.elements.BoxElement;
import de.heger.voxelengine.renderer.ui.elements.ButtonElement;
import de.heger.voxelengine.renderer.ui.elements.TextElement;
import de.heger.voxelengine.renderer.ui.font.Font;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The settings screen, reached from the pause menu.
 *
 * <p>Lets the player adjust options such as field of view and view distance, then
 * applies them live to the {@link Camera} and persists them through
 * {@link ConfigManager} so they survive a restart. Like the pause menu it is built
 * from reusable UI elements and toggled as a group.</p>
 */
public class SettingsMenu {
    private static final LoggerFacade LOGGER = LoggerFacade.get(SettingsMenu.class);

    private final UIManager uiManager;
    private final Window window;
    private final Font font;
    private final Config config;
    private final Camera camera;

    private BoxElement backgroundOverlay;
    private TextElement titleText;

    private ButtonElement vsyncButton;
    private TextElement vsyncValueText;

    
    private ButtonElement cycleViewDistanceButton;
    private TextElement viewDistanceValueText;

    private ButtonElement fullscreenButton;
    private TextElement fullscreenValueText;

    private ButtonElement cycleResolutionButton;
    private TextElement resolutionValueText;

    private ButtonElement applyButton;
    private ButtonElement saveButton;
    private ButtonElement backButton;

    private final List<UIElement> menuElements = new ArrayList<>();
    private boolean isVisible = false;

    private Runnable onBackAction;

    private static final float VIEW_DISTANCE_STEP = 25.0f;
    private static final float MIN_VIEW_DISTANCE = 25.0f;
    private static final float MAX_VIEW_DISTANCE = 500.0f;

    private record ResolutionItem(int width, int height) {
        @Override
        public String toString() {
            return width + "x" + height;
        }
    }

    private final List<ResolutionItem> availableResolutions = Arrays.asList(
        new ResolutionItem(800, 600),
        new ResolutionItem(1280, 720),
        new ResolutionItem(1600, 900),
        new ResolutionItem(1920, 1080),
        new ResolutionItem(2560, 1440)
    );
    private int currentResolutionIndex = 0;

    private final List<Float> availableViewDistances = new ArrayList<>();
    private int currentViewDistanceIndex = 0;

    public SettingsMenu(UIManager uiManager, Window window, Font font, Config config, Camera camera) {
        this.uiManager = uiManager;
        this.window = window;
        this.font = font;
        this.config = config;
        this.camera = camera;
        initializeAvailableViewDistances();
        initializeCurrentResolutionIndex();
        initializeCurrentViewDistanceIndex();
        createMenuElements();
    }

    private void initializeAvailableViewDistances() {
        for (float i = MIN_VIEW_DISTANCE; i <= MAX_VIEW_DISTANCE; i += VIEW_DISTANCE_STEP) {
            availableViewDistances.add(i);
        }
    }

    private void initializeCurrentResolutionIndex() {
        for (int i = 0; i < availableResolutions.size(); i++) {
            ResolutionItem res = availableResolutions.get(i);
            if (res.width() == config.getWidth() && res.height() == config.getHeight()) {
                currentResolutionIndex = i;
                return;
            }
        }
        // If current config resolution is not in the list, add it and select it
        // Or default to the first one if adding is not desired.
        LOGGER.warn("Current config resolution {}x{} not in predefined list. Defaulting.", config.getWidth(), config.getHeight());
        if (!availableResolutions.isEmpty()) {
            config.setWidth(availableResolutions.get(0).width());
            config.setHeight(availableResolutions.get(0).height());
            currentResolutionIndex = 0;
        } else {
            LOGGER.error("Available resolutions list is empty!");
        }
    }

    private void initializeCurrentViewDistanceIndex() {
        float currentViewDistance = config.getViewDistance();
        for (int i = 0; i < availableViewDistances.size(); i++) {
            if (Math.abs(availableViewDistances.get(i) - currentViewDistance) < 0.1f) { // Compare floats with tolerance
                currentViewDistanceIndex = i;
                return;
            }
        }
        // If current config view distance is not in the list, find the closest or default
        LOGGER.warn("Current config view distance {} not in predefined list. Defaulting.", currentViewDistance);
        if (!availableViewDistances.isEmpty()) {
            // Default to a reasonable value or the first in the list
            float defaultViewDistance = 100f; // Example default
            int closestIndex = 0;
            float smallestDiff = Float.MAX_VALUE;
            for (int i = 0; i < availableViewDistances.size(); i++) {
                float diff = Math.abs(availableViewDistances.get(i) - defaultViewDistance);
                if (diff < smallestDiff) {
                    smallestDiff = diff;
                    closestIndex = i;
                }
            }
            currentViewDistanceIndex = closestIndex;
            config.setViewDistance(availableViewDistances.get(currentViewDistanceIndex));

        } else {
            LOGGER.error("Available view distances list is empty!");
        }
    }

    private void createMenuElements() {
        backgroundOverlay = new BoxElement(new Vector2f(0, 0), new Vector2f(window.getWidth(), window.getHeight()), new Vector4f(0.1f, 0.1f, 0.1f, 0.85f));
        menuElements.add(backgroundOverlay);

        titleText = new TextElement("Settings", font, new Vector2f(0, 0), new Vector4f(1f, 1f, 1f, 1f), 0.7f);
        menuElements.add(titleText);

        float buttonWidth = 280f; // Increased width for more space
        float smallButtonWidth = 50f; // Increased width
        float buttonHeight = 40f; // Increased height
        float buttonTextScale = 0.45f; // Slightly larger text
        float valueTextScale = 0.45f;
        Vector4f textColor = new Vector4f(0.95f, 0.95f, 0.95f, 1f);

        // VSync
        vsyncButton = new ButtonElement(new Vector2f(0,0), "VSync", font, buttonTextScale, this::toggleVSync);
        vsyncButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(vsyncButton);
        vsyncValueText = new TextElement("", font, new Vector2f(0,0), textColor, valueTextScale);
        menuElements.add(vsyncValueText);

        // Fullscreen
        fullscreenButton = new ButtonElement(new Vector2f(0,0), "Fullscreen", font, buttonTextScale, this::toggleFullscreen);
        fullscreenButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(fullscreenButton);
        fullscreenValueText = new TextElement("", font, new Vector2f(0,0), textColor, valueTextScale);
        menuElements.add(fullscreenValueText);

        // Resolution
        cycleResolutionButton = new ButtonElement(new Vector2f(0,0), "Resolution", font, buttonTextScale, this::cycleResolution);
        cycleResolutionButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(cycleResolutionButton);
        resolutionValueText = new TextElement("", font, new Vector2f(0,0), textColor, valueTextScale);
        menuElements.add(resolutionValueText);

        // View Distance
        cycleViewDistanceButton = new ButtonElement(new Vector2f(0,0), "View Distance", font, buttonTextScale, this::cycleViewDistance);
        cycleViewDistanceButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(cycleViewDistanceButton);
        if (viewDistanceValueText == null) {
             viewDistanceValueText = new TextElement("", font, new Vector2f(0,0), textColor, valueTextScale);
        }
        menuElements.add(viewDistanceValueText);

        // Action Buttons
        applyButton = new ButtonElement(new Vector2f(0, 0), "Apply Changes", font, buttonTextScale, this::applySettings);
        applyButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(applyButton);

        saveButton = new ButtonElement(new Vector2f(0, 0), "Save & Apply", font, buttonTextScale, this::saveAndApplySettings);
        saveButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(saveButton);
        
        backButton = new ButtonElement(new Vector2f(0, 0), "Back", font, buttonTextScale, () -> {
            if (onBackAction != null) onBackAction.run();
        });
        backButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(backButton);

        for(UIElement element : menuElements) {
            element.setVisible(false);
        }
        updateDynamicTexts();
    }
    
    private void updateDynamicTexts() {
        if (vsyncValueText != null) {
            vsyncValueText.setText(config.isVsync() ? "ON" : "OFF");
            vsyncValueText.buildMeshIfNeeded();
        }
        if (fullscreenValueText != null) {
            fullscreenValueText.setText(config.isFullscreen() ? "ON" : "OFF");
            fullscreenValueText.buildMeshIfNeeded();
        }
        if (resolutionValueText != null) {
            ResolutionItem currentRes = availableResolutions.get(currentResolutionIndex);
            resolutionValueText.setText(currentRes.toString());
            resolutionValueText.buildMeshIfNeeded();
        }
        if (viewDistanceValueText != null) {
            if (!availableViewDistances.isEmpty()) {
                viewDistanceValueText.setText(String.format(Locale.US, "%.0f", availableViewDistances.get(currentViewDistanceIndex)));
                viewDistanceValueText.buildMeshIfNeeded();
            }
        }
        updateLayout();
    }

    private void toggleVSync() {
        config.setVsync(!config.isVsync());
        updateDynamicTexts();
    }

    private void toggleFullscreen() {
        config.setFullscreen(!config.isFullscreen());
        updateDynamicTexts();
    }

    private void cycleResolution() {
        currentResolutionIndex = (currentResolutionIndex + 1) % availableResolutions.size();
        ResolutionItem newRes = availableResolutions.get(currentResolutionIndex);
        config.setWidth(newRes.width());
        config.setHeight(newRes.height());
        updateDynamicTexts();
    }

    private void cycleViewDistance() {
        if (availableViewDistances.isEmpty()) return;
        currentViewDistanceIndex = (currentViewDistanceIndex + 1) % availableViewDistances.size();
        config.setViewDistance(availableViewDistances.get(currentViewDistanceIndex));
        updateDynamicTexts();
    }

    private void applySettings() {
        LOGGER.info("Applying settings...");
        if (window != null) {
            window.setVsync(config.isVsync());
            LOGGER.info("Applied VSync: {}", config.isVsync());
            
            // Important: Changing fullscreen or resolution might require window recreation or careful handling
            // by the Window class. These are blocking calls for simplicity here.
            if (window.isFullscreen() != config.isFullscreen()) {
                 window.setFullscreen(config.isFullscreen()); // Apply fullscreen first if it changed
                 LOGGER.info("Applied Fullscreen: {}", config.isFullscreen());
            }
            // Only apply resolution if not fullscreen or if resolution changed separately
            if (window.getWidth() != config.getWidth() || window.getHeight() != config.getHeight()) {
                window.setSize(config.getWidth(), config.getHeight());
                LOGGER.info("Applied Resolution: {}x{}", config.getWidth(), config.getHeight());
            }
        }
        if (camera != null) {
            camera.setViewDistance(config.getViewDistance());
            LOGGER.info("Applied View Distance: {}", config.getViewDistance());
        }
        updateLayout();
    }

    private void saveAndApplySettings() {
        LOGGER.info("Saving and applying settings...");
        ConfigManager.save(config);
        LOGGER.info("Configuration saved to file.");
        applySettings();
    }

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    public void show() {
        if (isVisible) return;
        LOGGER.debug("Showing SettingsMenu");
        initializeCurrentResolutionIndex(); // Ensure resolution index is synced with config
        initializeCurrentViewDistanceIndex(); // Ensure view distance index is synced
        updateDynamicTexts(); 
        for (UIElement element : menuElements) {
            element.setVisible(true);
            uiManager.addElement(element);
        }
        isVisible = true;
    }

    public void hide() {
        if (!isVisible) return;
        LOGGER.debug("Hiding SettingsMenu");
        for (UIElement element : menuElements) {
            element.setVisible(false);
            uiManager.removeElement(element);
        }
        isVisible = false;
    }

    public boolean isVisible() {
        return isVisible;
    }
    
    public void updateLayout() {
        float windowWidth = window.getWidth();
        float windowHeight = window.getHeight();

        if (backgroundOverlay != null) {
            backgroundOverlay.setPosition(0,0);
            backgroundOverlay.setSize(windowWidth, windowHeight);
        }

        if (titleText != null) {
            TextElement tempTitle = new TextElement(titleText.getText(), font, new Vector2f(0,0), new Vector4f(0,0,0,0), titleText.getScale());
            float titleWidth = tempTitle.getSize().x;
            tempTitle.cleanup();
            titleText.setPosition((windowWidth - titleWidth) / 2, windowHeight * 0.08f); // Title higher
        }
        
        float itemStartY = windowHeight * 0.20f; // Start items a bit lower than title
        float itemSpacingY = 20f; // Increased spacing between rows
        float controlGroupSpacingY = 30f; // Spacing between groups of controls (e.g. VSync and Fullscreen)
        float labelValueSpacingX = 15f; // Horizontal spacing between a setting's button and its value text
        float currentY = itemStartY;

        float column1X = windowWidth * 0.3f; // X for labels/buttons
        float column2X = windowWidth * 0.65f; // X for values (right aligned or start)
        float buttonHeight = vsyncButton != null ? vsyncButton.getSize().y : 40f; // Use actual button height

        // VSync Layout
        if (vsyncButton != null && vsyncValueText != null) {
            vsyncButton.setPosition(column1X - vsyncButton.getSize().x / 2, currentY); // Centered in its column part
            float valueTextHeight = vsyncValueText.getSize().y;
            float valueTextScale = vsyncValueText.getScale();
            float valueTextVisualAscent = font.getAscent() * valueTextScale;
            float valueTextBlockTopY = currentY + (buttonHeight - valueTextHeight) / 2.0f;
            vsyncValueText.setPosition(column2X, valueTextBlockTopY + valueTextVisualAscent);
            currentY += buttonHeight + itemSpacingY;
        }

        // Fullscreen Layout
        if (fullscreenButton != null && fullscreenValueText != null) {
            fullscreenButton.setPosition(column1X - fullscreenButton.getSize().x / 2, currentY);
            float valueTextHeight = fullscreenValueText.getSize().y;
            float valueTextScale = fullscreenValueText.getScale();
            float valueTextVisualAscent = font.getAscent() * valueTextScale;
            float valueTextBlockTopY = currentY + (buttonHeight - valueTextHeight) / 2.0f;
            fullscreenValueText.setPosition(column2X, valueTextBlockTopY + valueTextVisualAscent);
            currentY += buttonHeight + itemSpacingY;
        }

        // Resolution Layout
        if (cycleResolutionButton != null && resolutionValueText != null) {
            cycleResolutionButton.setPosition(column1X - cycleResolutionButton.getSize().x / 2, currentY);
            float valueTextHeight = resolutionValueText.getSize().y;
            float valueTextScale = resolutionValueText.getScale();
            float valueTextVisualAscent = font.getAscent() * valueTextScale;
            float valueTextBlockTopY = currentY + (buttonHeight - valueTextHeight) / 2.0f;
            resolutionValueText.setPosition(column2X, valueTextBlockTopY + valueTextVisualAscent);
            currentY += buttonHeight + itemSpacingY;
        }

        // View Distance Layout (New: Similar to Resolution)
        if (cycleViewDistanceButton != null && viewDistanceValueText != null) {
            cycleViewDistanceButton.setPosition(column1X - cycleViewDistanceButton.getSize().x / 2, currentY);
            float valueTextHeight = viewDistanceValueText.getSize().y;
            float valueTextScale = viewDistanceValueText.getScale();
            float valueTextVisualAscent = font.getAscent() * valueTextScale;
            float valueTextBlockTopY = currentY + (buttonHeight - valueTextHeight) / 2.0f;
            viewDistanceValueText.setPosition(column2X, valueTextBlockTopY + valueTextVisualAscent);
            currentY += buttonHeight + itemSpacingY;
        }

        currentY += controlGroupSpacingY * 0.5f; // Extra space before view distance controls (or next group)

        // Action Buttons Layout (Apply, Save, Back) - Stacked at the bottom
        float actionButtonStartY = windowHeight - (buttonHeight * 3) - (itemSpacingY * 2) - (windowHeight * 0.05f); // Start from bottom up
        currentY = actionButtonStartY;

        if (backButton != null) {
            backButton.setPosition((windowWidth - backButton.getSize().x()) / 2f, currentY);
            currentY += buttonHeight + itemSpacingY;
        }
        if (applyButton != null) {
            applyButton.setPosition((windowWidth - applyButton.getSize().x()) / 2f, currentY);
            currentY += buttonHeight + itemSpacingY;
        }
        if (saveButton != null) { // Save button at the very bottom of this stack
            saveButton.setPosition((windowWidth - saveButton.getSize().x()) / 2f, currentY);
        }
    }

    public void cleanup() {
        LOGGER.debug("Cleaning up SettingsMenu");
        hide();
        for (UIElement element : menuElements) {
            element.cleanup();
        }
        menuElements.clear();
    }
} 