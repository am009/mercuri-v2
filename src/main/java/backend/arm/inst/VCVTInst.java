package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;

public class VCVTInst extends AsmInst {
    // 根据quick reference card上的条目分类
    public enum Ty {
        F2I,
        I2F,
        F2D;

        @Override
        public String toString() {
            switch (this) {
                case F2I: return "VCVT.S32.F32";
                case I2F: return "VCVT.F32.S32";
                case F2D: return "vcvt.f64.f32";
            }
            return null;
        }
    }
    Ty ty;

    public VCVTInst(AsmBlock p, Ty ty, AsmOperand to, AsmOperand from) {
        parent = p;
        this.ty = ty;
        defs.add(to);
        uses.add(from);
    }

    @Override
    public String toString() {
        return ty.toString()+"\t"+defs.get(0).toString()+", "+uses.get(0).toString();
    }
}
