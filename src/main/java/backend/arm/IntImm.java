package backend.arm;

public class IntImm extends Imm {
    // 当作Unsigned使用
    int value;

    public IntImm(int val) {
        value = val;
    }

    // https://stackoverflow.com/questions/9854166/declaring-an-unsigned-int-in-java
    // 用于对比指令的imm的范围要求。例如要求小于4096时，判断（highestOneBit() < 4095）(虽然<=2048即可)
    public long highestOneBit() {
        // highestOneBit(19) = 16
        long val = Integer.toUnsignedLong(value);
        return Long.highestOneBit(val);
    }

    public IntImm getLow16() {
        return new IntImm(value & 0xffff);
    }

    public IntImm getHigh16() {
        // unsigned shift right
        return new IntImm(value >>> 16);
    }

    @Override
    public String toString() {
        return "#0x" + Long.toHexString(Integer.toUnsignedLong(value));
    }
}
