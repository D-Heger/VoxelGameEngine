/**
 * Thin helpers around fastutil's primitive collections.
 *
 * <p>The engine touches a lot of {@code int}s and {@code short}s &mdash; block
 * ids, coordinates, indices &mdash; and boxing every one of them into
 * {@code Integer} objects would create needless garbage on hot paths. The
 * factories in {@link de.heger.voxelengine.core.collections.FastUtilCollections}
 * hand out primitive-specialised maps, sets, and lists so the rest of the code
 * can stay fast without repeating fastutil boilerplate.</p>
 */
package de.heger.voxelengine.core.collections;
