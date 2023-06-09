package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.StackOperand;
import backend.arm.StackOpInst;

public class StoreInst extends AsmInst implements StackOpInst {
    public StoreInst(AsmBlock p, AsmOperand val, AsmOperand addr) {
        parent = p;
        uses.add(val);
        uses.add(addr);
    }

    @Override
    public String toString() {
        return String.format("STR\t%s, [%s]",
            uses.get(0).toString(), uses.get(1).toString());
    }

    public boolean isImmFit(StackOperand so) {
        return LoadInst.isImmFitStatic(so);
    }
}
