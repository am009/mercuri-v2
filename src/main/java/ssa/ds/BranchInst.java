package ssa.ds;

public class BranchInst extends TerminatorInst {

    // 跳转到目标基本块的开头
    public BranchInst(BasicBlock parent, Value cond, BasicBlockValue t, BasicBlockValue f) {
        this.parent = parent;
        assert cond.type.equals(Type.Boolean);
        this.oprands.add(new Use(this, cond));
        this.oprands.add(new Use(this, t));
        this.oprands.add(new Use(this, f));
        type = Type.Void;
    }

    @Override
    public String getOpString() {
        return "br i1";
    }
}
