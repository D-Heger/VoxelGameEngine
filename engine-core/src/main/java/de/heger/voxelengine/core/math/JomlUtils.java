package de.heger.voxelengine.core.math;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;

/**
 * A static utility class for JOML-related operations, particularly
 * converting between engine vector types (Vec3f, Vec3i) and JOML vector types.
 */
public final class JomlUtils {

    // Private constructor to prevent instantiation
    private JomlUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // --- Vec3f to JOML Vector3f ---

    /**
     * Converts an engine Vec3f to a JOML Vector3f, storing the result in the destination vector.
     *
     * @param vec  The source engine vector.
     * @param dest The destination JOML vector to modify.
     * @return The modified destination vector.
     */
    public static Vector3f toJoml(Vec3f vec, Vector3f dest) {
        dest.x = vec.x;
        dest.y = vec.y;
        dest.z = vec.z;
        return dest;
    }

    /**
     * Converts an engine Vec3f to a new JOML Vector3f.
     *
     * @param vec The source engine vector.
     * @return A new JOML Vector3f instance.
     */
    public static Vector3f toJoml(Vec3f vec) {
        return new Vector3f(vec.x, vec.y, vec.z);
    }

    // --- JOML Vector3fc to Vec3f ---

    /**
     * Converts a JOML Vector3fc (read-only) to an engine Vec3f, storing the result in the destination vector.
     *
     * @param jomlVec The source JOML vector.
     * @param dest    The destination engine vector to modify (Note: Vec3f is immutable, so this isn't typical usage, but provided for completeness if needed elsewhere).
     *                Consider using the version returning a new Vec3f instead.
     * @return The *original* destination vector reference (as Vec3f is immutable, a new one isn't created here).
     *         This method signature might be misleading due to Vec3f's immutability.
     *         It's generally better to use `fromJoml(Vector3fc)` which returns a new Vec3f.
     */
     @Deprecated // Mark as deprecated due to potential confusion with immutable Vec3f
     public static Vec3f fromJoml(Vector3fc jomlVec, Vec3f dest) {
         // Since Vec3f is immutable, we cannot modify 'dest'.
         // This method doesn't make much sense in its current form for immutable Vec3f.
         // Returning a new instance is the correct pattern.
         // If modification was intended, Vec3f would need to be mutable.
         // For now, just return the original 'dest' to fulfill the signature,
         // but strongly recommend using the other fromJoml method.
         System.err.println("Warning: JomlUtils.fromJoml(Vector3fc, Vec3f) called. Due to Vec3f immutability, the 'dest' parameter is not modified. Use fromJoml(Vector3fc) instead.");
         return dest; // Does not actually modify dest
     }


    /**
     * Converts a JOML Vector3fc (read-only) to a new engine Vec3f.
     *
     * @param jomlVec The source JOML vector.
     * @return A new engine Vec3f instance.
     */
    public static Vec3f fromJoml(Vector3fc jomlVec) {
        return new Vec3f(jomlVec.x(), jomlVec.y(), jomlVec.z());
    }

    // --- Vec3i to JOML Vector3i ---

    /**
     * Converts an engine Vec3i to a JOML Vector3i, storing the result in the destination vector.
     *
     * @param vec  The source engine vector.
     * @param dest The destination JOML vector to modify.
     * @return The modified destination vector.
     */
    public static Vector3i toJoml(Vec3i vec, Vector3i dest) {
        dest.x = vec.x;
        dest.y = vec.y;
        dest.z = vec.z;
        return dest;
    }

    /**
     * Converts an engine Vec3i to a new JOML Vector3i.
     *
     * @param vec The source engine vector.
     * @return A new JOML Vector3i instance.
     */
    public static Vector3i toJoml(Vec3i vec) {
        return new Vector3i(vec.x, vec.y, vec.z);
    }

    // --- JOML Vector3ic to Vec3i ---

    /**
     * Converts a JOML Vector3ic (read-only) to an engine Vec3i, storing the result in the destination vector.
     *
     * @param jomlVec The source JOML vector.
     * @param dest    The destination engine vector to modify (Note: Vec3i is immutable).
     *                Consider using the version returning a new Vec3i instead.
     * @return The *original* destination vector reference.
     */
     @Deprecated // Mark as deprecated due to potential confusion with immutable Vec3i
     public static Vec3i fromJoml(Vector3ic jomlVec, Vec3i dest) {
         // Similar issue as with Vec3f due to immutability.
         System.err.println("Warning: JomlUtils.fromJoml(Vector3ic, Vec3i) called. Due to Vec3i immutability, the 'dest' parameter is not modified. Use fromJoml(Vector3ic) instead.");
         return dest; // Does not actually modify dest
     }

    /**
     * Converts a JOML Vector3ic (read-only) to a new engine Vec3i.
     *
     * @param jomlVec The source JOML vector.
     * @return A new engine Vec3i instance.
     */
    public static Vec3i fromJoml(Vector3ic jomlVec) {
        return new Vec3i(jomlVec.x(), jomlVec.y(), jomlVec.z());
    }
}