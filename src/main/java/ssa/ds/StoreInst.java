package ssa.ds;

// store <ty> <value>, <ty>* <pointer>
public class StoreInst extends Instruction {
    public Value val;
    public Value ptr;

    public static class Builder {
        private StoreInst inst;

        public Builder(BasicBlock parent) {
            inst = new StoreInst();
            inst.parent = parent;
            parent.insts.add(inst);
        }

        public Builder addType(Type t) {
            inst.type = t;
            return this;
        }

        public Builder addOperand(Value val, Value ptr) {
            inst.val = val;
            inst.ptr = ptr;
            return this;
        }

        public StoreInst build() {
            return inst;
        }
    }
}
