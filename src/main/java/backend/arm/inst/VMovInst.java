package backend.arm.inst;

import java.util.StringJoiner;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;

// 未来需要兼顾移动两个寄存器的情况
public class VMovInst extends AsmInst {
    // 根据quick reference card上的条目分类
    public enum Ty {
        CPY,
        A2S,
        S2A;

        @Override
        public String toString() {
            switch (this) {
                case CPY:
                case A2S:
                case S2A: return "VMOV";
            }
            return null;
        }
    }
    Ty ty;
    // public Cond cond; // TODO FPSCR的cond是否需要单独的类

    public VMovInst(AsmBlock p, Ty ty, AsmOperand to, AsmOperand from) {
        parent = p;
        this.ty = ty;
        defs.add(to);
        uses.add(from);
        // cond = Cond.AL;
    }

    @Override
    public String toString() {
        var sj = new StringJoiner(", ");
        defs.forEach(d -> sj.add(d.toString()));
        uses.forEach(u -> sj.add(u.toString()));
        return ty.toString()+"\t"+sj.toString();
    }
}
