package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.StackOperand;
import backend.arm.StackOpInst;

// VSTR{C} Fd, [Rn{, #<immed>}] imm范围0-1020
public class VLDRInst extends AsmInst implements StackOpInst {
    public VLDRInst(AsmBlock p, AsmOperand dest, AsmOperand addr) {
        parent = p;
        defs.add(dest);
        uses.add(addr);
    }

    @Override
    public String toString() {
        return String.format("VLDR\t%s, [%s]",
            defs.get(0).toString(), uses.get(0).toString());
    }

    @Override
    public boolean isImmFit(StackOperand so) {
        return VLDRInst.isImmFitStatic(so);
    }

    // `VSTR/VLDR Fd, [Rn{, #<immed>}]`  Immediate range 0-1020, multiple of 4.
    public static boolean isImmFitStatic(StackOperand so) {
        assert (so.offset % 4) == 0;
        if (so.offset >= 0 && so.offset <= 1020) {
            return true;
        }
        return false;
    }
}
