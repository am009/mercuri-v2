package backend.arm.inst;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmInst;
import backend.VirtReg;
import backend.arm.CallingConvention;
import backend.arm.LabelImm;
import backend.arm.Reg;
import backend.arm.VfpReg;

public class CallInst extends AsmInst {
    public LabelImm target;
    public CallingConvention cc;

    public List<Map.Entry<Reg, VirtReg>> paramConstraints;
    public List<Map.Entry<VfpReg, VirtReg>> vfpParamConstraints;

    public CallInst(AsmBlock p, LabelImm f, CallingConvention cc) {
        parent = p;
        target = f;
        this.cc = cc;
        paramConstraints = new ArrayList<>();
        vfpParamConstraints = new ArrayList<>();
    }

    @Override
    public String toString() {
        return String.format("BL\t%s", target.label);
    }

}
