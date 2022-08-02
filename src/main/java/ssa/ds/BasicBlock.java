package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock {
    // TODO landing pads for Phi insts? or basic block argument?
    public String label;
    public List<Instruction> insts;
    public BasicBlockValue val;

    public BasicBlock(String name) {
        label = name;
        insts = new ArrayList<>();
        val = new BasicBlockValue(this);
    }

    public BasicBlockValue getValue() {
        return val;
    }

    /**
     * 判断基本块是否应该终止。例如最后一个指令是 TerminatorInst
     * @return 如果应该终止，返回 true，否则返回 false
     */
    public boolean hasTerminator() {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            return false;
        }
        return true;
    }

    /**
     * 将一个指令插入到基本块末尾的终结指令**前**。
     * @param i
     * @return
     */
    public Instruction addBeforeTerminator(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            insts.add(i);
        } else {
            insts.add(insts.size()-1, i);
        }
        return i;
    }

    /**
     * 将一个指令插入到基本块末尾的跳转指令前（即不包括 ret）
     * @param i
     * @return
     */
    public Instruction addBeforeJump(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size()-1) instanceof TerminatorInst)) {
            insts.add(i);
        } else if (insts.get(insts.size()-1) instanceof RetInst) {
            insts.add(i);
        } else {
            insts.add(insts.size()-1, i);
        }
        return i;
    }

    /**
     * 根据 BasicBlockValue 被使用的情况，获取所有前驱基本块，以列表形式返回。
     */
    public List<BasicBlock> pred() {
        var val = getValue();
        ArrayList<BasicBlock> ret = new ArrayList<>();
        for(Use u: val.getUses()) {
            // 目前User仅有Instruction，以后多了新的User需要再次考虑此处
            // 目前使用BasicBlockValue的也仅有TerminatorInst。
            assert u.user instanceof TerminatorInst;
            TerminatorInst inst = (TerminatorInst) u.user;
            ret.add(inst.parent);
        }
        return ret;
    }

    /**
     * 根据本块末尾的跳转指令，查询能够跳转到的后继节点
     * @return
     */
    public List<BasicBlock> succ() {
        assert insts.size() != 0 && insts.get(insts.size()-1) instanceof TerminatorInst;
        TerminatorInst t = (TerminatorInst)insts.get(insts.size()-1);
        return t.getSuccessors();
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(label).append(":\n");
        insts.forEach(i -> b.append("  ").append(i.toString()).append("\n"));
        return b.toString();
    }
}
