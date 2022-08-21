package ssa.ds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class BasicBlock {
    public String label;
    public List<Instruction> insts = new LinkedList<>();
    public BasicBlockValue val;
    public List<BasicBlock> domers = new ArrayList<>(); // 谁支配我
    public List<BasicBlock> idoms = new ArrayList<>(); // 我直接支配谁
    public List<BasicBlock> domiFrontier = new ArrayList<>(); // 支配边界
    public BasicBlock idomer; // 谁直接支配我
    public Integer domLevel;
    public Func owner;

    public BasicBlock(String name, Func func) {
        label = name;
        owner = func; // 此属性可以延迟填写 
        val = new BasicBlockValue(this);
    }

    public BasicBlockValue getValue() {
        return val;
    }

    public Instruction entry() {
        if (insts.size() == 0)
            return null;
        return insts.get(0);
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
        return insts.get(insts.size() - 1);
    }

    public boolean hasPhi() {
        if (insts.size() == 0)
            return false;
        for (var inst : insts) {
            if (inst instanceof PhiInst) {
                return true;
            }
            break;
        }
        return false;
    }

    public List<PhiInst> getPhis() {
        var ret = new ArrayList<PhiInst>();
        for (var inst : insts) {
            if (inst instanceof PhiInst) {
                ret.add((PhiInst) inst);
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

    public boolean hasBranchTerm() {
        if (!(insts.size() > 0)) {
            return false;
        }

        if (!(insts.get(insts.size() - 1) instanceof TerminatorInst)) {
            return false;
        }

        if (!(insts.get(insts.size() - 1) instanceof BranchInst)) {
            return false;
        }

        return true;

    }

    // 删除指令，但是不一定将其 User 删除，因为此指令可能被替换为**值**
    public void removeInst(Instruction inst) {
        inst.removeAllOpr();
        insts.remove(inst);
    }

    public void removeInstWithIterator(Instruction inst, Iterator<Instruction> it) {
        inst.removeAllOpr();
        it.remove();
    }

    public static int nextSplitIdx = 0;

    /**
     *  从 callInst 开始, 将基本块分成两半, 并处理其中的 PhiInst
     * @param callInst
     * @param it
     * @return 新分出来的基本块
     */
    public BasicBlock splitAt(CallInst callInst, Iterator<Instruction> it) {
        var newBlock = new BasicBlock(label + "_split" + (nextSplitIdx++), owner);
        while (it.hasNext()) {
            var inst = it.next();
            it.remove();
            inst.parent = newBlock;
            newBlock.insts.add(inst);
            // 处理指令的所有操作数
            for (var instUsePhi : inst.oprands) {
                // 如果当前指令使用了 PhiInst, 而当前块已经被分割到新的 bb 中
                // 那么, 使用的 Phi 产生的 Use 关系, 已经从旧 那么就需要更新 PhiInst 的 oprands
                if (!(instUsePhi.value instanceof PhiInst)) {
                    continue;
                }
                var phi = (PhiInst) instUsePhi.value;
                for (int i = 0; i < phi.preds.size(); ++i) {
                    var phiPred = phi.preds.get(i);
                    // var val = phi.oprands.get(i);
                    assert phiPred != null;
                    var phiPredBB = ((BasicBlockValue) phiPred.value).b;
                    if (phiPredBB == callInst.parent) {
                        phiPredBB.val.removeUse(instUsePhi);
                        phi.overridePred(i, newBlock.val);
                    }
                }
            }
        }
        var bbindex = owner.bbs.indexOf(this);
        owner.bbs.add(bbindex + 1, newBlock);

        return newBlock;
    }

    public boolean idominate(BasicBlock block) {
        if (block == this) {
            return true;
        }
        return idoms.contains(block);
    }
}
