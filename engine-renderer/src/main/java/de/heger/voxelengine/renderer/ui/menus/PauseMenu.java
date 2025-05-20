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
    private ButtonElement quitButton;

    private final List<UIElement> menuElements = new ArrayList<>();

    private boolean isVisible = false;

    private Runnable onContinueAction;
    private Runnable onQuitAction;

    public PauseMenu(UIManager uiManager, Window window, Font font) {
        this.uiManager = uiManager;
        this.window = window;
        this.font = font;
        createMenuElements();
    }

    private void createMenuElements() {
        // Background Overlay
        float windowWidth = window.getWidth();
        float windowHeight = window.getHeight();
        backgroundOverlay = new BoxElement(
                new Vector2f(0, 0),
                new Vector2f(windowWidth, windowHeight),
                new Vector4f(0.1f, 0.1f, 0.1f, 0.85f) // Dark, semi-transparent
        );
        menuElements.add(backgroundOverlay);

        // Title Text
        String titleStr = "Paused";
        float titleScale = 0.8f;
        TextElement tempTitle = new TextElement(titleStr, font, new Vector2f(0,0), new Vector4f(1,1,1,1), titleScale);
        float titleWidth = tempTitle.getSize().x;
        //float titleHeight = tempTitle.getSize().y;
        float titleX = (windowWidth - titleWidth) / 2;
        float titleY = windowHeight * 0.2f; // Position title at 20% from top
        titleText = new TextElement(titleStr, font, new Vector2f(titleX, titleY), new Vector4f(1f, 1f, 1f, 1f), titleScale);
        tempTitle.cleanup();
        menuElements.add(titleText);


        // Buttons
        float buttonWidth = 200f;
        float buttonHeight = 40f;
        float buttonSpacing = 20f;
        float buttonTextScale = 0.5f;

        float continueButtonX = (windowWidth - buttonWidth) / 2;
        float continueButtonY = windowHeight * 0.45f;

        continueButton = new ButtonElement(
                new Vector2f(continueButtonX, continueButtonY),
                "Continue",
                font,
                buttonTextScale,
                () -> {
                    if (onContinueAction != null) onContinueAction.run();
                }
        );
        continueButton.setSize(buttonWidth, buttonHeight); // Force size
        menuElements.add(continueButton);

        float quitButtonX = (windowWidth - buttonWidth) / 2;
        float quitButtonY = continueButtonY + buttonHeight + buttonSpacing;

        quitButton = new ButtonElement(
                new Vector2f(quitButtonX, quitButtonY),
                "Quit Game",
                font,
                buttonTextScale,
                () -> {
                    if (onQuitAction != null) onQuitAction.run();
                }
        );
        quitButton.setSize(buttonWidth, buttonHeight); // Force size
        menuElements.add(quitButton);

        // Initially, elements are not added to UIManager, only when show() is called.
        // And they are not visible by default.
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

    public void show() {
        if (isVisible) return;
        LOGGER.debug("Showing PauseMenu");
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
            backgroundOverlay.setSize(windowWidth, windowHeight);
        }

        if (titleText != null) {
            // Recalculate title position based on its current text/font/scale if they could change
            TextElement tempTitle = new TextElement(titleText.getText(), titleText.getFont(), new Vector2f(0,0), new Vector4f(0,0,0,0), titleText.getScale());
            float titleWidth = tempTitle.getSize().x;
            tempTitle.cleanup();
            titleText.setPosition((windowWidth - titleWidth) / 2, windowHeight * 0.2f);
        }

        if (continueButton != null) {
            float buttonWidth = continueButton.getSize().x; // Assuming fixed size set earlier
            float buttonHeight = continueButton.getSize().y;
            continueButton.setPosition((windowWidth - buttonWidth) / 2, windowHeight * 0.45f);
        }

        if (quitButton != null) {
            float buttonWidth = quitButton.getSize().x; // Assuming fixed size
            float buttonHeight = quitButton.getSize().y;
            float buttonSpacing = 20f; // from createMenuElements
            float continueButtonY = windowHeight * 0.45f; // from createMenuElements
            if (continueButton != null) {
                 continueButtonY = continueButton.getPosition().y;
                 buttonHeight = continueButton.getSize().y; // ensure consistency
            }
            quitButton.setPosition((windowWidth - buttonWidth) / 2, continueButtonY + buttonHeight + buttonSpacing);
        }
    }


    public void cleanup() {
        LOGGER.debug("Cleaning up PauseMenu");
        hide(); // Ensure elements are removed from UIManager
        for (UIElement element : menuElements) {
            element.cleanup(); // Individual cleanup for VAOs etc.
        }
        menuElements.clear();
    }
} 