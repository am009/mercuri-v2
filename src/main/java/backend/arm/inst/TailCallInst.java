package backend.arm.inst;

import backend.AsmBlock;
import backend.arm.CallingConvention;
import backend.arm.LabelImm;

/**
 * label的名字按照AsmFunc.tailCallLabel的格式设置
 */
public class TailCallInst extends CallInst {

    public TailCallInst(AsmBlock p, LabelImm f, CallingConvention cc) {
        super(p, f, cc);
    }

    @Override
    public String toString() {
        return String.format("B\t%s", target.label);
    }
}
