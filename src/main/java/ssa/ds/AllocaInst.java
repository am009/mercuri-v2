package ssa.ds;

public class AllocaInst extends Instruction {
    public Type ty;
    public long numElement = 1;
    
    public static class Builder {
        private AllocaInst inst;

        public Builder(BasicBlock parent) {
            inst = new AllocaInst();
            inst.parent = parent;
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
