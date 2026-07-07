/**
 * Not drawing what you cannot see.
 *
 * <p>The fastest triangle is the one you never submit. This package decides which
 * chunks are worth drawing each frame.
 * {@link de.heger.voxelengine.renderer.culling.FrustumCuller} throws out anything
 * outside the camera's view cone, while
 * {@link de.heger.voxelengine.renderer.culling.OcclusionCuller} and
 * {@link de.heger.voxelengine.renderer.culling.HZBOcclusionCuller} try to skip
 * chunks that are fully hidden behind others (the latter using a hierarchical
 * depth buffer). Getting this right is one of the biggest wins for a voxel
 * renderer, since worlds contain far more geometry than any frame can afford.</p>
 */
package de.heger.voxelengine.renderer.culling;
