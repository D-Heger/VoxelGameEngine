package de.heger.voxelengine.renderer.ui.layout;

import de.heger.voxelengine.renderer.ui.UIElement;
import org.joml.Vector2f;
import java.util.List;

/**
 * Interface for layout managers.
 * Layout managers are responsible for arranging child UI elements within a container
 * and calculating the preferred size of the container based on its children.
 */
public interface LayoutManager {
    /**
     * Arranges the child UI elements within the given container element.
     * This method should set the position and size of each child element
     * based on the layout strategy.
     *
     * @param container The container UIElement whose children need to be arranged.
     * @param children  The list of child UIElement objects to arrange.
     */
    void arrangeChildren(UIElement container, List<UIElement> children);

    /**
     * Calculates the preferred size of the container element based on its children
     * and the layout strategy. This size typically represents the minimum size
     * required to fit all children according to the layout rules.
     *
     * @param container The container UIElement for which to calculate the preferred size.
     * @param children  The list of child UIElement objects to consider for size calculation.
     * @return A Vector2f representing the calculated preferred width and height of the container.
     */
    Vector2f calculatePreferredSize(UIElement container, List<UIElement> children);
} 