package de.heger.voxelengine.renderer.ui.menus;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.platform.Window;
import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIManager;
import de.heger.voxelengine.renderer.ui.elements.BoxElement;
import de.heger.voxelengine.renderer.ui.elements.ButtonElement;
import de.heger.voxelengine.renderer.ui.elements.TextElement;
import de.heger.voxelengine.renderer.ui.font.Font;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class PauseMenu {
    private static final LoggerFacade LOGGER = LoggerFacade.get(PauseMenu.class);

    private final UIManager uiManager;
    private final Window window;
    private final Font font;

    private BoxElement backgroundOverlay;
    private TextElement titleText;
    private ButtonElement continueButton;
    private ButtonElement settingsButton;
    private ButtonElement quitButton;

    private final List<UIElement> menuElements = new ArrayList<>();

    private boolean isVisible = false;

    private Runnable onContinueAction;
    private Runnable onSettingsAction;
    private Runnable onQuitAction;

    public PauseMenu(UIManager uiManager, Window window, Font font) {
        this.uiManager = uiManager;
        this.window = window;
        this.font = font;
        createMenuElements();
    }

    private void createMenuElements() {
        // Background Overlay
        backgroundOverlay = new BoxElement(
                new Vector2f(0, 0),
                new Vector2f(window.getWidth(), window.getHeight()),
                new Vector4f(0.1f, 0.1f, 0.1f, 0.85f) // Dark, semi-transparent
        );
        menuElements.add(backgroundOverlay);

        // Title Text
        String titleStr = "Paused";
        float titleScale = 0.8f;
        titleText = new TextElement(titleStr, font, new Vector2f(0, 0), new Vector4f(1f, 1f, 1f, 1f), titleScale);
        menuElements.add(titleText);

        // Buttons
        float buttonWidth = 200f;
        float buttonHeight = 40f;
        float buttonTextScale = 0.5f;

        continueButton = new ButtonElement(
                new Vector2f(0, 0),
                "Continue",
                font,
                buttonTextScale,
                () -> {
                    if (onContinueAction != null) onContinueAction.run();
                }
        );
        continueButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(continueButton);

        settingsButton = new ButtonElement(
                new Vector2f(0, 0),
                "Settings",
                font,
                buttonTextScale,
                () -> {
                    if (onSettingsAction != null) onSettingsAction.run();
                }
        );
        settingsButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(settingsButton);

        quitButton = new ButtonElement(
                new Vector2f(0, 0),
                "Quit Game",
                font,
                buttonTextScale,
                () -> {
                    if (onQuitAction != null) onQuitAction.run();
                }
        );
        quitButton.setSize(buttonWidth, buttonHeight);
        menuElements.add(quitButton);

        for(UIElement element : menuElements) {
            element.setVisible(false);
        }
    }

    public void setOnContinueAction(Runnable onContinueAction) {
        this.onContinueAction = onContinueAction;
    }

    public void setOnQuitAction(Runnable onQuitAction) {
        this.onQuitAction = onQuitAction;
    }

    public void setOnSettingsAction(Runnable onSettingsAction) {
        this.onSettingsAction = onSettingsAction;
    }

    public void show() {
        if (isVisible) return;
        LOGGER.debug("Showing PauseMenu");
        updateLayout();
        for (UIElement element : menuElements) {
            element.setVisible(true);
            uiManager.addElement(element);
        }
        isVisible = true;
    }

    public void hide() {
        if (!isVisible) return;
        LOGGER.debug("Hiding PauseMenu");
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
            TextElement tempTitle = new TextElement(titleText.getText(), titleText.getFont(), new Vector2f(0,0), new Vector4f(0,0,0,0), titleText.getScale());
            float titleWidth = tempTitle.getSize().x;
            tempTitle.cleanup();
            titleText.setPosition((windowWidth - titleWidth) / 2, windowHeight * 0.2f);
        }

        float buttonStackStartY = windowHeight * 0.45f;
        float buttonSpacing = 20f;

        if (continueButton != null) {
            float buttonWidth = continueButton.getSize().x;
            continueButton.setPosition((windowWidth - buttonWidth) / 2, buttonStackStartY);
        }

        if (settingsButton != null) {
            float buttonWidth = settingsButton.getSize().x;
            float continueButtonEndY = continueButton.getPosition().y + continueButton.getSize().y;
            settingsButton.setPosition((windowWidth - buttonWidth) / 2, continueButtonEndY + buttonSpacing);
        }

        if (quitButton != null && continueButton != null) {
            float buttonWidth = quitButton.getSize().x;
            float settingsButtonEndY = settingsButton.getPosition().y + settingsButton.getSize().y;
            quitButton.setPosition((windowWidth - buttonWidth) / 2, settingsButtonEndY + buttonSpacing);
        }
    }

    public void cleanup() {
        LOGGER.debug("Cleaning up PauseMenu");
        hide();
        for (UIElement element : menuElements) {
            element.cleanup();
        }
        menuElements.clear();
    }
} 