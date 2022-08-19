package ssa.pass;

import ds.Global;
import ssa.ds.BasicBlock;
import ssa.ds.CallInst;
import ssa.ds.Func;
import ssa.ds.FuncValue;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.RetInst;

/**
 * process函数给可以尾递归优化的函数标记mustCall。
 */
public class MarkTailCall {
    public static Module process(Module m) {
        for (var func: m.funcs) {
            var self = new MarkTailCall(func);
            self.doAnalysis();
        }
        return m;
    }

    Func func;

    public MarkTailCall(Func func) {
        this.func = func;
    }
    
    protected void doAnalysis() {
        for (var blk: func.bbs) {
            doAnalysis(blk);
        }
    }

    private void doAnalysis(BasicBlock blk) {
        int size = blk.insts.size();
        for(int i=0;i<size;i++) {
            var current = blk.insts.get(i);
            if (!(current instanceof RetInst)) continue;
            Instruction prev = null;
            if (i-1>=0) {
                prev = blk.insts.get(i-1);
            }
            if (!(prev instanceof CallInst)) continue;
            var call = (CallInst)prev;
            if (call.target() != func) continue;
            if (call.isVariadic()) continue;
            // 判断每个参数是否使用了Local的值
            boolean canTail = true;
            for (var use: call.oprands) {
                var op = use.value;
                if (op instanceof FuncValue) continue;
                if (op.type.isPointer) {
                    var arrVal = PAA.getArrayValue(op);
                    if (PAA.isLocal(arrVal)) {
                        canTail = false;
                    }
                }
            }
            if (canTail) {
                Global.logger.trace("MarkTailCall: mark "+call.toString());
                call.mustTail = true;
            }
        }
    }
}
