package de.heger.voxelengine.renderer.ui.layout;

import de.heger.voxelengine.renderer.ui.Insets;
import de.heger.voxelengine.renderer.ui.UIElement;
import org.joml.Vector2f;

import java.util.List;

public class VerticalListLayout implements LayoutManager {

    public enum HorizontalAlignment {
        LEFT,
        CENTER,
        RIGHT,
        STRETCH
    }

    private float spacing = 0f;
    private HorizontalAlignment horizontalAlignment = HorizontalAlignment.LEFT;
    // Vertical alignment is implicitly TOP for a vertical list.
    // STRETCH_VERTICAL would be handled by children if they want to fill height.

    public VerticalListLayout() {}

    public VerticalListLayout(float spacing) {
        this.spacing = spacing;
    }

    public VerticalListLayout(float spacing, HorizontalAlignment horizontalAlignment) {
        this.spacing = spacing;
        this.horizontalAlignment = horizontalAlignment;
    }

    public float getSpacing() {
        return spacing;
    }

    public void setSpacing(float spacing) {
        this.spacing = spacing;
    }

    public HorizontalAlignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(HorizontalAlignment horizontalAlignment) {
        this.horizontalAlignment = horizontalAlignment;
    }

    @Override
    public void arrangeChildren(UIElement container, List<UIElement> children) {
        if (container == null || children == null || children.isEmpty()) {
            return;
        }

        Insets padding = container.getPadding();
        float containerInnerWidth = container.getComputedSize().x - padding.left - padding.right;

        float currentY = padding.top;

        for (int i = 0; i < children.size(); i++) {
            UIElement child = children.get(i);
            if (!child.isVisible()) {
                continue;
            }

            Insets childMargin = child.getMargin();
            Vector2f childPreferredSize = child.getPreferredSize(); // Respect child's preference
            Vector2f childActualSize = new Vector2f(childPreferredSize);

            // Apply STRETCH if horizontalAlignment is STRETCH
            if (horizontalAlignment == HorizontalAlignment.STRETCH) {
                childActualSize.x = containerInnerWidth - childMargin.left - childMargin.right;
            }
            // Ensure size respects min/max for the child
            childActualSize.x = Math.max(child.getMinSize().x, Math.min(childActualSize.x, child.getMaxSize().x));
            childActualSize.y = Math.max(child.getMinSize().y, Math.min(childActualSize.y, child.getMaxSize().y));
            child.setSize(childActualSize); // Set the actual size for the child

            currentY += childMargin.top; // Add top margin of child

            float childX = padding.left + childMargin.left;
            switch (horizontalAlignment) {
                case LEFT:
                    // Default, childX is already correct
                    break;
                case CENTER:
                    childX = padding.left + childMargin.left + (containerInnerWidth - childMargin.left - childMargin.right - childActualSize.x) / 2f;
                    break;
                case RIGHT:
                    childX = padding.left + containerInnerWidth - childMargin.right - childActualSize.x;
                    break;
                case STRETCH:
                    // childX is already correct for STRETCH as it starts at padding.left + childMargin.left
                    break;
            }

            child.setPosition(childX, currentY);

            currentY += childActualSize.y + childMargin.bottom;
            if (i < children.size() - 1) { // Don't add spacing after the last element
                currentY += spacing;
            }
        }
        // Final Y position accounting for container's bottom padding will be handled by calculatePreferredSize indirectly
    }

    @Override
    public Vector2f calculatePreferredSize(UIElement container, List<UIElement> children) {
        if (container == null || children == null) {
            return new Vector2f(0, 0);
        }

        Insets padding = container.getPadding();
        float preferredWidth = 0;
        float preferredHeight = padding.top + padding.bottom;

        if (children.isEmpty()) {
            return new Vector2f(padding.left + padding.right, preferredHeight);
        }

        for (int i = 0; i < children.size(); i++) {
            UIElement child = children.get(i);
            if (!child.isVisible() && horizontalAlignment != HorizontalAlignment.STRETCH) { // STRETCH items still take up width space implicitly
                // For height, invisible items are currently skipped. This might need adjustment based on desired behavior.
                continue;
            }

            Insets childMargin = child.getMargin();
            Vector2f childPreferredSize = child.getPreferredSize(); // Use child's own preferred size

            // Width calculation: max of children widths (+ their horizontal margins)
            float childTotalWidth = childPreferredSize.x + childMargin.left + childMargin.right;
            preferredWidth = Math.max(preferredWidth, childTotalWidth);

            // Height calculation: sum of children heights (+ their vertical margins and spacing)
            preferredHeight += childPreferredSize.y + childMargin.top + childMargin.bottom;
            if (i < children.size() - 1) {
                preferredHeight += spacing;
            }
        }

        preferredWidth += padding.left + padding.right;

        // Ensure container preferred size respects its own min/max, if set
        // However, a layout manager primarily calculates based on content.
        // The container itself would apply its min/max to the result of this.

        return new Vector2f(preferredWidth, preferredHeight);
    }
} 