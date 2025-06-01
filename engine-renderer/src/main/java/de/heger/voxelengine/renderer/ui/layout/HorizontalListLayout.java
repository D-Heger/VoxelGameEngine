package de.heger.voxelengine.renderer.ui.layout;

import de.heger.voxelengine.renderer.ui.Insets;
import de.heger.voxelengine.renderer.ui.UIElement;
import org.joml.Vector2f;

import java.util.List;

public class HorizontalListLayout implements LayoutManager {

    public enum VerticalAlignment {
        TOP,
        MIDDLE,
        BOTTOM,
        STRETCH
    }

    private float spacing = 0f;
    private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;
    // Horizontal alignment is implicitly LEFT for a horizontal list.

    public HorizontalListLayout() {}

    public HorizontalListLayout(float spacing) {
        this.spacing = spacing;
    }

    public HorizontalListLayout(float spacing, VerticalAlignment verticalAlignment) {
        this.spacing = spacing;
        this.verticalAlignment = verticalAlignment;
    }

    public float getSpacing() {
        return spacing;
    }

    public void setSpacing(float spacing) {
        this.spacing = spacing;
    }

    public VerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public void setVerticalAlignment(VerticalAlignment verticalAlignment) {
        this.verticalAlignment = verticalAlignment;
    }

    @Override
    public void arrangeChildren(UIElement container, List<UIElement> children) {
        if (container == null || children == null || children.isEmpty()) {
            return;
        }

        Insets padding = container.getPadding();
        float containerInnerHeight = container.getComputedSize().y - padding.top - padding.bottom;

        float currentX = padding.left;

        for (int i = 0; i < children.size(); i++) {
            UIElement child = children.get(i);
            if (!child.isVisible()) {
                continue;
            }

            Insets childMargin = child.getMargin();
            Vector2f childPreferredSize = child.getPreferredSize();
            Vector2f childActualSize = new Vector2f(childPreferredSize);

            if (verticalAlignment == VerticalAlignment.STRETCH) {
                childActualSize.y = containerInnerHeight - childMargin.top - childMargin.bottom;
            }
            childActualSize.x = Math.max(child.getMinSize().x, Math.min(childActualSize.x, child.getMaxSize().x));
            childActualSize.y = Math.max(child.getMinSize().y, Math.min(childActualSize.y, child.getMaxSize().y));
            child.setSize(childActualSize);

            currentX += childMargin.left;

            float childY = padding.top + childMargin.top;
            switch (verticalAlignment) {
                case TOP:
                    break;
                case MIDDLE:
                    childY = padding.top + childMargin.top + (containerInnerHeight - childMargin.top - childMargin.bottom - childActualSize.y) / 2f;
                    break;
                case BOTTOM:
                    childY = padding.top + containerInnerHeight - childMargin.bottom - childActualSize.y;
                    break;
                case STRETCH:
                    break;
            }

            child.setPosition(currentX, childY);

            currentX += childActualSize.x + childMargin.right;
            if (i < children.size() - 1) {
                currentX += spacing;
            }
        }
    }

    @Override
    public Vector2f calculatePreferredSize(UIElement container, List<UIElement> children) {
        if (container == null || children == null) {
            return new Vector2f(0, 0);
        }

        Insets padding = container.getPadding();
        float preferredWidth = padding.left + padding.right;
        float preferredHeight = 0;

        if (children.isEmpty()) {
            return new Vector2f(preferredWidth, padding.top + padding.bottom);
        }

        for (int i = 0; i < children.size(); i++) {
            UIElement child = children.get(i);
            if (!child.isVisible() && verticalAlignment != VerticalAlignment.STRETCH) {
                continue;
            }

            Insets childMargin = child.getMargin();
            Vector2f childPreferredSize = child.getPreferredSize();

            preferredWidth += childPreferredSize.x + childMargin.left + childMargin.right;
            if (i < children.size() - 1) {
                preferredWidth += spacing;
            }

            float childTotalHeight = childPreferredSize.y + childMargin.top + childMargin.bottom;
            preferredHeight = Math.max(preferredHeight, childTotalHeight);
        }

        preferredHeight += padding.top + padding.bottom;

        return new Vector2f(preferredWidth, preferredHeight);
    }
} 