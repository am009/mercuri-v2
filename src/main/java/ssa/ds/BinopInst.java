package ssa.ds;

import java.util.StringJoiner;

import dst.ds.BinaryOp;

// UnaryOp也直接转换为Binop。sub -> sub 0, xxx。not -> eq 0, xxx
public class BinopInst extends Instruction {
    public Type opType;
    public BinaryOp op;

    public BinopInst(BasicBlock parent, BinaryOp op, Value lhs, Value rhs) {
        this.parent = parent;
        assert lhs.type.equals(rhs.type); // 确保两边类型相同
        Type type = lhs.type;
        this.opType = type;
        this.type = type;
        if (op.isBoolean()) {
            this.type = Type.Boolean;
        }
        this.op = op;
        this.oprands.add(new Use(this, lhs));
        this.oprands.add(new Use(this, rhs));
    }

    @Override
    public String getOpString() {
        return op.toString(opType.baseType.isFloat()) + " " + opType.toString();
    }
}
