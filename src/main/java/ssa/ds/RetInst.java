package ssa.ds;

public class RetInst extends BaseInst {

    public static class Builder {
        private RetInst inst;

        public Builder(BasicBlock parent) {
            inst = new RetInst();
            inst.parent = parent;
            parent.insts.add(inst);
        }

        public RetInst build() {
            return inst;
        }
    }
}
