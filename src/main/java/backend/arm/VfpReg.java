package backend.arm;

import backend.AsmOperand;

public class VfpReg extends AsmOperand {
    // s0-s29
    // 其中s16-s31 是callee saved
    long index;
    // useAsDouble暂时不支持
    boolean useAsDouble = false; // d0-d15

    public VfpReg(long index) {
        assert index > 0;
        this.index = index;
    }
}
