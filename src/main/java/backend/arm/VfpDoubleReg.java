package backend.arm;

import backend.AsmOperand;

// 供临时变参函数传参转换过程中临时使用。
public class VfpDoubleReg extends AsmOperand {
    public VfpDoubleReg() {
        isFloat = true;
    }

    @Override
    public String toString() {
        return "d16";
    }
}
