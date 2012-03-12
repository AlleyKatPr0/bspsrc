/*
** 2012 March 12
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
*/

package info.ata4.bsplib.vector;

/**
 * An immutable four-dimensional vector class for float values.
 *
 * @author Sandern
 */
public final class Vector4f {

    // frequently used pre-defined vectors
    public static final Vector4f NULL = new Vector4f(0, 0, 0, 0);
    public static final Vector4f INVALID = new Vector4f(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    public static final Vector4f MAX_VALUE = new Vector4f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    public static final Vector4f MIN_VALUE = MAX_VALUE.scalar(-1); // don't use Float.MIN_VALUE here
    
    // number of axes, since no arrays/collections are used
    public static final int AXES = 4;
    
    // vector values
    public final float x;
    public final float y;
    public final float z;
    public final float w;

    /**
     * Default constructor, equivalent to Vector4f(0, 0, 0).
     * 
     * @deprecated Use Vector4f.NULL instead
     */
    public Vector4f() {
        this(0, 0, 0, 0);
    }

    /**
     * Replicating contructor
     * Constructs new Vector4f by copying an existing Vector4f
     * 
     * @param v The Vector4f to copy
     */
    public Vector4f(Vector4f v) {
        this(v.x, v.y, v.z, v.w);
    }
    
    /**
     * Constructs a new Vector4f using the values out of an array.
     * The array must have a size of at least 3.
     * 
     * @param v float array
     */
    public Vector4f(float[] v) {
        this(v[0], v[1], v[2], v[3]);
    }

    /**
     * Constructs a new Vector4f from x, y and z components
     * 
     * @param x the vector x component
     * @param y the vector y component
     * @param z the vector z component
     */
    public Vector4f(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
    
    /**
     * Returns the value of the n'th axis.
     * 
     * @param axis axis number
     * @return axis value
     */
    public float getAxis(int axis) {
        switch (axis) {
            case 0:
                return this.x;
            case 1:
                return this.y;
            case 2:
                return this.z;
            case 3:
                return this.w;
            default:
                return 0;
        }
    }

    /**
     * Set the value of the n'th axis.
     * 
     * @param axis axis number
     * @param value new axis value
     * @return vector with new value
     */
    public Vector4f setAxis(int axis, float value) {
        switch (axis) {
            case 0:
                return new Vector4f(value, y, z, w);
            case 1:
                return new Vector4f(x, value, z, w);
            case 2:
                return new Vector4f(x, y, value, w);
            case 3:
                return new Vector4f(x, y, z, value);
            default:
                return this;
        }
    }

    /**
     * Vector dot product: this . that
     * 
     * @param that the Vector4f to take dot product with
     * @return the dot product of the two vectors
     */
    public float dot(Vector4f that) {
        return this.x * that.x + this.y * that.y + this.z * that.z;
    }


    /**
     * Vector normalisation: ^this
     * 
     * @return the normalised vector
     */
    public Vector4f normalize() {
        float len = length();
        float rx = x / len;
        float ry = y / len;
        float rz = z / len;
        float rw = w / len;

        return new Vector4f(rx, ry, rz, rw);
    }

    /**
     * Vector addition: this + that
     * 
     * @param that The vector to add
     * @return The sum of the two vectors
     */
    public Vector4f add(Vector4f that) {
        float rx = this.x + that.x;
        float ry = this.y + that.y;
        float rz = this.z + that.z;
        float rw = this.w + that.w;

        return new Vector4f(rx, ry, rz, rw);
    }

    /**
     * Vector subtraction: this - that
     * 
     * @param that The vector to subtract
     * @return The difference of the two vectors
     */
    public Vector4f sub(Vector4f that) {
        float rx = this.x - that.x;
        float ry = this.y - that.y;
        float rz = this.z - that.z;
        float rw = this.w - that.w;
        
        return new Vector4f(rx, ry, rz, rw);
    }

    /**
     * Snap vector to nearest value: round(this / value) * value
     * 
     * @param value snap value
     * @return This vector snapped to the nearest values of 'value'
     */
    public Vector4f snap(float value) {
        float rx = Math.round(x / value) * value;
        float ry = Math.round(y / value) * value;
        float rz = Math.round(z / value) * value;
        float rw = Math.round(w / value) * value;
        
        return new Vector4f(rx, ry, rz, rw);
    }

    /**
     * Calculate the length of this vector
     * 
     * @return length of this vector
     */
    public float length() {
        return (float) Math.sqrt(Math.pow(this.x, 2) + Math.pow(this.y, 2) + Math.pow(this.z, 2) + Math.pow(this.w, 2));
    }

    /**
     * Performs a scalar multiplication on this vector: this * mul
     * 
     * @param mul multiplicator
     * @return scalar multiplied vector
     */
    public Vector4f scalar(float mul) {
        float rx = this.x * mul;
        float ry = this.y * mul;
        float rz = this.z * mul;
        float rw = this.w * mul;

        return new Vector4f(rx, ry, rz, rw);
    }
    
    /**
     * Performs a scalar multiplication on this vector: this * that
     * 
     * @param that multiplicator vector
     * @return scalar multiplied vector
     */
    public Vector4f scalar(Vector4f that) {
        float rx = this.x * that.x;
        float ry = this.y * that.y;
        float rz = this.z * that.z;
        float rw = this.w * that.w;

        return new Vector4f(rx, ry, rz, rw);
    }

    /**
     * Vector multiplication-addition: this + scale*that
     * 
     * @param scale
     * @param that
     * @return 
     */
    public Vector4f addMult(float scale, Vector4f that) {
        // same as this.add(that.scalar(scale)) with just one new instance?
        float rx = this.x + scale * that.x;
        float ry = this.y + scale * that.y;
        float rz = this.z + scale * that.z;
        float rw = this.w + scale * that.w;
        
        return new Vector4f(rx, ry, rz, rw);
    }
    
    /**
     * Returns the minima between this vector and another vector.
     * 
     * @param that other vector to compare
     * @return minima of this and that vector
     */
    public Vector4f min(Vector4f that) {
        float rx = Math.min(this.x, that.x);
        float ry = Math.min(this.y, that.y);
        float rz = Math.min(this.z, that.z);
        float rw = Math.min(this.w, that.w);
        
        return new Vector4f(rx, ry, rz, rw);
    }

    /**
     * Returns the maxima between this vector and another vector.
     * 
     * @param that other vector to compare
     * @return maxima of this and that vector
     */
    public Vector4f max(Vector4f that) {
        float rx = Math.max(this.x, that.x);
        float ry = Math.max(this.y, that.y);
        float rz = Math.max(this.z, that.z);
        float rw = Math.max(this.w, that.w);
        
        return new Vector4f(rx, ry, rz, rw);
    }
    
    /**
     * Checks if the vector has NaN values.
     * 
     * @return true if one value is NaN
     */
    public boolean isNaN() {
        return Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z) || Float.isNaN(w);
    }

    /**
     * Checks if the vector has infinite values.
     * 
     * @return true if one value is infinite
     */
    public boolean isInfinite() {
        return Float.isInfinite(x) || Float.isInfinite(y) || Float.isInfinite(z) || Float.isInfinite(w);
    }
    
    /**
     * Checks if the vector has NaN or infinite values.
     * 
     * @return true if one value is NaN or infinite
     */
    public boolean isValid() {
        return !isNaN() && !isInfinite();
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ", " + w + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Vector4f)) {
            return false;
        }

        Vector4f that = (Vector4f) obj;

        return this.x == that.x && this.y == that.y && this.z == that.z && this.w == that.w;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Float.floatToIntBits(this.x);
        hash = 89 * hash + Float.floatToIntBits(this.y);
        hash = 89 * hash + Float.floatToIntBits(this.z);
        hash = 89 * hash + Float.floatToIntBits(this.w);
        return hash;
    }

}
