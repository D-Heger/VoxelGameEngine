package de.heger.voxelengine.renderer.management;

import de.heger.voxelengine.renderer.shader.ShaderProgram;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

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

    // OPTIMIZATION: Configurable threshold - increased from 0.001f to 0.01f for better performance
    private static final float DEFAULT_TIME_CALCULATION_THRESHOLD = 0.01f;
    private float timeCalculationThreshold = DEFAULT_TIME_CALCULATION_THRESHOLD;

    // OPTIMIZATION: Sin calculation cache for common time values
    private static final int SIN_CACHE_SIZE = 360; // Cache for 360 steps (1 degree precision)
    private static final float[] SIN_CACHE = new float[SIN_CACHE_SIZE];
    private static boolean sinCacheInitialized = false;

    // OPTIMIZATION: Profiling metrics
    private long totalUpdateCalls = 0;
    private long actualRecalculations = 0;
    private long cacheHits = 0;
    private double totalRecalculationTime = 0.0;
    private long lastResetTime = System.currentTimeMillis();

    static {
        initializeSinCache();
    }

    /**
     * Initializes the sin calculation cache for performance optimization.
     */
    private static void initializeSinCache() {
        if (sinCacheInitialized) return;
        
        for (int i = 0; i < SIN_CACHE_SIZE; i++) {
            float normalizedTime = (float) i / SIN_CACHE_SIZE;
            SIN_CACHE[i] = (float) Math.sin(normalizedTime * Math.PI);
        }
        sinCacheInitialized = true;
    }

    /**
     * Gets cached sin value for the given normalized time of day.
     * @param timeOfDay Normalized time from 0.0 to 1.0
     * @return Cached sin value
     */
    private float getCachedSin(float timeOfDay) {
        int index = (int) (timeOfDay * (SIN_CACHE_SIZE - 1));
        index = Math.max(0, Math.min(SIN_CACHE_SIZE - 1, index));
        cacheHits++;
        return SIN_CACHE[index];
    }

    /**
     * Updates the time of day.
     * @param timeOfDay The new time of day, from 0.0 (midnight) to 1.0 (next midnight).
     */
    public void updateTimeOfDay(float timeOfDay) {
        this.currentTimeOfDay = timeOfDay % 1.0f; // Ensure it wraps around
    }

    /**
     * Sets the threshold for time calculation updates.
     * @param threshold The new threshold value (recommended range: 0.001f to 0.1f)
     */
    public void setTimeCalculationThreshold(float threshold) {
        this.timeCalculationThreshold = Math.max(0.0001f, Math.min(0.1f, threshold));
    }

    /**
     * Gets the current time calculation threshold.
     * @return The current threshold value
     */
    public float getTimeCalculationThreshold() {
        return timeCalculationThreshold;
    }

    /**
     * Recalculates lighting and fog values if the time of day has changed significantly.
     * This should be called once per frame before rendering.
     */
    public void update() {
        totalUpdateCalls++;
        
        if (Math.abs(currentTimeOfDay - lastCalculatedTimeOfDay) < timeCalculationThreshold) {
            return; // Skip recalculation if time hasn't changed enough
        }

        // OPTIMIZATION: Profile recalculation time
        long startTime = System.nanoTime();

        // OPTIMIZATION: Use cached sin calculation instead of Math.sin
        float lightIntensityFactor = getCachedSin(this.currentTimeOfDay);
        lightIntensityFactor = Math.max(0.0f, Math.min(1.0f, lightIntensityFactor));

        // Interpolate light properties
        interpolateVector(midnightLightColor, middayLightColor, lightIntensityFactor, cachedLightColor);
        cachedAmbientStrength = midnightAmbientStrength + (middayAmbientStrength - midnightAmbientStrength) * lightIntensityFactor;
        interpolateVector(midnightAmbientColor, middayAmbientColor, lightIntensityFactor, cachedAmbientColor);
        interpolateVector(midnightFogColor, middayFogColor, lightIntensityFactor, cachedFogColor);

        lastCalculatedTimeOfDay = currentTimeOfDay;
        actualRecalculations++;

        // OPTIMIZATION: Track recalculation time
        long endTime = System.nanoTime();
        totalRecalculationTime += (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
    }

    /**
     * Fills the provided ByteBuffer with lighting and fog data for the UBO.
     * The buffer should have a capacity of 80 bytes for the Lighting UBO.
     * <p>
     * UBO Layout (std140):
     * - vec3 lightDir (12) + pad (4)
     * - vec3 lightColor (12) + pad (4)
     * - vec3 ambientColor (12)
     * - float ambientStrength (4)
     * - vec3 fogColor (12)
     * - float fogStart (4)
     * - float fogEnd (4)
     * - float pad (4)
     * </p>
     * @param buffer The ByteBuffer to fill.
     * @param viewDistance The current camera view distance.
     */
    public void fillLightingUboBuffer(ByteBuffer buffer, float viewDistance) {
        if (buffer.capacity() < 80) {
            throw new IllegalArgumentException("ByteBuffer capacity is less than the required 80 bytes for Lighting UBO.");
        }
        buffer.clear();

        // Light Dir
        DEFAULT_LIGHT_DIR.get(buffer);
        buffer.position(buffer.position() + 12);
        buffer.putFloat(0.0f); // Padding

        // Light Color
        cachedLightColor.get(buffer);
        buffer.position(buffer.position() + 12);
        buffer.putFloat(0.0f); // Padding

        // Ambient Color & Strength
        cachedAmbientColor.get(buffer);
        buffer.position(buffer.position() + 12);
        buffer.putFloat(cachedAmbientStrength);

        // Fog Color & Range
        float fogStartDistance = viewDistance * 0.60f;
        cachedFogColor.get(buffer);
        buffer.position(buffer.position() + 12);
        buffer.putFloat(fogStartDistance);
        buffer.putFloat(viewDistance);
        buffer.putFloat(0.0f); // Padding

        buffer.flip();
    }

    /**
     * @return The current color of the fog.
     */
    public Vector3f getFogColor() {
        return cachedFogColor;
    }

    /**
     * Gets performance metrics for the lighting manager.
     * @return Performance metrics as a formatted string
     */
    public String getPerformanceMetrics() {
        if (totalUpdateCalls == 0) {
            return "SceneLighting: No data available";
        }
        
        double recalculationRate = (double) actualRecalculations / totalUpdateCalls * 100.0;
        double avgRecalcTime = actualRecalculations > 0 ? totalRecalculationTime / actualRecalculations : 0.0;
        long elapsedSeconds = (System.currentTimeMillis() - lastResetTime) / 1000;
        
        return String.format("SceneLighting: %.1f%% recalc rate, %.3fms avg time, %d cache hits, %ds elapsed",
                recalculationRate, avgRecalcTime, cacheHits, elapsedSeconds);
    }

    /**
     * Gets the recalculation rate as a percentage.
     * @return Percentage of update calls that resulted in actual recalculation
     */
    public double getRecalculationRate() {
        return totalUpdateCalls > 0 ? (double) actualRecalculations / totalUpdateCalls * 100.0 : 0.0;
    }

    /**
     * Gets the average recalculation time in milliseconds.
     * @return Average time spent on recalculations
     */
    public double getAverageRecalculationTimeMs() {
        return actualRecalculations > 0 ? totalRecalculationTime / actualRecalculations : 0.0;
    }

    /**
     * Gets the total number of cache hits.
     * @return Number of times cached sin values were used
     */
    public long getCacheHits() {
        return cacheHits;
    }

    /**
     * Resets all performance metrics.
     */
    public void resetPerformanceMetrics() {
        totalUpdateCalls = 0;
        actualRecalculations = 0;
        cacheHits = 0;
        totalRecalculationTime = 0.0;
        lastResetTime = System.currentTimeMillis();
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

    public Vector3f getLightDirection() {
        return DEFAULT_LIGHT_DIR;
    }
} 