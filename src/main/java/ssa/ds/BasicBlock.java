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
        val = new BasicBlockValue(this);
    }

    public BasicBlockValue getValue() {
        return val;
    }

    public boolean hasTerminator() {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            return false;
        }
        return true;
    }

    public Instruction addBeforeTerminator(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            insts.add(i);
        } else {
            insts.add(insts.size()-1, i);
        }
        return i;
    }

    public Instruction addBeforeJump(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            insts.add(i);
        } else if (insts.get(insts.size()-1) instanceof RetInst) {
            insts.add(i);
        } else {
            insts.add(insts.size()-1, i);
        }
        return i;
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(label).append(":\n");
        insts.forEach(i -> b.append("  ").append(i.toString()).append("\n"));
        return b.toString();
    }
}
