package de.heger.voxelengine.renderer.ui.elements;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIRenderer;
import de.heger.voxelengine.renderer.ui.font.Font;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class ButtonElement extends UIElement {
    private static final LoggerFacade LOGGER = LoggerFacade.get(ButtonElement.class);

    private TextElement textElement;
    private BoxElement backgroundElement;
    private Runnable onClickAction;

    private Font font;
    private String text;
    private float textScale;

    // Style properties
    private Vector4f normalBackgroundColor;
    private Vector4f hoverBackgroundColor;
    private Vector4f pressedBackgroundColor;
    private Vector4f disabledBackgroundColor;

    private Vector4f normalTextColor;
    private Vector4f hoverTextColor;
    private Vector4f pressedTextColor;
    private Vector4f disabledTextColor;

    private boolean isPressed = false;
    private boolean isDisabled = false;

    private float paddingHorizontal = 10.0f;
    private float paddingVertical = 5.0f;
    private boolean autoSize = true;

    public ButtonElement(Vector2f position, String text, Font font, float textScale, Runnable onClickAction) {
        super(position, new Vector2f(0,0)); // Initial size, will be updated by updateLayout
        this.text = text;
        this.font = font;
        this.textScale = textScale;
        this.onClickAction = onClickAction;

        // Default styles
        this.normalBackgroundColor = new Vector4f(0.3f, 0.3f, 0.3f, 1.0f);
        this.hoverBackgroundColor = new Vector4f(0.4f, 0.4f, 0.4f, 1.0f);
        this.pressedBackgroundColor = new Vector4f(0.2f, 0.2f, 0.2f, 1.0f);
        this.disabledBackgroundColor = new Vector4f(0.5f, 0.5f, 0.5f, 0.5f);

        this.normalTextColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.hoverTextColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.pressedTextColor = new Vector4f(0.8f, 0.8f, 0.8f, 1.0f);
        this.disabledTextColor = new Vector4f(0.7f, 0.7f, 0.7f, 0.7f);

        // Children are created with placeholder positions/sizes, updateLayout will fix them.
        this.backgroundElement = new BoxElement(new Vector2f(0,0), new Vector2f(0,0), normalBackgroundColor);
        this.textElement = new TextElement(text, font, new Vector2f(0,0), normalTextColor, this.textScale);

        updateLayout();
        updateAppearance();
    }

    public void updateLayout() {
        if (textElement == null || backgroundElement == null) return;

        // Ensure textElement's content is up-to-date for size calculation
        textElement.setText(this.text);
        textElement.setFont(this.font);
        textElement.setScale(this.textScale);
        textElement.buildMeshIfNeeded(); // Ensures textElement.getSize() is accurate

        if (autoSize) {
            float textWidth = textElement.getSize().x;
            float textHeight = textElement.getSize().y;

            float newButtonWidth = textWidth + (2 * paddingHorizontal);
            float newButtonHeight = textHeight + (2 * paddingVertical);
            super.setSize(newButtonWidth, newButtonHeight); // Update Button's own size
        }

        backgroundElement.setSize(this.size);

        // Position textElement centered within the button.
        // Assumes textElement.position is its BASELINE.
        // Button's this.position is its TOP-LEFT.
        float buttonTopLeftX = this.position.x;
        float buttonTopLeftY = this.position.y;
        float buttonWidth = this.size.x;
        float buttonHeight = this.size.y;

        float textContentWidth = textElement.getSize().x;
        float textElementHeight = textElement.getSize().y; // This is font.getLineHeight() * textElement.getScale()

        float textBaselineX = buttonTopLeftX + (buttonWidth - textContentWidth) / 2.0f;

        // Align baseline of text such that the text block appears vertically centered.
        // Top of text block = buttonTopLeftY + (buttonHeight - textElementHeight) / 2.0f
        // Baseline Y = Top of text block + (font.getAscent() * textElement.getScale())
        // Note: font.getAscent() returns a value already scaled by font's internal scale factor for its target pixel height.
        // textElement.getScale() is an additional scale factor applied in TextElement.
        float textVisualAscent = font.getAscent() * textElement.getScale();
        float textBlockTopY = buttonTopLeftY + (buttonHeight - textElementHeight) / 2.0f;
        float textBaselineY = textBlockTopY + textVisualAscent;

        textElement.setPosition(textBaselineX, textBaselineY);
    }

    private void updateAppearance() {
        if (backgroundElement == null || textElement == null) return;

        if (isDisabled) {
            backgroundElement.setColor(disabledBackgroundColor);
            textElement.setColor(disabledTextColor);
        } else if (isPressed && mouseOver) { // only show pressed if mouse is still over
            backgroundElement.setColor(pressedBackgroundColor);
            textElement.setColor(pressedTextColor);
        } else if (mouseOver) {
            backgroundElement.setColor(hoverBackgroundColor);
            textElement.setColor(hoverTextColor);
        } else {
            backgroundElement.setColor(normalBackgroundColor);
            textElement.setColor(normalTextColor);
        }
    }
    
    // --- Getters and Setters for style ---
    public void setNormalBackgroundColor(Vector4f color) { this.normalBackgroundColor = color; updateAppearance(); }
    public void setHoverBackgroundColor(Vector4f color) { this.hoverBackgroundColor = color; updateAppearance(); }
    public void setPressedBackgroundColor(Vector4f color) { this.pressedBackgroundColor = color; updateAppearance(); }
    public void setDisabledBackgroundColor(Vector4f color) { this.disabledBackgroundColor = color; updateAppearance(); }
    public void setNormalTextColor(Vector4f color) { this.normalTextColor = color; updateAppearance(); }
    public void setHoverTextColor(Vector4f color) { this.hoverTextColor = color; updateAppearance(); }
    public void setPressedTextColor(Vector4f color) { this.pressedTextColor = color; updateAppearance(); }
    public void setDisabledTextColor(Vector4f color) { this.disabledTextColor = color; updateAppearance(); }

    public String getText() { return text; }
    public void setText(String text) {
        this.text = text;
        // textElement.setText(text); // Done in updateLayout
        updateLayout();
        updateAppearance(); // Text color might need update if text content changes appearance logic (not typical)
    }

    public Font getFont() { return font; }
    public void setFont(Font font) {
        this.font = font;
        // textElement.setFont(font); // Done in updateLayout
        updateLayout();
        updateAppearance(); // Font change might affect appearance (e.g. if colors were font-dependent)
    }

    public float getTextScale() {
        return textScale;
    }

    public void setTextScale(float textScale) {
        if (this.textScale != textScale && textScale > 0) {
            this.textScale = textScale;
            // textElement.setScale(this.textScale); // Done in updateLayout
            updateLayout();
            updateAppearance();
        }
    }

    public void setPadding(float horizontal, float vertical) {
        this.paddingHorizontal = horizontal;
        this.paddingVertical = vertical;
        updateLayout();
    }

    public void setPadding(float padding) {
        setPadding(padding, padding);
    }

    public void setOnClickAction(Runnable onClickAction) {
        this.onClickAction = onClickAction;
    }

    public boolean isDisabled() { return isDisabled; }
    public void setDisabled(boolean disabled) {
        if (this.isDisabled != disabled) {
            this.isDisabled = disabled;
            if (disabled) {
                isPressed = false; // Cannot be pressed if disabled
            }
            updateAppearance();
        }
    }

    @Override
    public void setSize(Vector2f size) {
        super.setSize(size);
        this.autoSize = false; // Explicitly setting size turns off autoSize
        updateLayout(); // Re-center text etc. within new size
    }
    
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        this.autoSize = false;
        updateLayout();
    }

    // --- Event Handlers ---
    @Override
    public void onMouseEnter() {
        super.onMouseEnter(); // Sets this.mouseOver = true
        if (!isDisabled) {
            updateAppearance();
        }
    }

    @Override
    public void onMouseLeave() {
        super.onMouseLeave(); // Sets this.mouseOver = false
        // If mouse leaves while pressed, it should visually unpress unless behavior dictates otherwise
        if (isPressed) {
            // isPressed = false; // Option 1: unpress if mouse leaves
        }
        if (!isDisabled) {
            updateAppearance();
        }
    }

    @Override
    public boolean onMouseDown(int button, float mouseX, float mouseY) {
        if (isDisabled || !isMouseOver(mouseX, mouseY)) return false; // Check isMouseOver for safety
        
        if (button == 0) { // Assuming 0 is left mouse button (GLFW.GLFW_MOUSE_BUTTON_LEFT)
            isPressed = true;
            updateAppearance();
            return true; // Event consumed
        }
        return false;
    }

    @Override
    public boolean onMouseUp(int button, float mouseX, float mouseY) {
        if (isDisabled) return false;

        if (button == 0 && isPressed) {
            boolean wasPressedAndReleasedOnElement = isPressed && isMouseOver(mouseX,mouseY);
            isPressed = false;
            updateAppearance(); // Update to hover or normal based on mouseOver state
            // Click action is handled in onClick, which UIManager calls if onMouseUp happens on the same element as onMouseDown
            return wasPressedAndReleasedOnElement; // Event consumed if it was a valid press-release sequence start
        }
        return false;
    }
    
    @Override
    public boolean onClick(int button, float mouseX, float mouseY) {
        if (isDisabled || !isMouseOver(mouseX, mouseY)) return false;

        if (button == 0) { // Left click
            if (onClickAction != null) {
                LOGGER.debug("Button '{}' clicked.", text);
                onClickAction.run();
            }
            return true; // Event consumed
        }
        return false;
    }

    @Override
    public void render(UIRenderer renderer) {
        if (!visible) return;

        // Ensure children's visual properties are up-to-date based on button state
        updateAppearance(); // Handles colors based on state (hover, pressed, disabled)

        if (backgroundElement != null && backgroundElement.isVisible()) {
            // Background fills the button. Its screen position is the button's screen position.
            backgroundElement.setPosition(this.position.x, this.position.y);
            // backgroundElement.setSize(this.size); // Size is set in updateLayout
            backgroundElement.setAlpha(this.alpha * renderer.getCurrentAlpha());
            backgroundElement.render(renderer);
        }

        if (textElement != null && textElement.isVisible() && this.text != null && !this.text.isEmpty()) {
            // textElement's position is absolute, set by updateLayout
            textElement.setAlpha(this.alpha * renderer.getCurrentAlpha());
            textElement.render(renderer);
        }
    }
    
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        // Children could have their own updates if necessary
        if (backgroundElement != null && backgroundElement.isVisible()) {
            backgroundElement.update(deltaTime);
        }
        if (textElement != null && textElement.isVisible()) {
            textElement.update(deltaTime);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        if (textElement != null) {
            textElement.cleanup();
        }
        if (backgroundElement != null) {
            backgroundElement.cleanup();
        }
        LOGGER.debug("ButtonElement '{}' cleaned up.", text);
    }

    @Override
    public void setPosition(Vector2f position) {
        super.setPosition(position);
        updateLayout();
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        updateLayout();
    }
} 