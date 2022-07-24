package backend.arm.inst;

import java.util.HashMap;
import java.util.Map;

import backend.AsmInst;
import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.Reg;
import backend.arm.VfpReg;

public class ConstrainRegInst extends AsmInst {
    protected Map<VirtReg, AsmOperand> constraints = new HashMap<>();

    public Map<VirtReg, AsmOperand> getConstraints() {
        return constraints;
    }
    public void setConstraint(VirtReg reg, Reg phyReg) {
        assert !reg.isFloat;
        constraints.put(reg, phyReg);
    }

    public void setConstraint(VirtReg reg, VfpReg phyReg) {
        assert reg.isFloat;
        constraints.put(reg, phyReg);
    }
}
