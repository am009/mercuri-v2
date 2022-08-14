package ssa.pass;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ssa.ds.BasicBlock;
import ssa.ds.Func;
import ssa.ds.JumpInst;
import ssa.ds.Module;
import ssa.ds.PhiInst;

/**
 * TODO handle phi指令。
 */
public class BasicBlockMerging {
    public static Module process(Module m) {
        for (var func: m.funcs) {
            var self = new BasicBlockMerging(func);
            self.doAnalysis();
        }
        return m;
    }

    Func func;
    Set<BasicBlock> visited = new HashSet<>();
    public BasicBlockMerging(Func func) {
        this.func = func;
    }

    public void doAnalysis() {
        Deque<BasicBlock> q = new LinkedList<>();
        q.add(func.entry());
        while(q.size() > 0) {
            var front = q.pollFirst();
            var succs = front.succ();
            boolean single_succ = succs.size() == 1;
            
            // handle edge from front -> succ
            if (single_succ) {
                var succ = succs.get(0);
                if (succ.pred().size() == 1) {
                    merge(front, succ);
                    // 可能创造新的优化机会，之后重新回来遍历
                    // visited.remove(front);
                    q.add(front);
                    continue;
                }
            }

            // 图遍历
            if (! visited.contains(front)) {
                visited.add(front);
                for(var succ: succs) {
                    if (!visited.contains(succ)) {
                        q.add(succ);
                    }
                }
            }
        }
    }

    private void merge(BasicBlock front, BasicBlock succ) {
        assert front.getTerminator() instanceof JumpInst; // 无条件跳转
        // 1. 删除这个无条件跳转。
        front.getTerminator().removeAllOpr();
        front.insts.remove(front.insts.size()-1);
        // succ全部合并到front
        // 2. 修改指令的parent，并把指令加入过去。
        for(var inst: succ.insts) {
            if (inst instanceof PhiInst) {
                // TODO handle phi.
                throw new UnsupportedOperationException();
                // inst.replaceAllUseWith(inst.oprands.get(0).value)
            }
            inst.parent = front;
            front.insts.add(inst);
        }
        // 删除succ这个基本块。
        assert succ.getValue().getUses().size() == 0;
        func.bbs.remove(succ);
    }

}
