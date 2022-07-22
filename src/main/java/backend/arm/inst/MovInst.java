package backend.arm.inst;

import java.util.List;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.arm.NumImm;

public class MovInst extends AsmInst {
    public enum Ty {
        REG,
        MOVW,
        MOVT;

        @Override
        public String toString() {
            switch (this) {
                case MOVT: return "MOVT";
                case MOVW: return "MOVW";
                case REG: return "MOV";
            }
            return null;
        }
    }
    Ty ty;

    public MovInst(AsmBlock p, Ty ty, AsmOperand to, AsmOperand from) {
        parent = p;
        this.ty = ty;
        defs.add(to);
        uses.add(from);
    }

    public static List<MovInst> loadImm(AsmBlock p, AsmOperand reg, NumImm imm) {
        if (imm.highestOneBit() < 65535) {
            return List.<MovInst>of(new MovInst(p, Ty.MOVW, reg, imm));
        } else {
            return List.<MovInst>of(new MovInst(p, Ty.MOVW, reg, imm.getLow16()),
                new MovInst(p, Ty.MOVT, reg, imm.getHigh16()));
        }
    }

    @Override
    public String toString() {
        return ty.toString()+"\t"+defs.get(0).toString()+", "+uses.get(0).toString();
    }
}
