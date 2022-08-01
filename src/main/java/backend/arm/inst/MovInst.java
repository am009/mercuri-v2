package backend.arm.inst;

import java.util.ArrayList;
import java.util.List;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.arm.Cond;
import backend.arm.FloatImm;
import backend.arm.Imm;
import backend.arm.IntImm;
import backend.arm.LabelImm;
import backend.arm.Reg;

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
    public Cond cond;

    public MovInst(AsmBlock p, Ty ty, AsmOperand to, AsmOperand from) {
        parent = p;
        this.ty = ty;
        defs.add(to);
        uses.add(from);
        cond = Cond.AL;
    }

    public static List<AsmInst> loadImm(AsmBlock p, AsmOperand reg, Imm imm) {
        var ret = new ArrayList<AsmInst>();
        if (imm instanceof FloatImm) {
            var fimm = (FloatImm) imm;
            assert reg.isFloat == true;
            // 先借助保留的IP加载进来
            var tmp = new Reg(Reg.Type.ip);
            ret.addAll(loadImm(p, tmp, new IntImm(fimm.bitcastInt())));
            // 从IP移到目标寄存器里。
            ret.add(new VMovInst(p, VMovInst.Ty.A2S, reg, tmp));
        } else if (imm instanceof LabelImm || imm instanceof IntImm) { // 简单情况
            // MOVW会自动清空高16bit，所以立即数小于16bit范围的可以直接MOVW
            if (imm.highestOneBit() < 65535) {
                ret.add(new MovInst(p, Ty.MOVW, reg, imm));
            } else {
                ret.add(new MovInst(p, Ty.MOVW, reg, imm.getLow16()));
                ret.add(new MovInst(p, Ty.MOVT, reg, imm.getHigh16()));
            }
        } else {throw new UnsupportedOperationException();}
        return ret;
    }

    @Override
    public String toString() {
        return ty.toString()+cond.toString()+"\t"+defs.get(0).toString()+", "+uses.get(0).toString();
    }
}
