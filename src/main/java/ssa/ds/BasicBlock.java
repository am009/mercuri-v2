package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock {
    // TODO landing pads for Phi insts? or basic block argument?
    public String label;
    public List<Instruction> insts;
    public BasicBlockValue val;

    public BasicBlock(String name) {
        label = name;
        insts = new ArrayList<>();
    }

    public void addBeforeTerminator(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            insts.add(i);
        } else {
            insts.add(insts.size()-1, i);
        }
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(label).append(":\n");
        insts.forEach(i -> b.append("  ").append(i.toString()).append("\n"));
        return b.toString();
    }
}
