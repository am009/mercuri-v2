package ssa.ds;

// %X = sitofp i32 257 to float
public class CastInst extends Instruction {
    public CastOp op;

    // 目前好像只需要用到int和float之间的转换，有其他的转换再回来改。
    public CastInst(BasicBlock b, Value from, CastOp op) {
        parent = b;
        this.op = op;
        if (op == CastOp.I2F) {
            type = Type.Float;
        } else if (op == CastOp.F2I) {
            type = Type.Int;
        } else {
            throw new UnsupportedOperationException();
        }
        oprands.add(new Use(this, from));
    }
}
