package ssa.pass.ds;

import java.util.List;

import ssa.ds.BasicBlock;

public class Loop {
    Loop parent;
    List<Loop> subLoops;
    List<BasicBlock> blocks;

    public Loop(BasicBlock header) {
        this.blocks = new java.util.ArrayList<>();
        this.blocks.add(header);
    }

    public int getDepth() {
        if (parent == null) {
            return 0;
        } else {
            return parent.getDepth() + 1;
        }
    }

    public List<Loop> getDeepest() {
        if (subLoops.size() == 0) {
            return subLoops;
        } else {
            var deepest = new java.util.ArrayList<Loop>();
            for (var loop : subLoops) {
                deepest.addAll(loop.getDeepest());
            }
            return deepest;
        }
    }

}