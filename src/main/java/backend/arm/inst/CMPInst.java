package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;

public class CMPInst extends AsmInst {
    public CMPInst(AsmBlock p, AsmOperand op1, AsmOperand op2) {
        parent = p;
        uses.add(op1);
        uses.add(op2);
    }

    @Override
    public String toString() {
        return String.format("CMP\t%s, %s", 
                    uses.get(0).toString(), uses.get(1).toString());
    }
}
