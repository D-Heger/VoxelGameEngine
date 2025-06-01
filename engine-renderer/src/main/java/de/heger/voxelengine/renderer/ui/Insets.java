package de.heger.voxelengine.renderer.ui;

public class Insets {
    public float top;
    public float right;
    public float bottom;
    public float left;

    public Insets(float top, float right, float bottom, float left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    public Insets(float topBottom, float leftRight) {
        this(topBottom, leftRight, topBottom, leftRight);
    }

    public Insets(float allSides) {
        this(allSides, allSides, allSides, allSides);
    }

    public Insets() {
        this(0, 0, 0, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Insets insets = (Insets) o;
        return Float.compare(insets.top, top) == 0 &&
               Float.compare(insets.right, right) == 0 &&
               Float.compare(insets.bottom, bottom) == 0 &&
               Float.compare(insets.left, left) == 0;
    }

    @Override
    public int hashCode() {
        int result = (top != +0.0f ? Float.floatToIntBits(top) : 0);
        result = 31 * result + (right != +0.0f ? Float.floatToIntBits(right) : 0);
        result = 31 * result + (bottom != +0.0f ? Float.floatToIntBits(bottom) : 0);
        result = 31 * result + (left != +0.0f ? Float.floatToIntBits(left) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Insets{" +
               "top=" + top +
               ", right=" + right +
               ", bottom=" + bottom +
               ", left=" + left +
               '}';
    }
} 