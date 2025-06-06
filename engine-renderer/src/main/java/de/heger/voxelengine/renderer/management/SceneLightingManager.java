package de.heger.voxelengine.renderer.management;

import de.heger.voxelengine.renderer.shader.ShaderProgram;
import org.joml.Vector3f;

/**
 * Manages scene lighting and fog, which change based on the time of day.
 */
public class SceneLightingManager {

    private float currentTimeOfDay = 0.5f; // 0.0 (midnight) to 1.0 (next midnight), 0.5 is midday
    private float lastCalculatedTimeOfDay = -1.0f;

    // Static light direction
    private static final Vector3f DEFAULT_LIGHT_DIR = new Vector3f(-0.5f, -1.0f, -0.5f);

    // Define light properties for key times
    private final Vector3f middayLightColor = new Vector3f(1.0f, 0.95f, 0.8f);
    private final Vector3f midnightLightColor = new Vector3f(0.05f, 0.05f, 0.15f); // Bluish moonlight
    private final float middayAmbientStrength = 0.8f;
    private final float midnightAmbientStrength = 0.15f;
    private final Vector3f middayAmbientColor = new Vector3f(0.6f, 0.6f, 0.6f);
    private final Vector3f midnightAmbientColor = new Vector3f(0.05f, 0.05f, 0.1f);

    // Define fog colors for key times
    private final Vector3f middayFogColor = new Vector3f(0.5f, 0.6f, 0.7f);
    private final Vector3f midnightFogColor = new Vector3f(0.0f, 0.0f, 0.05f);

    // Cached values to reduce recalculation
    private final Vector3f cachedLightColor = new Vector3f();
    private final Vector3f cachedAmbientColor = new Vector3f();
    private final Vector3f cachedFogColor = new Vector3f();
    private float cachedAmbientStrength = 0.0f;

    private static final float TIME_CALCULATION_THRESHOLD = 0.001f;

    /**
     * Updates the time of day.
     * @param timeOfDay The new time of day, from 0.0 (midnight) to 1.0 (next midnight).
     */
    public void updateTimeOfDay(float timeOfDay) {
        this.currentTimeOfDay = timeOfDay % 1.0f; // Ensure it wraps around
    }

    /**
     * Recalculates lighting and fog values if the time of day has changed significantly.
     * This should be called once per frame before rendering.
     */
    public void update() {
        if (Math.abs(currentTimeOfDay - lastCalculatedTimeOfDay) < TIME_CALCULATION_THRESHOLD) {
            return; // Skip recalculation if time hasn't changed enough
        }

        // Calculate time-based light intensity (0 at midnight, 1 at midday)
        float lightIntensityFactor = (float) Math.sin(this.currentTimeOfDay * Math.PI);
        lightIntensityFactor = Math.max(0.0f, Math.min(1.0f, lightIntensityFactor));

        // Interpolate light properties
        interpolateVector(midnightLightColor, middayLightColor, lightIntensityFactor, cachedLightColor);
        cachedAmbientStrength = midnightAmbientStrength + (middayAmbientStrength - midnightAmbientStrength) * lightIntensityFactor;
        interpolateVector(midnightAmbientColor, middayAmbientColor, lightIntensityFactor, cachedAmbientColor);
        interpolateVector(midnightFogColor, middayFogColor, lightIntensityFactor, cachedFogColor);

        lastCalculatedTimeOfDay = currentTimeOfDay;
    }

    /**
     * Applies the calculated lighting and fog uniforms to the given shader program.
     * @param shader The shader program to update.
     * @param viewDistance The current camera view distance, used to calculate fog range.
     */
    public void applyUniforms(ShaderProgram shader, float viewDistance) {
        shader.setUniform("lightDir", DEFAULT_LIGHT_DIR);
        shader.setUniform("lightColor", cachedLightColor);
        shader.setUniform("ambientColor", cachedAmbientColor);
        shader.setUniform("ambientStrength", cachedAmbientStrength);

        float fogStartDistance = viewDistance * 0.60f;
        shader.setUniform("fogColor", cachedFogColor);
        shader.setUniform("fogStart", fogStartDistance);
        shader.setUniform("fogEnd", viewDistance);
    }

    /**
     * @return The current color of the fog.
     */
    public Vector3f getFogColor() {
        return cachedFogColor;
    }

    /**
     * Linearly interpolates between two vectors.
     * @param start The start vector.
     * @param end The end vector.
     * @param factor The interpolation factor (0.0 to 1.0).
     * @param result The vector to store the result in.
     */
    private void interpolateVector(Vector3f start, Vector3f end, float factor, Vector3f result) {
        result.x = start.x + (end.x - start.x) * factor;
        result.y = start.y + (end.y - start.y) * factor;
        result.z = start.z + (end.z - start.z) * factor;
    }
} 