package ssa.ds;

import java.util.ArrayList;
import java.util.StringJoiner;


public abstract class Instruction extends User {
    public String comments; // 生成LLVM IR时在指令后面添加的额外注释，方便debug。
    public BasicBlock parent;

    // 使用默认的toString方法时只需要重载该方法
    public String getOpString() {
        return this.getClass().getName();
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        var valStr = toValueString();
        if (valStr != null && valStr.length() > 0) {
            b.append(valStr).append(" = ");
        }
        b.append(getOpString());
        var sj = new StringJoiner(", ", " ", "");
        oprands.forEach(use -> sj.add(use.value.toValueString()));
        b.append(sj.toString());
        if (comments != null) {
            b.append("     ; ").append(comments);
        }
        return b.toString();
    }

    public Value getOperand0() {
        return oprands.get(0).value;
    }

    public Value getOperand1() {
        return oprands.get(1).value;
    }

    public Value getOperand2() {
        return oprands.get(2).value;
    }

    public void overrideOperand(int index, Value newValue) {
        assert index >= 0 && index < oprands.size();
        assert newValue != null;
        var oldUse = oprands.get(index);
        if (oldUse != null) {
            oldUse.value.removeUse(oldUse);
        }
        oprands.set(index, new Use(this, newValue));
    }

    // 会改变 oprands 里内容的相对位置，一般只能由 PhiInst 相关操作使用
    public void RemoveOperands(int[] indices) {
        this.removeAllOperandUseFromValue();
        var todoList = new ArrayList<Use>();
        for (var i : indices) {
            todoList.add(oprands.get(i));
        }
        for (var u : todoList) {
            this.removeUse(u);
            oprands.remove(u);
        }
    }
}
