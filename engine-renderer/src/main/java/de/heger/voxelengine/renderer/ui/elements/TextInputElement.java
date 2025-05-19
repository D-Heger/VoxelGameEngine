package de.heger.voxelengine.renderer.ui.elements;

import de.heger.voxelengine.renderer.ui.UIElement;
import de.heger.voxelengine.renderer.ui.UIRenderer;
import de.heger.voxelengine.renderer.ui.font.Font;
import de.heger.voxelengine.renderer.ui.UIShader;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

// For rendering the text, we'll use a TextElement internally or draw directly.
// Let's use an internal TextElement for simplicity, assuming TextElement is robust.
// If TextElement is not suitable for dynamic updates, we'd need direct text rendering logic here.
// For now, let's assume TextElement can have its text updated.

public class TextInputElement extends UIElement {

    private final de.heger.voxelengine.renderer.ui.elements.TextElement textElement; // Internal TextElement for display
    private StringBuilder currentText;
    private Font font; // Font needs to be provided or fetched from UIManager/FontManager
    private Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f); // Default white
    private Vector4f backgroundColor = new Vector4f(0.2f, 0.2f, 0.2f, 1.0f); // Default dark grey
    private Vector4f focusedBackgroundColor = new Vector4f(0.3f, 0.3f, 0.3f, 1.0f);
    private BoxElement backgroundBox; // Use BoxElement for background

    private int maxLength = 100; // Default max length
    private String allowedCharacters = null; // null means all characters allowed

    // Cursor properties (basic conceptual for now)
    private boolean showCursor = false;
    private float cursorBlinkRate = 0.5f; // seconds
    private float cursorTimer = 0.0f;
    private boolean cursorVisible = true;
    private float textPadding = 5f; // Padding for text within the box


    public TextInputElement(Font font, Vector2f position, Vector2f size) {
        super(position, size);
        this.font = font;
        this.currentText = new StringBuilder();
        
        // Calculate Y position for TextElement to be vertically centered
        // This assumes TextElement's position sets its baseline or top. 
        // For true middle, we need font metrics.
        float textElementY = position.y + (size.y / 2.0f); // Initial estimate, will adjust in update
        if (font != null) {
            // Adjust Y so text appears centered. TextElement position usually refers to baseline or top-left of text block.
            // To center text vertically, Y position for TextElement should be: TextInput.Y + (TextInput.Height / 2) - (Text.Height / 2) + Text.Ascent (if position is baseline)
            // Or TextInput.Y + (TextInput.Height - Text.Height) / 2 (if position is top of text block)
            // TextElement seems to use baseline for its y-positioning of characters in buildMesh, based on yPos in STBTT.
            // So, position.y + (size.y / 2) should place baseline at middle. If text appears too low, adjust.
            // Let's aim to place the baseline of the text at the vertical center of the TextInputElement for now.
             textElementY = position.y + (size.y / 2f) + (font.getAscent() * 0.3f / 2f); // Assuming 0.3f is the scale
        }

        // Constructor for TextElement: String text, Font font, Vector2f position, Vector4f color, float scale
        // The scale for TextElement is relative to its font size. Let's use a fixed scale for now.
        float textRenderScale = 0.3f; // Example scale for the text rendering within input
        this.textElement = new de.heger.voxelengine.renderer.ui.elements.TextElement(
            "", 
            font, 
            new Vector2f(position.x + textPadding, textElementY), // Initial position, will be updated
            textColor, 
            textRenderScale
        );
        // Removed: this.textElement.setVerticalAlignment(de.heger.voxelengine.renderer.ui.elements.TextElement.VerticalAlignment.MIDDLE);

        this.backgroundBox = new BoxElement(position, size, backgroundColor);
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onFocus() {
        super.onFocus();
        showCursor = true;
        cursorTimer = 0;
        cursorVisible = true;
        backgroundBox.setColor(focusedBackgroundColor);
        // Potentially clear selection or move cursor to end
    }

    @Override
    public void onBlur() {
        super.onBlur();
        showCursor = false;
        backgroundBox.setColor(backgroundColor);
        // Potentially finalize input or validate
    }

    @Override
    public boolean onCharTyped(char character) {
        if (currentText.length() < maxLength) {
            if (allowedCharacters == null || allowedCharacters.indexOf(character) != -1) {
                // Basic check for printable ASCII, can be expanded
                if (character >= 32 && character != 127) { // Exclude control chars except DEL
                    currentText.append(character);
                    updateTextElement();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onKeyPressed(int key, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && currentText.length() > 0) {
            currentText.deleteCharAt(currentText.length() - 1);
            updateTextElement();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER) {
            // Handle enter press - maybe fire an event?
            // LOGGER.debug("Enter pressed in TextInput: " + currentText.toString());
            return true; 
        }
        // TODO: Handle other keys like DELETE, HOME, END, ARROW_KEYS for cursor movement/selection
        return false;
    }
    
    private void updateTextElement() {
        textElement.setText(currentText.toString() + (showCursor && cursorVisible ? "|" : ""));
    }

    public String getText() {
        return currentText.toString();
    }

    public void setText(String text) {
        if (text == null) {
            currentText = new StringBuilder();
        } else {
            currentText = new StringBuilder(text.substring(0, Math.min(text.length(), maxLength)));
        }
        updateTextElement();
    }
    
    public void setTextColor(Vector4f color) {
        this.textColor = color;
        this.textElement.setColor(color);
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        if (currentText.length() > this.maxLength) {
            currentText.setLength(this.maxLength);
            updateTextElement();
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        backgroundBox.setPosition(this.position); // Ensure background follows position
        backgroundBox.setSize(this.size);         // Ensure background follows size

        // Update internal TextElement's position to be correctly aligned and padded
        if (font != null) {
            float textRenderScale = textElement.getScale(); // Get the actual scale used by TextElement
            // To vertically center the text, usually based on its baseline:
            // Center Y of TextInput = this.position.y + this.size.y / 2
            // TextElement's baseline should be at this Center Y, adjusted by half its scaled ascent if text quad y0 is from baseline upwards.
            // Or, if TextElement render places text with (0,0) at top-left of first char box:
            // textElementY = this.position.y + (this.size.y - font.getLineHeight() * textRenderScale) / 2.0f;
            // Given TextElement implementation, its own y position is likely its baseline start. So we aim to position this baseline.
            float textElementBaselineY = this.position.y + (this.size.y / 2f); 
            // If text appears too high or low, this line needs adjustment based on how TextElement positions its characters relative to its `position` field.
            // A common way to center is (BoxCenterY - TextHeight/2) + TextAscent. Let's use BoxCenterY for baseline.
            textElement.setPosition(this.position.x + textPadding, textElementBaselineY);
        }

        if (showCursor) {
            cursorTimer += deltaTime;
            if (cursorTimer >= cursorBlinkRate) {
                cursorVisible = !cursorVisible;
                cursorTimer = 0.0f;
                updateTextElement(); // Update to show/hide cursor
            }
        }
    }

    @Override
    public void render(UIRenderer renderer) {
        if (!visible) return;

        // Render background
        backgroundBox.render(renderer);

        // Render text
        // The TextElement will use its own position, color, etc.
        // UIRenderer's shader should be bound by UIRenderer itself before calling element.render
        updateTextElement(); // Ensure cursor state is reflected before render
        textElement.render(renderer);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        textElement.cleanup();
        backgroundBox.cleanup();
    }
} 