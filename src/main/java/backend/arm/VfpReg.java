package backend.arm;

import backend.AsmOperand;

public class VfpReg extends AsmOperand {
    // s0-s29
    long index;
    boolean useAsDouble = false; // d0-d15

    public VfpReg(long index) {
        assert index > 0;
        this.index = index;
    }
}
