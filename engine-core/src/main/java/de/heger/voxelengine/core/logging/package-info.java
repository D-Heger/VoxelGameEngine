/**
 * A tiny logging shim over SLF4J.
 *
 * <p>Rather than scatter direct SLF4J calls throughout the engine,
 * {@link de.heger.voxelengine.core.logging.LoggerFacade} wraps a logger with a
 * handful of convenience methods. It keeps the call sites terse and gives the
 * project a single seam to swap or augment logging behaviour later.</p>
 */
package de.heger.voxelengine.core.logging;
