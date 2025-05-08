package de.heger.voxelengine.core.math;

import java.util.Objects;

/**
 * Represents a 3-dimensional vector using floats.
 * Suitable for entity positions, velocities, directions.
 * Instances of this class are immutable.
 */
public final class Vec3f {

    public final float x;
    public final float y;
    public final float z;

    /**
     * Creates a new Vec3f instance.
     *
     * @param x The x-component.
     * @param y The y-component.
     * @param z The z-component.
     */
    public Vec3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Adds another vector to this vector.
     *
     * @param other The vector to add.
     * @return A new Vec3f representing the sum.
     */
    public Vec3f add(Vec3f other) {
        return new Vec3f(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    /**
     * Adds components to this vector.
     *
     * @param x The x-component to add.
     * @param y The y-component to add.
     * @param z The z-component to add.
     * @return A new Vec3f representing the sum.
     */
    public Vec3f add(float x, float y, float z) {
        return new Vec3f(this.x + x, this.y + y, this.z + z);
    }

    /**
     * Subtracts another vector from this vector.
     *
     * @param other The vector to subtract.
     * @return A new Vec3f representing the difference.
     */
    public Vec3f sub(Vec3f other) {
        return new Vec3f(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    /**
     * Subtracts components from this vector.
     *
     * @param x The x-component to subtract.
     * @param y The y-component to subtract.
     * @param z The z-component to subtract.
     * @return A new Vec3f representing the difference.
     */
    public Vec3f sub(float x, float y, float z) {
        return new Vec3f(this.x - x, this.y - y, this.z - z);
    }

    /**
     * Multiplies this vector by a scalar.
     *
     * @param scalar The scalar value.
     * @return A new Vec3f representing the scaled vector.
     */
    public Vec3f mul(float scalar) {
        return new Vec3f(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    /**
     * Divides this vector by a scalar.
     *
     * @param scalar The scalar value.
     * @return A new Vec3f representing the scaled vector.
     * @throws ArithmeticException if scalar is 0.
     */
    public Vec3f div(float scalar) {
        if (scalar == 0.0f) {
            throw new ArithmeticException("Division by zero");
        }
        return new Vec3f(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    /**
     * Calculates the squared length (magnitude) of this vector.
     *
     * @return The squared length.
     */
    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * Calculates the length (magnitude) of this vector.
     *
     * @return The length.
     */
    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    /**
     * Returns a normalized version of this vector (unit vector).
     * If the length is zero, returns a zero vector.
     *
     * @return A new Vec3f representing the normalized vector.
     */
    public Vec3f normalize() {
        float len = length();
        if (len == 0.0f) {
            return new Vec3f(0.0f, 0.0f, 0.0f); // Or throw exception? Returning zero vector is common.
        }
        return div(len);
    }

    /**
     * Calculates the dot product between this vector and another vector.
     *
     * @param other The other vector.
     * @return The dot product.
     */
    public float dot(Vec3f other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /**
     * Calculates the cross product between this vector and another vector.
     * (this x other)
     *
     * @param other The other vector.
     * @return A new Vec3f representing the cross product.
     */
    public Vec3f cross(Vec3f other) {
        float crossX = this.y * other.z - this.z * other.y;
        float crossY = this.z * other.x - this.x * other.z;
        float crossZ = this.x * other.y - this.y * other.x;
        return new Vec3f(crossX, crossY, crossZ);
    }

    /**
     * Calculates the squared Euclidean distance between this vector and another vector.
     *
     * @param other The other vector.
     * @return The squared distance.
     */
    public float distanceSquared(Vec3f other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates the Euclidean distance between this vector and another vector.
     *
     * @param other The other vector.
     * @return The distance.
     */
    public float distance(Vec3f other) {
        return (float) Math.sqrt(distanceSquared(other));
    }

    /**
     * Performs linear interpolation between this vector and another vector.
     * result = this + alpha * (other - this)
     *
     * @param other The target vector.
     * @param alpha The interpolation factor (typically between 0 and 1).
     * @return A new Vec3f representing the interpolated vector.
     */
    public Vec3f lerp(Vec3f other, float alpha) {
        float resX = this.x + alpha * (other.x - this.x);
        float resY = this.y + alpha * (other.y - this.y);
        float resZ = this.z + alpha * (other.z - this.z);
        return new Vec3f(resX, resY, resZ);
    }

    /**
     * Converts this Vec3f to a Vec3i by casting float components to integers.
     *
     * @return A new Vec3i instance.
     */
    public Vec3i toVec3i() {
        return new Vec3i((int) this.x, (int) this.y, (int) this.z);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vec3f vec3f = (Vec3f) o;
        // Use Float.compare for proper NaN and +/-0.0f handling
        return Float.compare(vec3f.x, x) == 0 &&
               Float.compare(vec3f.y, y) == 0 &&
               Float.compare(vec3f.z, z) == 0;
    }

    @Override
    public int hashCode() {
        // Use Float.floatToIntBits for consistent hashing based on bit representation
        return Objects.hash(Float.floatToIntBits(x), Float.floatToIntBits(y), Float.floatToIntBits(z));
    }

    @Override
    public String toString() {
        return "Vec3f(" + x + ", " + y + ", " + z + ")";
    }
}