package ssa.ds;

public class BasicBlockEntry extends Value {
    public BasicBlock dest;

    public BasicBlockEntry(BasicBlock dest) {
        this.dest = dest;
    }
}
