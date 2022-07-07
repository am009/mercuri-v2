package ssa.ds;

import dst.ds.BinaryOp;

public class UnaopInst extends BaseInst {
    public BinaryOp op;

    public UnaopInst(BasicBlock parent, BinaryOp op, Value opr) {
        this.parent = parent;
        this.op = op;
        this.oprands.add(new Use(this, opr));
    }
}
