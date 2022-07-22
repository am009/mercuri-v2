package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;

public class BXInst extends AsmInst {
    public BXInst(AsmBlock p, AsmOperand op) {
        parent = p;
        uses.add(op);
    }
}
