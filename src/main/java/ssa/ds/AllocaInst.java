package ssa.ds;

public class AllocaInst extends BaseInst {

    public static class Builder {
        private CallInst inst;

        public Builder(BasicBlock parent, ) {
            var inst = new CallInst();
            inst.parent = parent;
        }

        public Builder setRetval(Value arg) {
            if (inst.oprands.size() >= 1) {
                throw new IllegalArgumentException("RetInst already has a retval");
            }
            inst.oprands.add(new Use(inst, arg));
            
            return this;
        }

        public CallInst build() {
            return inst;
        }
    }
}
