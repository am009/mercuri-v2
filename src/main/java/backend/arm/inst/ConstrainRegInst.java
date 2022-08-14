package backend.arm.inst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import backend.AsmInst;
import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.Reg;
import backend.arm.VfpReg;

/**
 * 子类需要重写两个setConstraint方法，根据自己的情况放到inConstraints和outConstraints中
 * 目前约束的情况是：
 * - 当函数多个参数相同的时候，单个vreg约束到了多个物理寄存器。
 * - 当函数既有参数也有返回值的时候，返回值可能在r0寄存器，第一个参数也可能在r0寄存器。所以得区分inConstraints和outConstraints。分别表示参数和返回值的约束。
 */
public class ConstrainRegInst extends AsmInst {
    // 区分是参数的约束还是返回值约束
    protected Map<AsmOperand, VirtReg> inConstraints = new HashMap<>();
    protected Map<AsmOperand, VirtReg> outConstraints = new HashMap<>();

    public List<Map.Entry<AsmOperand, VirtReg>> getConstraints() {
        List<Map.Entry<AsmOperand, VirtReg>> ret = new ArrayList<>();
        ret.addAll(inConstraints.entrySet());
        ret.addAll(outConstraints.entrySet());
        return ret;
    }

    public Map<AsmOperand, VirtReg> getInConstraints() {
        return inConstraints;
    }

    public Map<AsmOperand, VirtReg> getOutConstraints() {
        return outConstraints;
    }

    public void setConstraint(VirtReg reg, Reg phyReg) {
        assert !reg.isFloat;
        throw new UnsupportedOperationException();
    }

    public void setConstraint(VirtReg reg, VfpReg phyReg) {
        assert reg.isFloat;
        throw new UnsupportedOperationException();
    }
}
