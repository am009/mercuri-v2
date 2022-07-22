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
}
