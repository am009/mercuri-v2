package backend.arm.inst;

import backend.AsmBlock;
import backend.arm.CallingConvention;
import backend.arm.LabelImm;

public class CallInst extends ConstrainRegInst {
    public LabelImm target;
    public CallingConvention cc;

    public CallInst(AsmBlock p, LabelImm f, CallingConvention cc) {
        parent = p;
        target = f;
        this.cc = cc;
    }

    @Override
    public String toString() {
        return String.format("BL\t%s", target.label);
    }

}
