# Custom UI System Design

## 1. Overview

This document outlines the design for a basic custom UI system for the Voxel Game Engine. The initial focusThi## 9. Mouse Interaction Support (P4-T5.2) design provides a foundation for the required UI capabilities.
is on providing simple text rendering and basic layout capabilities, primarily to support a debug overlay like a performance monitor. Interaction is out of scope for the initial version.

The UI system will operate in screen space using a 2D orthographic projection.

## 2. Core Requirements

- Render text using a TrueType Font (`Roboto-Regular.ttf` is available).
- Basic layouting: Absolute positioning of UI elements.
- Minimalistic API for creating and managing UI elements.
- Integration into the existing rendering pipeline as an overlay.
- No interactive elements (buttons, input fields) in the initial version.

## 3. Proposed Modules and Classes

The UI system will reside primarily within the `engine-renderer` module, possibly in a new `de.heger.voxelengine.renderer.ui` package.

### 3.1. `FontManager`

- **Responsibilities:**
    - Loading TrueType Fonts (using LWJGL's STB TrueType bindings).
    - Generating a glyph atlas texture for efficient rendering.
    - Providing glyph information (metrics, texture coordinates) for text rendering.
- **Key Methods:**
    - `loadFont(String fontPath, float fontSize)`: Loads a font and generates its atlas.
    - `getGlyphInfo(char character)`: Returns metrics and UVs for a character.
    - `getFontAtlasTexture()`: Returns the OpenGL texture ID for the font atlas.
- **Implementation Notes:**
    - Will use `org.lwjgl.stb.STBTruetype` for font loading and glyph generation.
    - The glyph atlas will be a single texture containing rendered glyphs for a predefined character set (e.g., ASCII or common UI characters).

### 3.2. `UIShader`

- **Responsibilities:**
    - A simple shader program for rendering 2D UI elements.
    - Vertex Shader: Transforms 2D screen coordinates, passes texture coordinates.
    - Fragment Shader: Samples textures (e.g., font atlas, panel backgrounds), applies color.
- **Implementation Notes:**
    - Will be similar to other `ShaderProgram` implementations but simpler, using an orthographic projection matrix.
    - Uniforms: Projection matrix, model (transform) matrix, color, texture sampler.

### 3.3. `UIElement` (Abstract Base Class or Interface)

- **Responsibilities:**
    - Base for all UI elements.
    - Properties: Position (x, y), Size (width, height), Visibility.
    - Methods: `render(UIRenderer renderer)`, `update(float deltaTime)`.
- **Key Fields:**
    - `Vec2f position`
    - `Vec2f size`
    - `boolean visible`

### 3.4. `TextElement` (Extends `UIElement`)

- **Responsibilities:**
    - Rendering text strings.
    - Managing text content, font, color, and size.
- **Key Fields:**
    - `String text`
    - `Font font` (reference to a loaded font via `FontManager`)
    - `Color color`
- **Implementation Notes:**
    - `render()` method will iterate through characters, look up glyphs in the `FontManager`, and generate quad mesh data on the fly or use a pre-generated mesh.
    - For dynamic text, rebuilding mesh data per frame might be acceptable for small amounts of text.

### 3.5. `PanelElement` (Extends `UIElement`) (Optional for initial version, good for grouping)

- **Responsibilities:**
    - A simple rectangular container.
    - Can have a background color or texture.
    - Can contain child `UIElement`s (for basic layout).
- **Key Fields:**
    - `Color backgroundColor`
    - `List<UIElement> children`
- **Implementation Notes:**
    - `render()` would first render its background, then call `render()` on its children, applying appropriate transforms.

### 3.6. `UIRenderer`

- **Responsibilities:**
    - Managing and rendering a list of `UIElement`s.
    - Setting up the orthographic projection.
    - Handling the UI rendering pass (likely after 3D world rendering).
    - Managing the `UIShader`.
- **Key Methods:**
    - `init(Window window, FontManager fontManager)`
    - `addElement(UIElement element)`
    - `removeElement(UIElement element)`
    - `render()`: Iterates through visible UI elements and calls their `render()` methods.
    - `cleanup()`
- **Implementation Notes:**
    - The `render()` method will:
        1. Disable depth testing.
        2. Enable blending (for text and transparent panels).
        3. Set up orthographic projection (e.g., mapping window coordinates to screen space 0,0 to width,height).
        4. Bind the `UIShader`.
        5. Iterate and render UI elements.
        6. Restore GL state (enable depth testing if needed by subsequent passes).

### 3.7. `UIManager` (or integrated into `GameLoop`/`Renderer`)

- **Responsibilities:**
    - Top-level management of the UI system.
    - Initialization and cleanup of `FontManager`, `UIRenderer`.
    - Handling UI updates.
    - Providing an interface for other game systems to add/remove UI elements.

## 4. Rendering Pipeline Integration

1.  **Initialization:**
    - `FontManager` loads fonts and creates atlases.
    - `UIShader` is compiled and linked.
    - `UIRenderer` is initialized with the window dimensions and `FontManager`.
2.  **Per Frame:**
    - After the 3D scene is rendered.
    - `UIRenderer.render()` is called.
        - Sets up 2D orthographic projection.
        - Disables depth testing, enables blending.
        - Binds UI shader.
        - Renders all active UI elements.
        - Restores OpenGL state.

## 5. Text Rendering Details

-   **Font Atlas:** `FontManager` will generate a texture atlas for `Roboto-Regular.ttf`.
-   **Mesh Generation:** `TextElement` will generate a mesh (VAO/VBO) for its text.
    - Each character is a quad.
    - Vertex positions are calculated based on glyph metrics.
    - Texture coordinates map to the glyph's location in the font atlas.
-   **Batching:** For many small text elements, batching draw calls could be an optimization, but for a simple performance display, individual draw calls per text line might be acceptable initially.

## 6. Layout

-   **Absolute Positioning:** `UIElement`s will have `x` and `y` screen coordinates. The origin (0,0) could be top-left or bottom-left of the window. (Top-left is common for UI).
-   **No Complex Layouts:** Initially, no containers that automatically arrange children (like flexbox or grids). If `PanelElement` is implemented, children are positioned relative to their parent panel.

## 7. Future Considerations (Out of Scope for P4-T4.2)

-   Interactive elements (buttons, sliders).
-   Mouse input handling for UI.
-   More advanced layout managers.
-   Theming/styling.
-   Texture atlases for UI sprites/icons beyond fonts.
-   Nine-patch scaling for panels.

## 8. Connection to Other P4-T4 Subtasks

-   **P4-T4.3 (Add base custom ui class):** This design directly informs the `UIElement` and its concrete implementations like `TextElement`.
-   **P4-T4.4 (Add performance display):** The performance display will be implemented using `TextElement`s, managed by the `UIRenderer` or `UIManager`. The font rendering and basic positioning are key.
-   **P4-T4.5 (Integrate performance display):** This task will involve creating instances of the UI elements defined here and adding them to the `UIRenderer` to be drawn.

This design provides a foundation for the required UI capabilities.

# 9. Mouse Interaction Support (P4-T5.2)

This section outlines the plan for adding mouse interaction support to the UI system, which will enable UI elements to respond to mouse clicks, hover states, and scrolling events.

## 9.1. Core Event System

The interaction system will be built around a set of event classes that capture and propagate mouse interactions through the UI hierarchy.

### 9.1.1. Event Classes

- **`MouseEvent` (Abstract Base Class):**
  - **Properties:**
    - `Vec2f screenPosition`: Position in screen coordinates.
    - `long timestamp`: Time when the event occurred.
    - `boolean consumed`: Flag to mark if an event has been handled.
  - **Methods:**
    - `consume()`: Mark the event as consumed to prevent further propagation.
    - `isConsumed()`: Check if the event has been consumed.

- **`MouseButtonEvent` (Extends `MouseEvent`):**
  - **Properties:**
    - `int button`: The mouse button (LEFT, RIGHT, MIDDLE, etc.).
    - `ButtonAction action`: Enum for PRESS, RELEASE, CLICK (synthetic event).
    - `int clickCount`: For tracking double/triple clicks.

- **`MouseMoveEvent` (Extends `MouseEvent`):**
  - **Properties:**
    - `Vec2f previousPosition`: Previous mouse position for calculating delta.
    - `Vec2f delta`: Change in position since last move.

- **`MouseScrollEvent` (Extends `MouseEvent`):**
  - **Properties:**
    - `float scrollX`: Horizontal scroll amount.
    - `float scrollY`: Vertical scroll amount.

### 9.1.2. Listener Interface

- **`MouseListener` Interface:**
  - **Methods:**
    - `boolean onMouseButton(MouseButtonEvent event)`: Called on button press/release/click.
    - `boolean onMouseMove(MouseMoveEvent event)`: Called when mouse moves over element.
    - `boolean onMouseScroll(MouseScrollEvent event)`: Called when scrolling over element.
    - `void onMouseEnter()`: Called when mouse enters an element's bounds.
    - `void onMouseLeave()`: Called when mouse leaves an element's bounds.
  - Return `true` from event handlers to indicate the event was consumed.

## 9.2. Enhancements to UIElement

The `UIElement` class will be extended to support mouse interaction:

- **New Properties:**
  - `boolean interactive`: Flag to indicate if element responds to mouse events.
  - `boolean hovered`: Current hover state.
  - `boolean pressed`: Tracks if the element is currently pressed (mouse down).
  - `List<MouseListener> listeners`: Optional list of external listeners.

- **New Methods:**
  - `boolean isMouseOver(Vec2f screenPos)`: Tests if the point is within element bounds.
  - Event handling methods implementing `MouseListener` interface.
  - `addMouseListener(MouseListener listener)`: Add external listener.
  - `removeMouseListener(MouseListener listener)`: Remove external listener.

## 9.3. Input Management

The existing `InputManager` in the `engine-platform` module needs to be enhanced:

- **Additional State Tracking:**
  - Precise mouse press/release events (not just held state).
  - Mouse position changes between frames.
  - Scroll wheel deltas.

- **New Methods:**
  - `boolean isMouseButtonPressedThisFrame(int button)`: Detect single-frame press.
  - `boolean isMouseButtonReleasedThisFrame(int button)`: Detect single-frame release.
  - `Vec2f getMousePositionDelta()`: Get change in position since last frame.
  - `Vec2f getScrollDelta()`: Get scroll wheel movement since last frame.

## 9.4. Event Dispatching in UIManager

The `UIManager` class will handle event dispatching:

- **State Tracking:**
  - `UIElement hoveredElement`: Currently hovered element.
  - `UIElement pressedElement`: Element on which mouse was pressed down.

- **Event Processing in Update Method:**
  1. **Hover Detection:**
     - Iterate visible elements (top-to-bottom).
     - For each element, check `isMouseOver()`.
     - Manage hover state changes and trigger `onMouseEnter()`/`onMouseLeave()`.
  
  2. **Button Event Dispatching:**
     - On mouse down: Find element under cursor, set as pressedElement, call `onMouseButton()`.
     - On mouse up: If pressedElement exists, call its `onMouseButton()` method.
     - If mouse down and up on same element, generate click event.
  
  3. **Mouse Move Event Dispatching:**
     - If mouse moved, send `MouseMoveEvent` to hoveredElement.
  
  4. **Mouse Scroll Event Dispatching:**
     - If scroll occurred, send `MouseScrollEvent` to hoveredElement.

## 9.5. Element-Specific Interaction Implementation

Each concrete UI element class will implement interaction behavior:

- **BoxElement:**
  - Visual feedback for hover/press states (color changes).
  - Event forwarding to child elements if used as container.

- **TextElement:**
  - Optional hover/click effects (color changes, etc.).
  - Support for text selection in future iterations.

- **Button Element** (New):
  - Extends BoxElement with click handling.
  - Callback mechanism for click actions.
  - Visual states (normal, hovered, pressed).

## 9.6. Implementation Phases

1. **Phase 1 - Core Event System:**
   - Create event classes and listener interface.
   - Enhance InputManager for precise mouse event tracking.

2. **Phase 2 - UIElement Enhancements:**
   - Add interaction properties and methods to UIElement.
   - Implement isMouseOver and basic event handling.

3. **Phase 3 - UIManager Event Dispatching:**
   - Implement event routing and state tracking in UIManager.
   - Add Z-order for proper event targeting.

4. **Phase 4 - Interactive Elements:**
   - Implement hover/press visual feedback in BoxElement.
   - Implement basic Button element.
   - Add test cases in GameLoop.

5. **Phase 5 - Testing and Refinement:**
   - Validate event propagation and visual feedback.
   - Ensure proper event handling with overlapping elements.
   - Test performance impact of interaction logic.

## 9.7. Future Enhancements (Out of Scope for P4-T5.2)

- Event bubbling through element hierarchy.
- Drag and drop support.
- Focus management for keyboard navigation.
- Complex hit testing for non-rectangular elements.
- Touch input support.

This design extends the existing UI system to support mouse interaction while maintaining the clean separation of concerns established in the original design.
