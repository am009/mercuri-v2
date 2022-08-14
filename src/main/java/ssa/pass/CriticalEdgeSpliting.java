package ssa.pass;

import java.util.ArrayList;

import ds.Global;
import ssa.ds.BasicBlock;
import ssa.ds.BasicBlockValue;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.JumpInst;
import ssa.ds.Module;
import ssa.ds.PhiInst;
import ssa.ds.Use;

public class CriticalEdgeSpliting {
    public static Module process(Module m) {
        for (var func: m.funcs) {
            var self = new CriticalEdgeSpliting(func);
            self.doAnalysis();
        }
        return m;
    }

    Func func;
    // 即从terminator instruction指令到basic block value的那个对应关键边的关键的use。
    ArrayList<Use> criticalUses = new ArrayList<>();
    int nameIndex = 0;

    public CriticalEdgeSpliting(Func func) {
        this.func = func;
    }

    private void doAnalysis() {
        for (var bb: func.bbs) {
            var pred = bb.pred();
            if (pred.size() <= 1) {
                continue;
            }
            if (! bb.hasPhi()) {
                continue;
            }
            for (var p: pred) {
                var pSucc = p.succ();
                if (pSucc.size() > 1) { // 找到了critical edge
                    boolean found = false;
                    for (var u: bb.getValue().getUses()) {
                        if (u.user instanceof Instruction && ((Instruction)u.user).parent == p) {
                            assert found == false;
                            found = true;
                            Global.logger.trace("[CriticalEdgeSpliting] critial edge from %s to %s.", p.getValue().toValueString(), bb.getValue().toValueString());
                            criticalUses.add(u);
                        } 
                    }
                }
            }
        }
        for (var u: criticalUses) {
            splitCriticalEdge(u);
        }
    }

    /**
     * 有以下关键的指向关系
     * 1. terminator 指令的operands里和BasicBlockValue之间的use。
     * 2. phi指令对terminator指令的parent的use。
     * 要变成这样的指向关系：
     * 1. terminator 指令的operands里和新插入的BasicBlockValue之间的use。
     * 2. 新插入的基本块里的jump指令和原来BasicBlockValue之间的use
     * 3. phi指令改为引用新插入的基本块的BasicBlockValue
     * @param u
     */
    private void splitCriticalEdge(Use u) {
        var newbb = new BasicBlock("criticalEdge_"+nameIndex++);
        var targetbbv = (BasicBlockValue)u.value;
        var targetbb = targetbbv.b;
        var frombb = ((Instruction)u.user).parent;

        // step 1 设原来是 br -> targetbb
        // 要变成 br -> newbb, jmp -> targetbb
        targetbbv.removeUse(u); // remove old targetbbv -> br
        u.user.replaceUseWith(u, new Use(u.user, newbb.getValue())); // remove old br -> targetbbv & br -> newbb & newbb -> br
        var jmp = new JumpInst(newbb, targetbbv); // jmp -> targetbb & targetbbv -> jmp
        newbb.insts.add(jmp);
        
        // step 2 处理phi
        boolean isAllPhi = true;
        for (var inst: targetbb.insts) {
            if (inst instanceof PhiInst) {
                assert isAllPhi;
                var phi = (PhiInst) inst;
                int ps = phi.preds.size();
                for(int i=0;i<ps;i++) {
                    var phiu = phi.preds.get(i);
                    if (phiu.value == frombb.getValue()) {
                        // 找到了对应的use
                        phiu.value.removeUse(phiu);
                        phi.replacePredUseWith(phiu, new Use(phi, newbb.getValue()));
                    }
                }
            } else {
                isAllPhi = false;
            }
        }

        // 插入到合适的位置，critical edge的predecessor后面或者successor前面
        // 插入到successor前面似乎比较好
        int ind = func.bbs.indexOf(targetbb);
        func.bbs.add(ind, newbb);
    }

}
