/**
 * What a "block" is, and the registry that knows about all of them.
 *
 * <p>A block in a chunk is stored as a compact numeric id; this package is where
 * that id gets its meaning. {@link de.heger.voxelengine.world.block.BlockRegistry}
 * loads block definitions (from JSON) and hands out
 * {@link de.heger.voxelengine.world.block.BlockProperties} &mdash; is the block
 * solid, is it transparent, which textures go on which faces, and so on.
 * {@link de.heger.voxelengine.world.block.BlockDefinitionPojo} and
 * {@link de.heger.voxelengine.world.block.TextureRef} are the plain data shapes
 * those definitions deserialize into.</p>
 *
 * <p>The mesh builder and renderer consult this registry constantly, so lookups
 * are designed to be cheap and the registry itself safe to read from multiple
 * threads once populated.</p>
 */
package de.heger.voxelengine.world.block;
