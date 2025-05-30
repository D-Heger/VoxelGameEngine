# UI Refactoring Plan

## 1. Introduction

This document outlines the plan to refactor the existing custom UI implementation. The goal is to create a more robust, flexible, and feature-rich UI system that integrates seamlessly with the LWJGL/OpenGL rendering pipeline. The new system will address current limitations and provide a solid foundation for future UI development.

## 2. Core Goals

The primary objectives of this refactoring effort are:

*   **Enhanced Layout Capabilities:** Implement flexible layout options including vertical lists, horizontal lists, and grids.
*   **Advanced Positioning:** Support for anchor points, relative and absolute positioning, margins, and padding.
*   **Improved Rendering:** Transition to an immediate mode style rendering approach for dynamic updates and a unified OpenGL rendering pipeline.
*   **Comprehensive Interaction:** Robust mouse and keyboard interaction with proper event propagation through a well-defined UI tree.
*   **Dynamic Sizing:** Optional autosizing capabilities for UI elements, including parents, children, and text content.
*   **Enhanced Text Rendering:** Improve font clarity and overall text rendering quality.
*   **Expanded UI Element Library:** Introduce new UI elements like `ExpandableList` and improve existing ones.
*   **Modernize Existing Menus:** Refactor `DebugMenu`, `PauseMenu`, and `SettingsMenu` to leverage the new UI system's capabilities.

## 3. Detailed Design and Implementation Plan

### 3.1. UI Element Base (`UIElement`)

The existing `UIElement` class serves as a good foundation. It should be extended or modified to support the new features.

*   **Properties to Add/Enhance:**
    *   `parent`: Reference to the parent `UIElement`.
    *   `children`: List of child `UIElements`.
    *   `anchorPoint`: `Vector2f` (values 0.0 to 1.0) defining the element's origin within its own bounds (e.g., (0.5, 0.5) for center).
    *   `positioningMode`: Enum (`ABSOLUTE`, `RELATIVE_TO_PARENT`, `RELATIVE_TO_ANCHOR`).
    *   `margin`: `Margin` class (top, right, bottom, left).
    *   `padding`: `Padding` class (top, right, bottom, left).
    *   `minSize`, `maxSize`: `Vector2f` for constraining dimensions.
    *   `preferredSize`: `Vector2f` used by layout managers.
    *   `layout`: Reference to a `LayoutManager` instance if this element is a container.
    *   `needsLayoutUpdate`: Boolean flag for dirty layout.
    *   `needsRenderUpdate`: Boolean flag for dirty rendering state.

*   **Methods to Add/Enhance:**
    *   `addChild(UIElement child)`
    *   `removeChild(UIElement child)`
    *   `updateLayout()`: Triggers recalculation of this element's and its children's bounds.
    *   `getComputedPosition()`: Calculates screen position based on parent, anchor, and positioning mode.
    *   `getComputedSize()`: Calculates size based on content, children, and layout.
    *   `getBoundingBox()`: Returns the actual screen-space bounding box.

### 3.2. Layout System

A flexible layout system is crucial. We will introduce `LayoutManager` interfaces and concrete implementations.

*   **`LayoutManager` Interface:**
    *   `arrangeChildren(UIElement container, List<UIElement> children)`: Method to position and size children within the container.
    *   `calculatePreferredSize(UIElement container, List<UIElement> children)`: Method to determine the container's preferred size based on its children.

*   **Concrete Layout Managers:**
    *   **`VerticalListLayout`:**
        *   Arranges children in a single vertical column.
        *   Parameters: spacing, child alignment (left, center, right, stretch).
        *   Considers child margins and container padding.
    *   **`HorizontalListLayout`:**
        *   Arranges children in a single horizontal row.
        *   Parameters: spacing, child alignment (top, middle, bottom, stretch).
        *   Considers child margins and container padding.
    *   **`GridLayout`:**
        *   Arranges children in a grid.
        *   Parameters: rows, columns, horizontal/vertical spacing.
        *   Considers child margins and container padding.
    *   **`AnchorLayout` (Advanced):**
        *   Allows children to be anchored to specific edges or points within the parent, with optional offsets. This is useful for more complex, non-linear layouts.

*   **Integration:**
    *   `UIElement` will have a `setLayout(LayoutManager layout)` method.
    *   When `needsLayoutUpdate` is true, the element will invoke its `LayoutManager` (if any) to rearrange children.

### 3.3. Positioning and Sizing

*   **Anchor Points:**
    *   `UIElement.anchorPoint` (e.g., (0,0) for top-left, (0.5, 0.5) for center, (1,1) for bottom-right of the element itself).
    *   When positioning an element, its `anchorPoint` is what gets placed at the calculated `position`.
*   **Relative and Absolute Positioning:**
    *   `ABSOLUTE`: `position` is screen-space coordinates (or relative to the root UI container/viewport).
    *   `RELATIVE_TO_PARENT`: `position` is an offset from the parent's top-left corner (after parent's padding is applied).
    *   `RELATIVE_TO_ANCHOR`: (More complex) `position` is relative to a specific anchor point on the parent or a sibling.
*   **Margins and Padding:**
    *   `Margin`: Space *outside* an element's border. Affects spacing between siblings.
    *   `Padding`: Space *inside* an element's border, before content or children are placed.
    *   These will be simple data classes (e.g., `Insets(float top, float right, float bottom, float left)`).
*   **Autosizing:**
    *   **Content-based (Wrap Content):** Elements (especially text, images, or containers) can calculate their `preferredSize` based on their content.
        *   `TextElement`: Based on text string, font, and scale.
        *   Containers: Based on the `LayoutManager`'s `calculatePreferredSize` method.
    *   **Parent-based (Fill Parent/Match Parent):** Elements can be set to expand to fill their parent's available space (respecting parent's padding and element's margin).
    *   **Text Autosizing:**
        *   Option for `TextElement` to adjust its scale to fit within a given width/height.
        *   Option for container elements to adjust size based on their `TextElement` children.

### 3.4. Immediate Mode Rendering and Unified Rendering Pipeline

The concept of "immediate mode" here refers to the *style* of API usage and update immediacy, not necessarily re-rendering everything from scratch every frame without optimization. The goal is to have changes reflected automatically.

*   **Dirty Flags:**
    *   `needsLayoutUpdate`: Set when position, size, children, text content, etc., change.
    *   `needsRenderUpdate`: Set when visual properties (color, texture, font, mesh data) change.
*   **Rendering Loop:**
    1.  **Update Phase:**
        *   Traverse the UI tree.
        *   If `needsLayoutUpdate` is true for an element, call `element.updateLayout()`. This will involve its `LayoutManager` if it's a container. The layout update should calculate the element's final screen position and size.
        *   The `updateLayout` should also update the underlying vertex data for elements like `BoxElement` or `TextElement` if their size/content changes.
    2.  **Render Phase (`UIRenderer`):**
        *   The `UIRenderer` will still be responsible for managing OpenGL state (shaders, blending, depth testing).
        *   It will iterate through the visible `UIElements`.
        *   Each `UIElement` will have a `render(UIRenderer renderer)` method.
        *   **Batching:**
            *   The current `isNonTexturedElement` check can be expanded.
            *   Group draw calls by shader, texture, and other relevant GL state.
            *   `TextElement` rendering will use its font atlas texture.
            *   `BoxElement` and other primitive shapes might use a "white pixel" texture or a shader that doesn't require a texture.
        *   **OpenGL Integration:**
            *   VAOs/VBOs/EBOs will still be used for each element or batches of similar elements.
            *   The `UIShader` will be the primary shader. It should handle:
                *   Textured quads (for text, images).
                *   Untextured colored quads (for boxes, backgrounds).
                *   Potentially rounded corners or other simple effects via shaders if desired.
            *   The projection matrix will be orthographic, set up by `UIRenderer`.
            *   Model matrix for each element will transform it to its screen position.

### 3.5. Interaction Support (Mouse and Keyboard)

The `UIManager`'s existing input processing is a good start.

*   **UI Tree Traversal for Input:**
    *   Input events (mouse move, click, key press) should generally be offered to the topmost element under the cursor first.
    *   The `reversedElementsCache` in `UIManager` is good for this rendering/picking order.
*   **Event Object:**
    *   Consider creating an `UIEvent` class (e.g., `MouseEvent`, `KeyEvent`) that encapsulates event details (type, position, button, key, modifiers, consumed flag).
*   **Event Propagation (Bubbling and Capturing - Optional for initial refactor):**
    *   **Bubbling (Standard):** Event is handled by the target element first. If not consumed, it's offered to its parent, and so on, up to the root.
    *   **Capturing (Less Common for UI):** Event travels from root down to the target, allowing ancestors to intercept.
    *   For the initial refactor, a direct dispatch to the `hoveredElement` or `focusedElement` as currently done is likely sufficient, with clear `return true` to consume.
*   **Focus Management (`UIManager`):**
    *   The current `focusedElement` mechanism is good.
    *   Clicking on a `focusable` element should give it focus.
    *   Pressing `Tab` could cycle focus between `focusable` elements (requires an ordered list of focusable elements).
*   **Mouse Events:**
    *   `onMouseEnter`, `onMouseLeave`, `onMouseMove`, `onMouseDown`, `onMouseUp`, `onClick`, `onScroll` in `UIElement` are well-defined.
*   **Keyboard Events:**
    *   `onCharTyped`, `onKeyPressed`, `onKeyReleased` in `UIElement` are well-defined.
    *   These should only be sent to the `focusedElement`.

### 3.6. UI Tree Structure

*   Each `UIElement` can have one parent and multiple children.
*   The `UIManager` will hold a list of root elements (elements with no parents, typically top-level windows or menus).
*   The tree structure is essential for:
    *   Layout propagation (parent affects child positions/sizes).
    *   Event propagation.
    *   Rendering order (parents typically rendered before children, or children clipped to parent bounds).

### 3.7. Font Clarity and Rendering

The current system uses `stbtt_BakeFontBitmap`.

*   **Potential Improvements:**
    *   **SDF (Signed Distance Field) Fonts:**
        *   Can provide much sharper text rendering, especially at various scales and rotations.
        *   Requires generating SDF font atlases and a specific shader. This is a significant undertaking but offers the best quality.
        *   Tools exist to generate SDF atlases from TTF files.
    *   **Subpixel Antialiasing (If applicable and desired):** More complex, platform-dependent.
    *   **Higher Resolution Atlas:** The current `DEFAULT_ATLAS_WIDTH` and `DEFAULT_ATLAS_HEIGHT` of 4096x4096 are quite large. Ensure `DEFAULT_FONT_SIZE` (64f) used for baking is appropriate for the typical display sizes. If text appears blurry, it might be due to upscaling from a small baked glyph.
    *   **Mipmapping for Font Atlas:** Can help if text is scaled down significantly, but might not be necessary if SDF is used.
    *   **Correct Glyph Positioning:** Double-check calculations in `TextElement.buildMesh` related to `STBTTBakedChar` data (`xadvance`, `xoff`, `yoff`, `ascent`, `descent`). Ensure the baseline and character spacing are accurate. The current `yPos` in `buildMesh` starts at `0.0f`. The comment `// final_y0 = q.y0(); //q.y0() already incorporates yPos and yoffset from stbtt_bakedchar` seems correct. Ensure scaled metrics are used consistently.
*   **`TextElement` Refinements:**
    *   Ensure `size` property accurately reflects the bounding box of the rendered text.
    *   Support for multi-line text (automatic line wrapping or manual `
`). This would require significant changes to `buildMesh`.

### 3.8. New and Enhanced UI Elements

*   **`ButtonElement`:**
    *   Already exists. Ensure it integrates well with new layout/sizing.
    *   Padding property should effectively control space between text and button border.
*   **`TextElement`:**
    *   Already exists. Focus on font rendering improvements and autosizing.
    *   Add multi-line support.
*   **`TextInputElement`:**
    *   Already exists.
    *   Improve cursor rendering (perhaps a thin blinking `BoxElement`).
    *   Ensure text scrolls within the input field if it exceeds the visible width.
    *   Better vertical alignment of text within the input box. The current calculation for `textElementY` could be refined by using font metrics more precisely in conjunction with the `TextInputElement`'s height and padding.
*   **`BoxElement` (Panel/Container):**
    *   This will be the base for most containers.
    *   Should be able to have a `LayoutManager`.
    *   Can act as a simple colored panel.
*   **`ImageElement`:**
    *   Displays a texture.
    *   Properties: `Texture texture`, `uvRect` (for spritesheets).
*   **`ScrollableContainerElement`:**
    *   A container that can hold content larger than its own bounds.
    *   Displays scrollbars (vertical/horizontal) when content overflows.
    *   Requires clipping child rendering to its bounds.
    *   Scrollbars themselves could be `UIElement`s.
*   **`ExpandableListElement` (Accordion):**
    *   A list where each item has a header and a content area.
    *   Clicking an item's header expands or collapses its content area.
    *   Likely a container using `VerticalListLayout`. Each item is another container.
*   **`CheckboxElement`:**
    *   A box that can be checked or unchecked.
    *   Visual state for checked/unchecked, hover.
*   **`SliderElement`:**
    *   Allows selecting a value within a range by dragging a handle.
    *   Horizontal or vertical.

### 3.9. Refactoring Existing Menus

*   **General Approach for all Menus:**
    *   Rebuild them using the new UI elements and layout managers.
    *   Replace manual position and size calculations with layout-driven arrangements.
    *   Utilize `VerticalListLayout` extensively.

*   **`DebugMenu`:**
    *   **Current State:** Manually positions many `TextElement`s, calculates background box size based on estimates.
    *   **Refactor Plan:**
        1.  The `DebugMenu` itself becomes a `UIElement` container (e.g., a `BoxElement` with a `VerticalListLayout`).
        2.  The background `BoxElement` will be part of this container or the container itself, with padding. Its size will be determined by the layout manager based on its children.
        3.  Each line of debug information (`fpsText`, `upsText`, etc.) will be a `TextElement`.
        4.  Add all these `TextElement`s as children to the main `DebugMenu` container.
        5.  The `VerticalListLayout` will handle their positioning and spacing.
        6.  Set `spacing` on the `VerticalListLayout`.
        7.  The container's `preferredSize` will be calculated by the layout, making the background box auto-size to fit the content plus padding.
        8.  `textX` and `currentTextBaselineY` calculations will be eliminated.

*   **`PauseMenu`:**
    *   **Current State:** Manually positions title text and buttons, uses `window.getWidth/Height` for centering.
    *   **Refactor Plan:**
        1.  `PauseMenu` becomes a main container `UIElement` (e.g. a `BoxElement` filling the screen, potentially with a semi-transparent background color set directly).
        2.  Inside this, another `UIElement` container (e.g., `BoxElement`) for the menu items, using a `VerticalListLayout`. This inner container can be centered on the screen.
            *   To center the inner container: Set its `anchorPoint` to (0.5, 0.5), `positioningMode` to `ABSOLUTE`, and `position` to (screenWidth/2, screenHeight/2).
        3.  `titleText` (`TextElement`) is the first child of the inner container.
        4.  `continueButton`, `settingsButton`, `quitButton` (`ButtonElement`s) are subsequent children.
        5.  The `VerticalListLayout` on the inner container will stack them with specified spacing.
        6.  Child alignment within the `VerticalListLayout` (e.g., center horizontal) can be used for the buttons.
        7.  The `updateLayout()` method for manual repositioning will be simplified or removed, relying on the layout system.

*   **`SettingsMenu`:**
    *   **Current State:** Complex manual layout calculations for labels, value texts, and buttons using columns and explicit coordinates.
    *   **Refactor Plan:**
        1.  `SettingsMenu` is a main container `UIElement`.
        2.  Use a primary `VerticalListLayout` for the overall structure.
        3.  **Title:** `titleText` as the first item.
        4.  **Settings Rows:** Each setting (VSync, Fullscreen, Resolution, View Distance) will be a `UIElement` container (e.g., `BoxElement` or a generic container) using a `HorizontalListLayout`.
            *   Example for VSync row:
                *   Parent container for the row.
                *   Child 1: `TextElement` for "VSync" label (or `ButtonElement` "VSync" if it's clickable to toggle).
                *   Child 2: `TextElement` for "ON"/"OFF" (`vsyncValueText`).
                *   The `HorizontalListLayout` will arrange these side-by-side with spacing.
                *   Consider using `preferredSize` or weights for elements in the horizontal layout to align values neatly if desired (e.g. labels take X width, values take Y width).
        5.  **Action Buttons:**
            *   A separate `UIElement` container at the bottom for `applyButton`, `saveButton`, `backButton`.
            *   This container could use `HorizontalListLayout` if buttons are side-by-side, or `VerticalListLayout` if stacked.
            *   To center this button group at the bottom:
                *   Place this group as the last item in the main `VerticalListLayout`.
                *   Alternatively, anchor it to the bottom of the `SettingsMenu` main container.

### 3.10. `UIManager` and `UIRenderer` Enhancements

*   **`UIManager`:**
    *   Manage the UI tree and root elements.
    *   Propagate `update(deltaTime)` calls.
    *   Orchestrate the layout and render passes.
    *   Input processing logic will remain largely similar but operate on the more structured UI tree and event system.
*   **`UIRenderer`:**
    *   Focus on efficient batching of draw calls.
    *   GL state management needs to be robust.
    *   Clipping support: For `ScrollableContainerElement`, `glEnable(GL_SCISSOR_TEST)` and `glScissor` will be needed. The `UIRenderer` should manage the scissor stack if elements are nested.

## 4. Phased Approach (Optional)

Consider implementing the refactor in phases:

1.  **Phase 1: Core Systems.**
    *   Refine `UIElement` base class with new properties.
    *   Implement `VerticalListLayout` and `HorizontalListLayout`.
    *   Integrate basic layout updates and dirty flags.
    *   Improve `TextElement` and `BoxElement` to work with the new layout.
2.  **Phase 2: Rendering and Interaction.**
    *   Refine `UIRenderer` for better batching.
    *   Solidify event propagation in `UIManager`.
    *   Improve font rendering (initial steps, not necessarily full SDF).
3.  **Phase 3: New Elements and Menu Refactoring.**
    *   Implement new elements (`ImageElement`, `ScrollableContainerElement`).
    *   Refactor `DebugMenu`, `PauseMenu`, `SettingsMenu` one by one.
4.  **Phase 4: Advanced Features.**
    *   `GridLayout`, `AnchorLayout`.
    *   SDF Fonts if pursued.
    *   Advanced autosizing options.
    *   `ExpandableListElement`, etc.

## 5. Potential Challenges

*   **Complexity:** Managing a UI tree, layouts, and event propagation can become complex.
*   **Performance:** Inefficient layout calculations or rendering can lead to slowdowns. Batching and dirty checking are key.
*   **Font Rendering:** Achieving crisp, scalable text is non-trivial. SDF is powerful but adds complexity.
*   **API Design:** Creating an intuitive and flexible API for UI elements and layouts.
*   **Debugging:** Visual debugging of layouts and element states can be challenging. Consider adding debug rendering modes (e.g., show bounding boxes, margins, padding).

## 6. Conclusion

This refactoring is a significant undertaking but will result in a far more capable and maintainable UI system. By focusing on a solid foundation of extensible UI elements, flexible layout managers, and an efficient rendering pipeline, the VoxelGameEngine will be well-equipped for future UI demands.