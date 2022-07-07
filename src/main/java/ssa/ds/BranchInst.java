package ssa.ds;


public class BranchInst extends BaseInst {
    BranchCond cond;

    // 跳转到目标基本块的开头
    public BranchInst(BasicBlock parent, BranchCond cond, BasicBlockEntry opr) {
        this.parent = parent;
        this.cond = cond;
        this.oprands.add(new Use(this, opr));
    }
}
