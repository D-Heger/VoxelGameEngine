/**
 * Compiling GLSL shaders and feeding them data.
 *
 * <p>{@link de.heger.voxelengine.renderer.shader.ShaderProgram} handles the
 * lifecycle of an OpenGL shader program &mdash; loading vertex and fragment
 * source, compiling, linking, and setting uniforms &mdash; and is the base class
 * the engine's concrete shaders extend.
 * {@link de.heger.voxelengine.renderer.shader.UniformBuffer} wraps a Uniform
 * Buffer Object so data shared by many shaders (like the camera and lighting
 * blocks) can be uploaded once and bound cheaply.</p>
 */
package de.heger.voxelengine.renderer.shader;
