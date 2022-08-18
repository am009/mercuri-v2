package ssa.pass.ds;

import java.util.List;
import java.util.Map;

import ssa.ds.BasicBlock;

public class LoopInfo {
    Map<BasicBlock, Loop> loopMap;
    List<Loop> topLevel;

    public int getDepth(BasicBlock bb) {
        if (loopMap.get(bb) == null) {
            return 0;
        } else {
            return loopMap.get(bb).getDepth();
        }
    }

    public List<Loop> getDeepest() {
        if (topLevel.size() == 0) {
            return topLevel;
        } else {
            var deepest = new java.util.ArrayList<Loop>();
            for (var loop : topLevel) {
                deepest.addAll(loop.getDeepest());
            }
            return deepest;
        }
    }
}
