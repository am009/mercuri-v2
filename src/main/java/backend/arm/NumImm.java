package backend.arm;

import backend.AsmOperand;

public class NumImm extends Imm {
    // 当作Unsigned使用
    int value;

    public NumImm(int val) {
        value = val;
    }

    // https://stackoverflow.com/questions/9854166/declaring-an-unsigned-int-in-java
    // 用于对比指令的imm的范围要求。例如要求小于4096时，判断（highestOneBit() < 4095）
    public long highestOneBit() {
        // highestOneBit(19) = 16
        long val = Integer.toUnsignedLong(value);
        return Long.highestOneBit(val);
    }

    public NumImm getLow16() {
        return new NumImm(value & 0xffff);
    }

    public NumImm getHigh16() {
        // unsigned shift right
        return new NumImm(value >>> 16);
    }

    @Override
    public String toString() {
        return "#0x" + Long.toHexString(Integer.toUnsignedLong(value));
    }
}
