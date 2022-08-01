package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.arm.Imm;
import backend.arm.ImmOpInst;
import backend.arm.Operand2;

public class CMPInst extends AsmInst implements ImmOpInst {
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

    public boolean isImmFit(Imm m) {
        return Operand2.isImmFit(m);
    }
}
