/**
 * Turning text into textured quads.
 *
 * <p>Text rendering is bitmap-font based. {@link de.heger.voxelengine.renderer.ui.font.Font}
 * bakes a TrueType font into a glyph atlas (using STB) and records where each
 * character sits, {@link de.heger.voxelengine.renderer.ui.font.GlyphInfo} holds
 * the per-character metrics needed to place and size a glyph, and
 * {@link de.heger.voxelengine.renderer.ui.font.FontManager} loads and caches fonts
 * so the UI can ask for one by name.</p>
 */
package de.heger.voxelengine.renderer.ui.font;
