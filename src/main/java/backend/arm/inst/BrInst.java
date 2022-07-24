package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.arm.Cond;

public class BrInst extends AsmInst {
    public Cond cond;
    public AsmBlock target;

    public static class Builder {
        private BrInst inst;

        public Builder(AsmBlock parent, AsmBlock target) {
            inst = new BrInst();
            inst.parent = parent;
            inst.target = target;
            inst.cond = Cond.AL;
        }

        public Builder addCond(Cond c) {
            inst.cond = c;
            return this;
        }

        public Builder addComment(String c) {
            inst.comment = c;
            return this;
        }

        public BrInst build() {
            return inst;
        }
    }

    @Override
    public String toString() {
        return String.format("B%s\t%s", cond.toString(), target.label);
    }
}
