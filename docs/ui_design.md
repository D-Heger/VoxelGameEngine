# Custom UI System Design

## 1. Overview

This document outlines the design for a basic custom UI system for the Voxel Game Engine. The initial focus is on providing simple text rendering and basic layout capabilities, primarily to support a debug overlay like a performance monitor. Interaction is out of scope for the initial version.

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