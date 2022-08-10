package ssa.pass;

import ssa.ds.BasicBlock;
import ssa.ds.CallInst;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.StoreInst;

public class GVN {
    Module ssaModule;

    public GVN(Module ssaModule) {
        this.ssaModule = ssaModule;
    }

    public static void process(Module ssaModule) {
        var gvn = new GVN(ssaModule);
        ssaModule.funcs.forEach(func -> gvn.execute(func));
    }

    private void execute(Func func) {
        var rpo = Util.computeReversePostOrderBlockList(func);
        for (var bb : rpo) {
            this.excuteOnBB(bb);
        }
    }

    private void excuteOnBB(BasicBlock bb) {
        var npred = bb.pred().size();
        for (var i = 0; i < bb.insts.size(); i++) {
            var inst = bb.insts.get(i);
            this.executeOnInst(bb, inst);
        }
    }

    private void executeOnInst(BasicBlock parent, Instruction inst) {
        if (inst.getUses().size() == 0 && !(inst instanceof StoreInst) && !(inst instanceof CallInst)) {
            return;
        }
        var v = Util.simplify(inst, true);
        // 如果已经化简为非指令，则原来的指令可以直接删掉
        if (v != inst && !(v instanceof Instruction)) {
            inst.replaceAllUseWith(v);
            parent.destroyInst(inst);
        }
    }
}
