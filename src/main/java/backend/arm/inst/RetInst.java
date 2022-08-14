package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.VirtReg;
import backend.arm.Reg;
import backend.arm.VfpReg;

// 代表了需要插入epilogue和返回的抽象指令
// use可能为空，代表空的return，也可能返回一个值
public class RetInst extends ConstrainRegInst {
    // 寄存器分配的约束-返回值

    // 所在函数
    AsmFunc func;

    public RetInst(AsmBlock p, AsmFunc f) {
        func = f;
        parent = p;
    }

    public static String format = "mov\tsp, fp\t@ %s\n"
                                    + "\tpop\t{fp, lr}\n"
                                    + "\tbx\tlr";

    @Override
    public String toString() {
        String comment;
        if (uses.size() > 0) {
            comment = "ret " + uses.get(0).toString();
        } else {
            comment = "ret void";
        }
        return String.format(format, comment);
    }

    @Override
    public void setConstraint(VirtReg reg, Reg phyReg) {
        assert !reg.isFloat;
        inConstraints.put(phyReg, reg);
    }

    @Override
    public void setConstraint(VirtReg reg, VfpReg phyReg) {
        assert reg.isFloat;
        inConstraints.put(phyReg, reg);
    }
}
