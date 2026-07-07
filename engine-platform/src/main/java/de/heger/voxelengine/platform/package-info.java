/**
 * The bridge between the engine and the operating system's window and input.
 *
 * <p>This module keeps GLFW at arm's length so the rest of the engine never has
 * to. {@link de.heger.voxelengine.platform.Window} owns the native window and
 * the OpenGL context &mdash; creating it, resizing it, swapping buffers, and
 * tearing it down. {@link de.heger.voxelengine.platform.InputManager} translates
 * raw GLFW keyboard and mouse callbacks into simple, pollable state such as
 * "is W held?" or "how far did the mouse move this frame?".</p>
 *
 * <p>If you ever wanted to swap GLFW for another windowing backend, this is the
 * only place you would have to touch.</p>
 */
package de.heger.voxelengine.platform;
