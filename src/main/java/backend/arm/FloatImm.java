package backend.arm;

public class FloatImm extends Imm {
    Float value;

    public FloatImm(Float f) {
        isFloat = true;
        value = f;

    }

    public int bitcastInt() {
        return Float.floatToRawIntBits(value);
    }

    @Override
    public long highestOneBit() {
        long val = Integer.toUnsignedLong(bitcastInt());
        return Long.highestOneBit(val);
    }

    @Override
    public IntImm getLow16() {
        return new IntImm(bitcastInt() & 0xffff);
    }

    public IntImm getHigh16() {
        // unsigned shift right
        return new IntImm(bitcastInt() >>> 16);
    }

    @Override
    public String toString() {
        return "#0x" + Long.toHexString(Integer.toUnsignedLong(bitcastInt()));
    }
}
