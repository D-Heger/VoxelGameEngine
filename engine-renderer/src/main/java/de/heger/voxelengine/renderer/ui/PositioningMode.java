package de.heger.voxelengine.renderer.ui;

/**
 * How a UI element interprets its position coordinates.
 */
public enum PositioningMode {
    /** Coordinates are absolute, measured from the screen's origin. */
    ABSOLUTE,
    /** Coordinates are relative to the element's parent, so moving the parent moves the child. */
    RELATIVE_TO_PARENT
    // RELATIVE_TO_ANCHOR will be added later if needed as per ui_refactor_plan.md
} 