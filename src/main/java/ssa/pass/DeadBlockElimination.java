package ssa.pass;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import ssa.ds.BasicBlock;
import ssa.ds.Func;
import ssa.ds.Module;

/**
 * 刚生成出来的IR有一些dead basic block，会影响mem2reg对phi节点的插入。因此需要先移除一下
 */
public class DeadBlockElimination {
    public static Module process(Module m) {
        for (var func : m.funcs) {
            var self = new DeadBlockElimination(func);
            self.doAnalysis();
        }
        return m;
    }

    public Func func;
    Set<BasicBlock> reachable = new HashSet<>();

    public DeadBlockElimination(Func f) {
        this.func = f;
    }

    public void doAnalysis() {
        scanReachable();
        for (var it = func.bbs.iterator(); it.hasNext();) {
            var bb = it.next();
            if (!reachable.contains(bb)) {
                it.remove();
                // 移除跳转指令对其他基本块的Use。
                if (bb.hasTerminator()) {
                    var term = bb.getTerminator();
                    term.removeAllOpr();
                }
            }
        }
    }

    private void scanReachable() {
        Deque<BasicBlock> q = new LinkedList<>();
        q.add(func.entry());
        while (q.size() > 0) {
            var front = q.pollFirst();
            if (!reachable.contains(front)) {
                reachable.add(front);
                for (var succ : front.succ()) {
                    if (!reachable.contains(succ)) {
                        q.add(succ);
                    }
                }
            }
        }
    }
}
