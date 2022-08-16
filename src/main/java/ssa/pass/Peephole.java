package ssa.pass;

import java.util.ArrayList;
import java.util.List;

import dst.ds.BinaryOp;
import ssa.ds.BasicBlock;
import ssa.ds.BinopInst;
import ssa.ds.CastInst;
import ssa.ds.CastOp;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.Type;
import ssa.ds.Value;

/**
 * 1. 优化生成的条件判断中的冗余
 *  %48 = icmp eq i32 1, %47
    %49 = zext i1 %48 to i32
    %50 = icmp ne i32 0, %49
    br i1 %50, label %if_true_12, label %if_end_12
      对于 `%result = icmp ne i32 0, %x` 这个语句，如果x是从一个boolean类型转换过来的（zext i1 to i32）
    那这个不等于0的判断没有任何意义，result应该被直接替换为前面的i1，然后删除中间的zext和icmp。
 */
public class Peephole {
    public static Module process(Module m) {
        for (var func: m.funcs) {
            var self = new Peephole(func);
            self.doAnalysis();
        }
        return m;
    }

    Func func;

    public Peephole(Func func) {
        this.func = func;
    }
    
    protected void doAnalysis() {
        for (var blk: func.bbs) {
            doAnalysis(blk);
        }
    }

    private void doAnalysis(BasicBlock blk) {
        List<Instruction> toRemove = new ArrayList<>();
        for (var inst: blk.insts) {
            if (isIcmpNe0(inst)) {
                Value castedBool = getCastFromBoolean(inst);
                if (castedBool != null) {
                    inst.replaceAllUseWith(castedBool);
                    // 移除zext和icmp
                    var zext = (CastInst) ((BinopInst)inst).oprands.get(1).value;
                    // zext移除对castedBool的使用
                    zext.removeAllOperandUseFromValue();
                    // 顺序很重要，先移除icmp可以让zext的use归零
                    toRemove.add(inst);
                    toRemove.add(zext);
                }
            }
        }
        // 如果指令不被需要，则可以移除
        for (var inst: toRemove) {
            if (inst.getUses().size() == 0) {
                inst.removeAllOperandUseFromValue();
                inst.parent.insts.remove(inst);
            }
        }
    }

    // 配合isIcmpNe0使用，即isIcmpNe0需要为true
    private Value getCastFromBoolean(Instruction inst) {
        var val = ((BinopInst)inst).oprands.get(1).value;
        if (!(val instanceof CastInst)) return null;
        var zext = (CastInst) val;
        if (zext.op != CastOp.ZEXT) return null;
        var bool = zext.oprands.get(0).value;
        if (!bool.type.equals(Type.Boolean)) return null;
        return bool;
    }

    private boolean isIcmpNe0(Instruction inst) {
        if (!(inst instanceof BinopInst)) {
            return false;
        }
        var bin = (BinopInst) inst;
        if (bin.op != BinaryOp.LOG_NEQ) {
            return false;
        }
        // 和FakeSSAGenerator的NonShortLogicExpr的处理对应
        if (bin.oprands.get(0).value instanceof ConstantValue) {
            var cv = (ConstantValue)bin.oprands.get(0).value;
            if (cv.isDefault()) {
                return true;
            }
        }
        return false;
    }

}
