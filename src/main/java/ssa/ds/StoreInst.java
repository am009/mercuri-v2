package ssa.ds;

import dst.ds.Type;

// store <ty> <value>, <ty>* <pointer>
public class StoreInst extends BaseInst {
    public Type ty;
    public Value val;
    public Value ptr;

    public static class Builder {
        private StoreInst inst;

        public Builder(BasicBlock parent) {
            inst = new StoreInst();
            inst.parent = parent;
        }

        public Builder addType(Type t) {
            inst.ty = t;
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
