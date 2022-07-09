package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock {
    // TODO landing pads for Phi insts? or basic block argument?
    public String label;
    public List<BaseInst> insts;
    public BasicBlockValue val;

    public BasicBlock(String name) {
        label = name;
        insts = new ArrayList<>();
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(label).append(":\n");
        insts.forEach(i -> b.append("  ").append(i.toString()).append("\n"));
        return b.toString();
    }
}
