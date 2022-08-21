package ssa.ds;

public class BranchInst extends TerminatorInst {

    // 跳转到目标基本块的开头
    public BranchInst(BasicBlock parent, Value cond, BasicBlockValue t, BasicBlockValue f) {
        this.parent = parent;
        assert cond.type.equals(Type.Boolean);
        this.oprands.add(new Use(this, cond)); // 0
        this.oprands.add(new Use(this, t)); // 1
        this.oprands.add(new Use(this, f)); // 2
        type = Type.Void;
    }

    @Override
    public String getOpString() {
        return "br i1";
    }

    public Value getCond() {
        return getOperand0();
    }

    public BasicBlock getTrueBlock() {
        var v = this.getOperand1();
        var bb = (BasicBlockValue) v;
        return bb.b;
    }

    public BasicBlock getFalseBlock() {
        var v = this.getOperand2();
        var bb = (BasicBlockValue) v;
        return bb.b;
    }
}
