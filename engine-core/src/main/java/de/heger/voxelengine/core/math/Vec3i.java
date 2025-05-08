package de.heger.voxelengine.core.math;

import java.util.Objects;

/**
 * Represents a 3-dimensional vector using integers.
 * Suitable for block coordinates, chunk coordinates, or discrete grid positions.
 * Instances of this class are immutable.
 */
public final class Vec3i {

    public int x;
    public int y;
    public int z;

    /**
     * Creates a new Vec3i instance.
     *
     * @param x The x-component.
     * @param y The y-component.
     * @param z The z-component.
     */
    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a new Vec3i instance with all components set to zero.
     */
    public Vec3i() {
        this(0, 0, 0);
    }

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Adds another vector to this vector.
     *
     * @param other The vector to add.
     * @return A new Vec3i representing the sum.
     */
    public Vec3i add(Vec3i other) {
        return new Vec3i(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    /**
     * Adds components to this vector.
     *
     * @param x The x-component to add.
     * @param y The y-component to add.
     * @param z The z-component to add.
     * @return A new Vec3i representing the sum.
     */
    public Vec3i add(int x, int y, int z) {
        return new Vec3i(this.x + x, this.y + y, this.z + z);
    }

    /**
     * Subtracts another vector from this vector.
     *
     * @param other The vector to subtract.
     * @return A new Vec3i representing the difference.
     */
    public Vec3i sub(Vec3i other) {
        return new Vec3i(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    /**
     * Subtracts components from this vector.
     *
     * @param x The x-component to subtract.
     * @param y The y-component to subtract.
     * @param z The z-component to subtract.
     * @return A new Vec3i representing the difference.
     */
    public Vec3i sub(int x, int y, int z) {
        return new Vec3i(this.x - x, this.y - y, this.z - z);
    }

    /**
     * Multiplies this vector by a scalar.
     *
     * @param scalar The scalar value.
     * @return A new Vec3i representing the scaled vector.
     */
    public Vec3i mul(int scalar) {
        return new Vec3i(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    /**
     * Divides this vector by a scalar using integer division.
     *
     * @param scalar The scalar value.
     * @return A new Vec3i representing the scaled vector.
     * @throws ArithmeticException if scalar is 0.
     */
    public Vec3i div(int scalar) {
        if (scalar == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return new Vec3i(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    /**
     * Calculates the squared Euclidean distance between this vector and another vector.
     * This is often preferred over distance() for comparisons as it avoids a square root.
     *
     * @param other The other vector.
     * @return The squared distance.
     */
    public long distanceSquared(Vec3i other) {
        long dx = (long)this.x - other.x;
        long dy = (long)this.y - other.y;
        long dz = (long)this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates the Euclidean distance between this vector and another vector.
     *
     * @param other The other vector.
     * @return The distance.
     */
    public double distance(Vec3i other) {
        return Math.sqrt(distanceSquared(other));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vec3i vec3i = (Vec3i) o;
        return x == vec3i.x && y == vec3i.y && z == vec3i.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "Vec3i(" + x + ", " + y + ", " + z + ")";
    }
}