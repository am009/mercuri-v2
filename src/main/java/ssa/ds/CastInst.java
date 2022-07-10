package ssa.ds;

// %X = sitofp i32 257 to float
public class CastInst extends Instruction {
    CastOp op;

    // 目前好像只需要用到int到float的转换，有其他的转换再回来改。
    public CastInst(BasicBlock b, Value from) {
        parent = b;
        op = CastOp.I2F;
        type = Type.Float;
        oprands.add(new Use(this, from));
    }
}
