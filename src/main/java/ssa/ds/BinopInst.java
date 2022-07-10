package ssa.ds;

import dst.ds.BinaryOp;

public class BinopInst extends Instruction {
    public BinaryOp op;

    public BinopInst(BasicBlock parent, BinaryOp op, Value lhs, Value rhs) {
        this.parent = parent;
        this.op = op;
        this.oprands.add(new Use(this, lhs));
        this.oprands.add(new Use(this, rhs));
    }
}
