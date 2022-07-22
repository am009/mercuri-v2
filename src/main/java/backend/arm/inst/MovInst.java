package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;

public class MovInst extends AsmInst {
    public MovInst(AsmBlock p, AsmOperand from, AsmOperand to) {
        parent = p;
        uses.add(from);
        defs.add(to);
    }
}
