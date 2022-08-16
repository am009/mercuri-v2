package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock {
    // TODO landing pads for Phi insts? or basic block argument?
    public String label;
    public List<Instruction> insts;
    public BasicBlockValue val;
    public List<BasicBlock> domers; // 谁支配我
    public List<BasicBlock> idoms; // 我直接支配谁
    public List<BasicBlock> domiFrontier; // 支配边界
    public BasicBlock idomer; // 谁直接支配我
    public Integer domLevel;

    public BasicBlock(String name) {
        label = name;
        insts = new ArrayList<>();
        val = new BasicBlockValue(this);
        domers = new ArrayList<>();
        idoms = new ArrayList<>();
    }

    public BasicBlockValue getValue() {
        return val;
    }

    public void replaceInst(Instruction oldInst, Instruction newInst) {
        int ind = insts.indexOf(oldInst);
        assert ind != -1;
        insts.set(ind, newInst);
    }

    public void removeOprsAndReplaceInst(Instruction inst, Instruction newInst) {
        inst.removeAllOpr();
        replaceInst(inst, newInst);
    }

    /**
     * 判断基本块是否应该终止。例如最后一个指令是 TerminatorInst
     * @return 如果应该终止，返回 true，否则返回 false
     */
    public boolean hasTerminator() {
        if (insts.size() == 0 || !(insts.get(insts.size() - 1) instanceof TerminatorInst)) {
            return false;
        }
        return true;
    }

    public Instruction getTerminator() {
        assert hasTerminator();
        return insts.get(insts.size()-1);
    }

    public boolean hasPhi() {
        if (insts.size() == 0) return false;
        for (var inst: insts) {
            if (inst instanceof PhiInst) {
                return true;
            }
            break;
        }
        return false;
    }

    public List<PhiInst> getPhis() {
        var ret = new ArrayList<PhiInst>();
        for (var inst: insts) {
            if (inst instanceof PhiInst) {
                ret.add((PhiInst)inst);
            } else { // 因为phi必然在基本块开头
                break;
            }
        }
        return ret;
    }

    /**
     * 将一个指令插入到基本块末尾的终结指令**前**。
     * @param i
     * @return
     */
    public Instruction addBeforeTerminator(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size() - 1) instanceof TerminatorInst)) {
            insts.add(i);
        } else {
            insts.add(insts.size() - 1, i);
        }
        return i;
    }

    /**
     * 将一个指令插入到基本块末尾的跳转指令前（即不包括 ret）
     * @param i
     * @return
     */
    public Instruction addBeforeJump(Instruction i) {
        if (insts.size() == 0 || !(insts.get(insts.size() - 1) instanceof TerminatorInst)) {
            insts.add(i);
        } else if (insts.get(insts.size() - 1) instanceof RetInst) {
            insts.add(i);
        } else {
            insts.add(insts.size() - 1, i);
        }
        return i;
    }

    /**
     * 根据 BasicBlockValue 被使用的情况，获取所有前驱基本块，以列表形式返回。
     */
    public List<BasicBlock> pred() {
        var val = getValue();
        ArrayList<BasicBlock> ret = new ArrayList<>();
        for (Use u : val.getUses()) {
            // 目前使用BasicBlockValue有TerminatorInst和PhiInstruction的preds。
            if (u.user instanceof TerminatorInst) {
                TerminatorInst inst = (TerminatorInst) u.user;
                ret.add(inst.parent);
            }
        }
        return ret;
    }

    /**
     * 根据本块末尾的跳转指令，查询能够跳转到的后继节点
     * @return
     */
    public List<BasicBlock> succ() {
        assert insts.size() != 0 && insts.get(insts.size() - 1) instanceof TerminatorInst;
        TerminatorInst t = (TerminatorInst) insts.get(insts.size() - 1);
        return t.getSuccessors();
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(label).append(":\n");
        insts.forEach(i -> {
            b.append("  ").append(i.toString());
            if (i.comments != null) {
                b.append("    ; ").append(i.comments);
            }
            b.append("\n");
        });
        return b.toString();
    }

    public boolean hasValidTerm() {
        assert insts.size() > 0;
        return insts.get(insts.size() - 1) instanceof TerminatorInst;
    }

    // 删除指令，但是不一定将其 User 删除，因为此指令可能被替换为**值**
    public void removeInst(Instruction inst) {
        inst.removeAllOpr();
        insts.remove(inst);
    }
}
