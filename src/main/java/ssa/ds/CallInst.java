package ssa.ds;

public class CallInst extends BaseInst {
    public FuncValue func;

    public static class Builder {
        private CallInst inst;

        public Builder(BasicBlock parent, FuncValue func) {
            inst = new CallInst();
            inst.parent = parent;
            parent.insts.add(inst);
            inst.func = func;
        }

        public Builder addArg(Value arg) {
            inst.oprands.add(new Use(inst, arg));
            return this;
        }

        public CallInst build() {
            return inst;
        }
    }
}
