package backend.arm;

import backend.AsmOperand;

public class VfpReg extends AsmOperand {
    // s0-s29
    // 其中s16-s31 是callee saved
    long index;
    // useAsDouble暂时不支持
    boolean useAsDouble = false; // d0-d15
    static final long count = 32; // 寄存器数量

    public VfpReg(long index) {
        isFloat = true;
        assert index >= 0 && index < count;
        this.index = index;
    }

    public static boolean isCalleeSaved(int ind) {
        if (ind >= 16) {
            return true;
        }
        return false;
    }

    public Integer getIndex(){
        return (int)index;
    }

    @Override
    public String toString() {
        return "s"+String.valueOf(index);
    }
    // for peephole optimization
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof VfpReg)) return false;
        VfpReg other = (VfpReg)o;
        return index == other.index && useAsDouble == other.useAsDouble;
    }
}
