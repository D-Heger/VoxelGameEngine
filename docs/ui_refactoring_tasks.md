# UI Refactoring - Task Tracker

## How to Use This Task Tracker

- Each task has a unique Task ID, Name, Description, Phase, Dependencies, and optional Subtasks.
- To mark a task complete, change `[ ]` to `[x]`.
- To add a new task, copy an existing entry under the relevant phase, update details, and assign the next Task ID.
- Place subtasks under the **Subtasks:** section using their own Subtask IDs (e.g., UI-P1-T1.1). Subtasks can have Notes attached if during the implementation you find something that needs to be done later or is not implemented yet.
- Use the **Dependencies:** section to track which tasks need to be completed before starting a new task. This helps in understanding the order of implementation. Specific pieces of code or project implementations can also be listed here as dependencies.
- The **Phase:** section indicates the current phase of the UI refactoring project. This helps in organizing tasks into logical groups.
- The **Implementation Context:** property provides a brief summary of how the task was implemented, including key classes or libraries used. Logically, this can only be filled out after the task or subtask is completed.

## Phase 1: Core UI Systems (Foundation)

- [x] **Task ID:** UI-P1-T1
  - **Name:** Enhance `UIElement` Base Class
  - **Description:** Extend the existing `UIElement` class with properties and methods to support the new UI system as outlined in section 3.1 of `ui_refactor_plan.md`.
  - **Phase:** 1 - Core UI Systems
  - **Dependencies:** Existing `UIElement` class (`engine-renderer/src/main/java/de/heger/voxelengine/renderer/ui/UIElement.java`)
  - **Subtasks:**
    - [x] **Subtask ID:** UI-P1-T1.1
      - **Name:** Add Core Properties
      - **Description:** Add `parent`, `children` (List), `anchorPoint`, `positioningMode` (Enum: `ABSOLUTE`, `RELATIVE_TO_PARENT`), `margin` (Insets class), `padding` (Insets class), `minSize`, `maxSize`, `preferredSize`, `layout` (LayoutManager reference), `needsLayoutUpdate`, `needsRenderUpdate`.
    - [x] **Subtask ID:** UI-P1-T1.2
      - **Name:** Implement Core Methods
      - **Description:** Implement `addChild(UIElement child)`, `removeChild(UIElement child)`, a basic `updateLayout()` stub, `getComputedPosition()`, `getComputedSize()`, and `getBoundingBox()`.
    - [x] **Subtask ID:** UI-P1-T1.3
      - **Name:** Create `Insets` Class
      - **Description:** Implement a simple class (e.g., `Insets.java`) to handle margin and padding values (top, right, bottom, left).
  - **Implementation Context:** Created `Insets.java`, `PositioningMode.java` enum, and a placeholder `LayoutManager.java` interface. Added new properties and core methods to `UIElement.java`, including `addChild`, `removeChild`, `updateLayout` (stub), `getComputedPosition`, `getComputedSize`, and `getBoundingBox`.

- [x] **Task ID:** UI-P1-T2
  - **Name:** Implement Basic Layout Managers
  - **Description:** Create the `LayoutManager` interface and implement `VerticalListLayout` and `HorizontalListLayout` as described in section 3.2 of `ui_refactor_plan.md`.
  - **Phase:** 1 - Core UI Systems
  - **Dependencies:** UI-P1-T1
  - **Subtasks:**
    - [x] **Subtask ID:** UI-P1-T2.1
      - **Name:** Define `LayoutManager` Interface
      - **Description:** Create `LayoutManager.java` with `arrangeChildren(UIElement container, List<UIElement> children)` and `calculatePreferredSize(UIElement container, List<UIElement> children)` methods.
    - [x] **Subtask ID:** UI-P1-T2.2
      - **Name:** Implement `VerticalListLayout`
      - **Description:** Implements arranging children in a vertical column, considering spacing, child alignment, margins, and padding.
    - [x] **Subtask ID:** UI-P1-T2.3
      - **Name:** Implement `HorizontalListLayout`
      - **Description:** Implements arranging children in a horizontal row, considering spacing, child alignment, margins, and padding.
  - **Implementation Context:** Updated `LayoutManager.java` with method definitions. Created `VerticalListLayout.java` and `HorizontalListLayout.java` with alignment and spacing options, and implementations for `arrangeChildren` and `calculatePreferredSize`.

- [x] **Task ID:** UI-P1-T3
  - **Name:** Integrate Basic Layout Updates
  - **Description:** Connect the layout system to `UIElement`. Implement logic for `needsLayoutUpdate` flag and triggering `updateLayout()` which then calls the assigned `LayoutManager`.
  - **Phase:** 1 - Core UI Systems
  - **Dependencies:** UI-P1-T1, UI-P1-T2
  - **Subtasks:**
    - [x] **Subtask ID:** UI-P1-T3.1
      - **Name:** Add `setLayout(LayoutManager)` to `UIElement`.
    - [x] **Subtask ID:** UI-P1-T3.2
      - **Name:** Implement `updateLayout()` in `UIElement` to call its `LayoutManager.arrangeChildren()` and `LayoutManager.calculatePreferredSize()`.
    - [x] **Subtask ID:** UI-P1-T3.3
      - **Name:** Ensure `needsLayoutUpdate` is set correctly when relevant properties (position, size, children, text content etc.) change.
  - **Implementation Context:** Updated `UIElement.updateLayout()` to invoke layout manager methods. Ensured `needsLayoutUpdate` flag is set appropriately on property changes in `UIElement` and `TextElement` (for content changes).

- [x] **Task ID:** UI-P1-T4
  - **Name:** Enhance `BoxElement` for Layout
  - **Description:** Update `BoxElement` to function as a basic container that can utilize the new layout system. Ensure its own size can be determined by its layout manager and children.
  - **Phase:** 1 - Core UI Systems
  - **Dependencies:** UI-P1-T1, UI-P1-T3, Existing `BoxElement.java`
  - **Subtasks:**
    - [x] **Subtask ID:** UI-P1-T4.1
      - **Name:** Allow `BoxElement` to have children and a `LayoutManager`.
    - [x] **Subtask ID:** UI-P1-T4.2
      - **Name:** Ensure `BoxElement.buildMesh()` is updated if its size changes due to layout.
    - [x] **Subtask ID:** UI-P1-T4.3
      - **Name:** Refactor usages of `BoxElement` accordingly.
  - **Implementation Context:** Added a new constructor to `BoxElement` for layout-driven sizing. Updated `render()` and `update()` methods to process children. Ensured `setSize()` calls `super.setSize()` and then `buildMesh()`, covering size changes from layout. `render()` now uses `getComputedPosition()`. Analysis of existing `BoxElement` usages indicates no immediate forced refactoring, but `DebugMenu` is a candidate for later layout-driven sizing.

- [x] **Task ID:** UI-P1-T5
  - **Name:** Enhance `TextElement` for Layout
  - **Description:** Update `TextElement` to integrate with the new layout system. Its `preferredSize` should be calculated based on its text content, font, and scale.
  - **Phase:** 1 - Core UI Systems
  - **Dependencies:** UI-P1-T1, UI-P1-T3, Existing `TextElement.java`
  - **Subtasks:**
    - [x] **Subtask ID:** UI-P1-T5.1
      - **Name:** Implement `preferredSize` calculation in `TextElement`.
    - [x] **Subtask ID:** UI-P1-T5.2
      - **Name:** Ensure `TextElement.buildMesh()` is called when text or scale changes, and `needsLayoutUpdate` is flagged for its parent.
    - [x] **Subtask ID:** UI-P1-T5.3
      - **Name:** Refactor usages of `TextElement` accordingly. Take special care of `ButtonElement` and ensure its text is centered inside the buttons background box.
    - [x] **Subtask ID:** UI-P1-T5.4
      - **Name:** Ensure `PauseMenu` and `SettingsMenu` are adjusted with the new layout system, including the changes to the `ButtonElement` class.
  - **Implementation Context:** Verified that `TextElement.size` (used as preferred size by `UIElement.getPreferredSize()` if no explicit preferred size is set) is correctly calculated in `buildMeshIfNeeded()`. Setters in `TextElement` correctly flag `meshDirty` and `needsLayoutUpdate`. Modified `ButtonElement.updateLayout()` to call `textElement.buildMeshIfNeeded()` before calculations and corrected text centering logic. Changed `TextElement.buildMeshIfNeeded()` to `public`. No direct changes needed for `PauseMenu` or `SettingsMenu` for this task, as `ButtonElement`'s internal centering is now improved, and full menu layout refactoring is for UI-P3.

## Phase 2: UI Rendering and Interaction

- [x] **Task ID:** UI-P2-T1
  - **Name:** Refine `UIRenderer` Batching
  - **Description:** Improve the `UIRenderer` to handle more sophisticated batching of draw calls, grouping by shader, texture, and other OpenGL states.
  - **Phase:** 2 - UI Rendering and Interaction
  - **Dependencies:** Existing `UIRenderer.java`, UI-P1-T1
  - **Subtasks:**
    - [x] **Subtask ID:** UI-P2-T1.1
      - **Name:** Expand `isNonTexturedElement` or implement a more robust system for categorizing elements for batching.
    - [x] **Subtask ID:** UI-P2-T1.2
      - **Name:** Implement draw call grouping logic in `UIRenderer.render()`. This should reduce the number of draw calls and improve performance.
  - **Implementation Context:** Replaced the simple `isNonTexturedElement` method with a comprehensive batching system. Created `RenderState` enum with categories (SOLID_COLOR, FONT_TEXTURED, IMAGE_TEXTURED, CUSTOM) and `RenderBatch` class for grouping elements. Implemented `determineElementRenderState()` for proper element categorization and `renderElementsBatched()` with optimal rendering order. The system now minimizes OpenGL state changes by batching elements with similar rendering requirements, significantly improving performance.

- [ ] **Task ID:** UI-P2-T2
  - **Name:** Solidify Event Propagation in `UIManager`
  - **Description:** Review and enhance event propagation for mouse and keyboard events in `UIManager`. Introduce `UIEvent` objects if beneficial.
  - **Phase:** 2 - UI Rendering and Interaction
  - **Dependencies:** Existing `UIManager.java`, UI-P1-T1
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P2-T2.1
      - **Name:** Define `UIEvent` base class and specific event types (e.g., `MouseEvent`, `KeyEvent`).
    - [ ] **Subtask ID:** UI-P2-T2.2
      - **Name:** Update `UIManager` input processing to use `UIEvent` objects.
    - [ ] **Subtask ID:** UI-P2-T2.3
      - **Name:** Implement basic event bubbling (target first, then parent).
    - [ ] **Subtask ID:** UI-P2-T2.4
      - **Name:** Update existing menus to use the new event system.
      - **Description:** Update `PauseMenu` and `SettingsMenu` to use the new event system.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P2-T3
  - **Name:** Initial Font Rendering Improvements
  - **Description:** Make initial improvements to font rendering clarity. This might involve adjusting atlas generation parameters or shader techniques, without a full SDF implementation yet.
  - **Phase:** 2 - UI Rendering and Interaction
  - **Dependencies:** Existing `Font.java`, `TextElement.java`, `UIShader.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P2-T3.1
      - **Name:** Review and adjust `stbtt_BakeFontBitmap` parameters in `Font.java`.
    - [ ] **Subtask ID:** UI-P2-T3.2
      - **Name:** Experiment with shader techniques for sharper text (e.g., slight adjustments to alpha testing or blending in `ui.frag`).
    - [ ] **Subtask ID:** UI-P2-T3.3
      - **Name:** Ensure `TextElement` accurately calculates its bounding box/size based on rendered glyphs.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P2-T4
  - **Name:** Implement `UIManager` Tree Management
  - **Description:** `UIManager` should manage a list of root UI elements and facilitate traversal of the UI tree for updates and rendering.
  - **Phase:** 2 - UI Rendering and Interaction
  - **Dependencies:** UI-P1-T1, Existing `UIManager.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P2-T4.1
      - **Name:** Modify `UIManager` to hold `List<UIElement> rootElements`.
    - [ ] **Subtask ID:** UI-P2-T4.2
      - **Name:** Update `UIManager.update()` and `UIManager.render()` to traverse the tree starting from root elements.
  - **Implementation Context:** (TBD)

## Phase 3: New UI Elements and Menu Refactoring

- [ ] **Task ID:** UI-P3-T1
  - **Name:** Implement `ImageElement`
  - **Description:** Create a `UIElement` to display textures.
  - **Phase:** 3 - New UI Elements and Menu Refactoring
  - **Dependencies:** UI-P1-T1, `Texture.java`, `UIShader.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P3-T1.1
      - **Name:** Create `ImageElement.java` with properties for `Texture` and `uvRect`.
    - [ ] **Subtask ID:** UI-P3-T1.2
      - **Name:** Implement rendering logic for `ImageElement` using `UIShader`.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P3-T2
  - **Name:** Implement `ScrollableContainerElement`
  - **Description:** Create a container that supports scrolling its content. Involves clipping and scrollbar elements.
  - **Phase:** 3 - New UI Elements and Menu Refactoring
  - **Dependencies:** UI-P1-T1, UI-P1-T2, UI-P2-T1 (for `glScissor`)
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P3-T2.1
      - **Name:** Create `ScrollableContainerElement.java`.
    - [ ] **Subtask ID:** UI-P3-T2.2
      - **Name:** Implement child rendering with `glScissor` for clipping.
    - [ ] **Subtask ID:** UI-P3-T2.3
      - **Name:** Design and implement basic `ScrollBarElement`s (can be simple `BoxElement`s initially).
    - [ ] **Subtask ID:** UI-P3-T2.4
      - **Name:** Handle scroll events (`onScroll`) to adjust content offset.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P3-T3
  - **Name:** Refactor `DebugMenu`
  - **Description:** Rebuild `DebugMenu` using the new UI system, primarily with `VerticalListLayout` and `TextElement`s.
  - **Phase:** 3 - New UI Elements and Menu Refactoring
  - **Dependencies:** UI-P1-T1, UI-P1-T2, UI-P1-T4, UI-P1-T5, Existing `DebugMenu.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P3-T3.1
      - **Name:** Make `DebugMenu` a `UIElement` container (e.g., extend `BoxElement`).
    - [ ] **Subtask ID:** UI-P3-T3.2
      - **Name:** Assign a `VerticalListLayout` to the `DebugMenu` container.
    - [ ] **Subtask ID:** UI-P3-T3.3
      - **Name:** Replace manual positioning of text elements with adding them as children to the layout.
    - [ ] **Subtask ID:** UI-P3-T3.4
      - **Name:** Ensure background box auto-sizes based on content + padding.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P3-T4
  - **Name:** Refactor `PauseMenu`
  - **Description:** Rebuild `PauseMenu` using the new UI system, focusing on `VerticalListLayout` for menu items and proper centering.
  - **Phase:** 3 - New UI Elements and Menu Refactoring
  - **Dependencies:** UI-P1-T1, UI-P1-T2, UI-P1-T4, UI-P1-T5, Existing `PauseMenu.java`, Existing `ButtonElement.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P3-T4.1
      - **Name:** Create a main container for `PauseMenu` (e.g. screen-filling `BoxElement`).
    - [ ] **Subtask ID:** UI-P3-T4.2
      - **Name:** Create an inner centered container with `VerticalListLayout` for title and buttons.
    - [ ] **Subtask ID:** UI-P3-T4.3
      - **Name:** Use `anchorPoint` and `positioningMode` for centering the inner container.
    - [ ] **Subtask ID:** UI-P3-T4.4
      - **Name:** Ensure buttons are correctly sized and laid out by `VerticalListLayout`.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P3-T5
  - **Name:** Refactor `SettingsMenu`
  - **Description:** Rebuild `SettingsMenu` using new elements, `VerticalListLayout` for main structure, and `HorizontalListLayout` for individual setting rows.
  - **Phase:** 3 - New UI Elements and Menu Refactoring
  - **Dependencies:** UI-P1-T1, UI-P1-T2, UI-P1-T4, UI-P1-T5, Existing `SettingsMenu.java`, Existing `ButtonElement.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P3-T5.1
      - **Name:** `SettingsMenu` main container with `VerticalListLayout`.
    - [ ] **Subtask ID:** UI-P3-T5.2
      - **Name:** Create rows for each setting as `UIElement` containers with `HorizontalListLayout`.
    - [ ] **Subtask ID:** UI-P3-T5.3
      - **Name:** Place labels (`TextElement`) and value controls/text within these rows.
    - [ ] **Subtask ID:** UI-P3-T5.4
      - **Name:** Layout action buttons (Apply, Save, Back) at the bottom, possibly in their own centered container.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P3-T6
  - **Name:** Improve `TextInputElement`
  - **Description:** Enhance `TextInputElement` with better cursor rendering and vertical text alignment.
  - **Phase:** 3 - New UI Elements and Menu Refactoring
  - **Dependencies:** UI-P1-T1, Existing `TextInputElement.java`, `BoxElement.java`, `TextElement.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P3-T6.1
      - **Name:** Implement a blinking cursor using a thin `BoxElement` or dedicated drawing.
    - [ ] **Subtask ID:** UI-P3-T6.2
      - **Name:** Refine vertical alignment of internal `TextElement` based on `TextInputElement` height, padding and font metrics.
    - [ ] **Subtask ID:** UI-P3-T6.3
      - **Name:** Ensure text scrolls horizontally if it exceeds the input field's width (requires clipping and offset).
  - **Implementation Context:** (TBD)

## Phase 4: Advanced UI Features

- [ ] **Task ID:** UI-P4-T1
  - **Name:** Implement `GridLayout`
  - **Description:** Create `GridLayout.java` for arranging elements in a grid.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T2
  - **Subtasks:** (none)
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T2
  - **Name:** Implement `AnchorLayout` (Optional/Advanced)
  - **Description:** Create `AnchorLayout.java` for complex anchoring of children.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T2
  - **Subtasks:** (none)
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T3
  - **Name:** Implement SDF Font Rendering (Advanced)
  - **Description:** Transition to Signed Distance Field font rendering for superior text clarity.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P2-T3
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P4-T3.1
      - **Name:** Research and select/implement SDF atlas generation.
    - [ ] **Subtask ID:** UI-P4-T3.2
      - **Name:** Create a new shader for SDF text rendering.
    - [ ] **Subtask ID:** UI-P4-T3.3
      - **Name:** Update `Font.java` and `TextElement.java` to use SDF.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T4
  - **Name:** Implement Advanced Autosizing Options
  - **Description:** Add more sophisticated autosizing: text scaling to fit, containers adjusting to text.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T1, UI-P1-T5
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P4-T4.1
      - **Name:** Add option to `TextElement` to adjust scale to fit given bounds.
    - [ ] **Subtask ID:** UI-P4-T4.2
      - **Name:** Allow container `LayoutManager`s to consider text autosizing of children.
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T5
  - **Name:** Implement `ExpandableListElement`
  - **Description:** Create an accordion-style list element.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T1, UI-P1-T2, `ButtonElement.java`
  - **Subtasks:** (none)
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T6
  - **Name:** Implement `CheckboxElement`
  - **Description:** Create a standard checkbox UI element.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T1, `ImageElement.java` (optional, for checkmark) or `BoxElement.java`
  - **Subtasks:** (none)
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T7
  - **Name:** Implement `SliderElement`
  - **Description:** Create a slider UI element for selecting values within a range.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T1, `BoxElement.java`
  - **Subtasks:** (none)
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T8
  - **Name:** Add Multi-line Text Support to `TextElement`
  - **Description:** Modify `TextElement.buildMesh()` to handle `
` characters and optionally implement basic word wrapping.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T5
  - **Subtasks:** (none)
  - **Implementation Context:** (TBD)

- [ ] **Task ID:** UI-P4-T9
  - **Name:** UI Debugging Tools
  - **Description:** Implement visual debugging aids, such as rendering bounding boxes, margins, and padding for UI elements.
  - **Phase:** 4 - Advanced UI Features
  - **Dependencies:** UI-P1-T1, `UIRenderer.java`
  - **Subtasks:**
    - [ ] **Subtask ID:** UI-P4-T9.1
      - **Name:** Add a debug flag to `UIElement` or `UIManager`.
    - [ ] **Subtask ID:** UI-P4-T9.2
      - **Name:** Modify `UIElement.render()` or `UIRenderer` to draw debug outlines if flag is set.
  - **Implementation Context:** (TBD) 