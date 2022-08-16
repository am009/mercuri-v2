package backend.arm.pass;

import java.util.List;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.arm.Cond;
import backend.arm.IntImm;
import backend.arm.inst.BrInst;
import backend.arm.inst.CMPInst;
import backend.arm.inst.MovInst;

public class Peephole {
    public static AsmModule process(AsmModule m) {
        for (var f: m.funcs) {
            var g = new Peephole(f);
            g.doAnalysis();
        }
        return m;
    }

    AsmFunc func;

    public Peephole(AsmFunc f) {
        func = f;
    }

    protected void doAnalysis() {
        if (func.bbs.size() == 0) {
            return;
        }
        var it = func.iterator();
        AsmBlock current = null;
        AsmBlock next = it.next();
        for (;it.hasNext();) {
            current = next;
            next = it.next();
            doAnalysis(current, next);
        }
        current = next;
        next = null;
        doAnalysis(current, next);
    }

    protected void doAnalysis(AsmBlock current, AsmBlock next) {
        int isize = current.insts.size();
        for (int i=0;i<isize;i++) {
            var inst_ = current.insts.get(i);
            // 优化操作数相同的mov指令
            if (inst_ instanceof MovInst) {
                var inst = (MovInst) inst_;
                if (inst.uses.get(0).equals(inst.defs.get(0))) {
                    current.insts.remove(inst);
                    i -= 1;
                    isize -= 1;
                }
            }
            // 优化条件跳转，详见backend文档的窥孔优化部分
            Cond cond = optimizableBr(inst_, current.insts, i);
            if (cond != null) {
                // 修改末尾跳转
                var br = (BrInst) inst_;
                br.cond = cond;
                // 删去前三个指令
                for (int j=0;j<3;j++) {
                    current.insts.remove(i-1);
                    i -= 1;
                    isize -= 1;
                }
            }
        }
        // 跨AsmBlock的优化，优化不必要的无条件跳转
        int size = current.insts.size();
        if (size > 0) {
            var last = current.insts.get(size-1);
            if (last instanceof BrInst) {
                var br = (BrInst) last;
                if (br.cond == Cond.AL) { // 无条件跳转
                    if (br.target == next) { // 跳转到下一个基本块
                        current.insts.remove(br);
                    }
                }
            }
        }
    }

    Cond optimizableBr(AsmInst inst_, List<AsmInst> insts, int i) {
        if (insts.size() < 4) return null;
        // BEQ/BNE label
        if (!(inst_ instanceof BrInst)) return null;
        var br = (BrInst) inst_;
        if (br.cond != Cond.EQ && br.cond != Cond.NE) return null;
        // CMP r, #0x0
        var inst_1 = insts.get(i-1);
        if (!(inst_1 instanceof CMPInst)) return null;
        var cmp = (CMPInst)inst_1;
        if (!isUse0IntImm(cmp.uses.get(1), 0)) return null;
        var operand = cmp.uses.get(0);
        // MOVWcond r, #0x1
        var inst_2 = insts.get(i-2);
        if (!(inst_2 instanceof MovInst)) return null;
        var movCond = (MovInst)inst_2;
        if (!movCond.ty.equals(MovInst.Ty.MOVW)) return null;
        if (movCond.cond == Cond.AL) return null;
        if (!isUse0IntImm(movCond.uses.get(0), 1)) return null;
        if (!movCond.defs.get(0).equals(operand)) return null;
        // MOVW	r, #0x0
        var inst_3 = insts.get(i-3);
        if (!(inst_3 instanceof MovInst)) return null;
        var mov = (MovInst)inst_3;
        if (!mov.ty.equals(MovInst.Ty.MOVW)) return null;
        if (mov.cond != Cond.AL) return null;
        if (!isUse0IntImm(mov.uses.get(0), 0)) return null;
        if (!mov.defs.get(0).equals(operand)) return null;
        // 计算目标Cond
        Cond target = movCond.cond;
        if (br.cond == Cond.NE) {
            return target;
        } else if (br.cond == Cond.EQ) {
            return target.negate();
        }
        throw new RuntimeException("unreachable");
    }

    static boolean isUse0IntImm(AsmOperand op, int i) {
        if (!(op instanceof IntImm)) return false;
        var movCondImm = (IntImm) op;
        if (movCondImm.value != i) return false;
        return true;
    }
}
