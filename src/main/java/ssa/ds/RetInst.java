package ssa.ds;

import java.util.Collections;
import java.util.List;

public class RetInst extends TerminatorInst {

    // 保存返回值的类型
    public Type ty;

    public RetInst(BasicBlock parent) {
        this.parent = parent;
        // 防止本指令的返回值被使用。
        type = Type.Void;
    }

    public static class Builder {
        private RetInst inst;

        public Builder(BasicBlock parent) {
            inst = new RetInst(parent);
        }

        public Builder addType(Type t) {
            inst.ty = t;
            return this;
        }

        // 设置返回值
        public Builder addOperand(Value v) {
            inst.oprands.add(new Use(inst, v));
            return this;
        }

        public RetInst build() {
            return inst;
        }
    }

    @Override
    List<BasicBlock> getSuccessors() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        var b = new StringBuilder("ret");
        b.append(" ").append(ty.toString());
        oprands.forEach(use -> b.append(" ").append(use.value.toValueString()));
        return b.toString();
    }
}
