package ssa.ds;

public class BranchInst extends TerminatorInst {
    BranchCond cond;

    // 跳转到目标基本块的开头
    public BranchInst(BasicBlock parent, BranchCond cond, BasicBlockValue opr) {
        this.parent = parent;
        this.cond = cond;
        this.oprands.add(new Use(this, opr));
    }
}
