/**
 * A small, self-contained UI toolkit for the HUD and menus.
 *
 * <p>The engine draws its own interface rather than pulling in a UI framework.
 * {@link de.heger.voxelengine.renderer.ui.UIManager} owns the tree of
 * {@link de.heger.voxelengine.renderer.ui.UIElement}s, routes mouse and keyboard
 * events to them (hover, press, focus), and lays them out;
 * {@link de.heger.voxelengine.renderer.ui.UIRenderer} and
 * {@link de.heger.voxelengine.renderer.ui.UIShader} draw them in screen space on
 * top of the world.</p>
 *
 * <p>Supporting types like {@link de.heger.voxelengine.renderer.ui.Insets} and
 * {@link de.heger.voxelengine.renderer.ui.PositioningMode} describe spacing and
 * placement. Concrete widgets live in the
 * {@link de.heger.voxelengine.renderer.ui.elements} sub-package, layout strategies
 * in {@link de.heger.voxelengine.renderer.ui.layout}, text rendering in
 * {@link de.heger.voxelengine.renderer.ui.font}, and the assembled screens in
 * {@link de.heger.voxelengine.renderer.ui.menus}.</p>
 */
package de.heger.voxelengine.renderer.ui;
