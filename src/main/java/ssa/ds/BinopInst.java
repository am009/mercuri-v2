package ssa.ds;

import java.util.StringJoiner;

import dst.ds.BinaryOp;

// UnaryOp也直接转换为Binop。sub -> sub 0, xxx。not -> eq 0, xxx
public class BinopInst extends Instruction {
    public BinaryOp op;

    public BinopInst(BasicBlock parent, Type type, BinaryOp op, Value lhs, Value rhs) {
        this.parent = parent;
        this.type = type;
        this.op = op;
        this.oprands.add(new Use(this, lhs));
        this.oprands.add(new Use(this, rhs));
    }

    @Override
    public String getOpString() {
        return op.toString(type.baseType.isFloat()) + " " + type.toString();
    }
}
