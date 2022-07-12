package ssa.ds;

public class JumpInst extends TerminatorInst {
    public JumpInst(BasicBlock p, BasicBlockValue bbv) {
        parent = p;
        this.oprands.add(new Use(this, bbv));
        type = Type.Void;
    }

    @Override
    public String getOpString() {
        return "br";
    }
}
