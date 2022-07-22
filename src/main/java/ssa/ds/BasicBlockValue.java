package ssa.ds;

public class BasicBlockValue extends Value {
    public BasicBlock b;

    public BasicBlockValue(BasicBlock b) {
        this.name = b.label;
        this.type = null;
        this.b = b;
    }

    @Override
    public String toValueString() {
        if (name != null) {
            return "label %" + name.toString();
        } else {
            return "label %?"; // 没有命名
        }
    }
}
