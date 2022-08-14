package backend.arm.inst;

import java.util.HashMap;
import java.util.Map;

import backend.AsmBlock;
import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.CallingConvention;
import backend.arm.LabelImm;
import backend.arm.Reg;
import backend.arm.VfpReg;

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

    // Call 指令设置约束使用更下面两个方法
    @Override
    public void setConstraint(VirtReg reg, Reg phyReg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConstraint(VirtReg reg, VfpReg phyReg) {
        throw new UnsupportedOperationException();
    }

    public void setConstraint(VirtReg reg, Reg phyReg, boolean argOrRet) {        
        if (argOrRet) {
            inConstraints.put(phyReg, reg);
        } else {
            outConstraints.put(phyReg, reg);
        }
    }

    public void setConstraint(VirtReg reg, VfpReg phyReg, boolean argOrRet) {
        if (argOrRet) {
            inConstraints.put(phyReg, reg);
        } else {
            outConstraints.put(phyReg, reg);
        }
    }

}
