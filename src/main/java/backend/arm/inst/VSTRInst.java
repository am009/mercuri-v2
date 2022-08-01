package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.StackOperand;
import backend.arm.StackOpInst;

public class VSTRInst extends AsmInst implements StackOpInst {
    public VSTRInst(AsmBlock p, AsmOperand val, AsmOperand addr) {
        parent = p;
        uses.add(val);
        uses.add(addr);
    }

    @Override
    public String toString() {
        return String.format("VSTR\t%s, [%s]",
            uses.get(0).toString(), uses.get(1).toString());
    }

    @Override
    public boolean isImmFit(StackOperand so) {
        return VLDRInst.isImmFitStatic(so);
    }
}
