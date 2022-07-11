package ssa.ds;

public class StoreInst extends Instruction {

    public StoreInst() {
        type = Type.Void;
    }

    public static class Builder {
        private StoreInst inst;

        public Builder(BasicBlock parent) {
            inst = new StoreInst();
            inst.parent = parent;
        }

        public Builder addOperand(Value val, Value ptr) {
            inst.oprands.add(new Use(inst, val));
            inst.oprands.add(new Use(inst, ptr));
            return this;
        }

        public StoreInst build() {
            return inst;
        }
    }

    // store <ty> <value>, <ty>* <pointer>
    @Override
    public String toString() {
        var b = new StringBuilder("store ");
        var v1 = oprands.get(0).value;
        var v2 = oprands.get(1).value;
        b.append(v1.type.toString());
        b.append(" ").append(v1.toValueString());
        b.append(", ").append(v2.type.toString());
        b.append(" ").append(v2.toValueString());
        return b.toString();
    }
}
