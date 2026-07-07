/**
 * Procedural noise used to shape the terrain.
 *
 * <p>Contains a Java port of <a href="https://github.com/Auburn/FastNoiseLite">FastNoiseLite</a>,
 * the well-known MIT-licensed noise library. Terrain generators sample this
 * noise to decide the height of the ground, where caves might go, and other
 * organic-looking variation. The source is kept close to upstream so it can be
 * updated easily; treat it as a vendored dependency rather than hand-written
 * engine code.</p>
 */
package de.heger.voxelengine.core.noise;
