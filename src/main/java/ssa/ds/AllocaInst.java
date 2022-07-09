package ssa.ds;

import dst.ds.Type;

public class AllocaInst extends BaseInst {
    public Type ty;
    public long numElement = 1;
    
    public static class Builder {
        private AllocaInst inst;

        public Builder(BasicBlock parent) {
            inst = new AllocaInst();
            inst.parent = parent;
            parent.insts.add(inst);
        }

        public Builder addType(Type t) {
            inst.ty = t;
            return this;
        }

        public AllocaInst build() {
            return inst;
        }
    }
}
