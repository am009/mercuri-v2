package ssa.ds;

// %X = sitofp i32 257 to float
public class CastInst extends Instruction {
    public CastOp op;
    public Type from;

    // 目前好像只需要用到int和float之间的转换，有其他的转换再回来改。
    public CastInst(BasicBlock b, Value v) {
        parent = b;
        oprands.add(new Use(this, v));
    }

    public static class Builder {
        private CastInst inst;

        public Builder(BasicBlock parent, Value v) {
            inst = new CastInst(parent, v);
            inst.parent = parent;
        }

        public Builder addOp(CastOp op) {
            inst.op = op;
            if (op == CastOp.I2F) {
                inst.type = Type.Float;
                inst.from = Type.Int;
            } else if (op == CastOp.F2I) {
                inst.type = Type.Int;
                inst.from = Type.Float;
            } else {
                throw new UnsupportedOperationException();
            }
            return this;
        }

        // from [n x i8]* to i8*
        public Builder strBitCast(Type from) {
            inst.op = CastOp.TYPE;
            inst.type = Type.String;
            inst.from = from;
            return this;
        }

        public CastInst build() {
            return inst;
        }
    }

    // 目前只考虑了fptosi和sitofp的格式
    // <result> = fptosi <ty> <value> to <ty2>
    // <result> = sitofp <ty> <value> to <ty2>
    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(toValueString()).append(" = ");
        b.append(op.toString());
        b.append(" ").append(from.toString());
        b.append(" ").append(oprands.get(0).value.toValueString());
        b.append(" to ").append(type.toString());
        if (comments != null) {
            b.append("     ; ").append(comments);
        }
        return b.toString();
    }
}
