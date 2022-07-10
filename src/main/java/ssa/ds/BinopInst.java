package ssa.ds;

import dst.ds.BinaryOp;

public class BinopInst extends Instruction {
    public BinaryOp op;

    public BinopInst(BasicBlock parent, Type type, BinaryOp op, Value lhs, Value rhs) {
        this.parent = parent;
        parent.insts.add(this);
        this.type = type;
        this.op = op;
        this.oprands.add(new Use(this, lhs));
        this.oprands.add(new Use(this, rhs));
    }
}
