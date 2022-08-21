package backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import backend.arm.inst.BrInst;
import ssa.ds.BasicBlock;

public class AsmBlock {
    // 双向链表
    public AsmBlock prev;
    public AsmBlock next;
    // 特殊情况：比如尾调用优化时产生的 AsmBlock, 将会此处为 null
    public BasicBlock ssaBlock;

    public String label;
    public List<AsmInst> insts;
    public int termInstCount; // 基本块末尾的跳转指令的数量

    // 前驱和后继节点。pred需要初始化，succ由那边生成跳转指令的时候初始化。
    public List<AsmBlock> pred;
    public List<AsmBlock> succ;
    public static final String prefix = ".LBB_";

    public AsmBlock(String label, BasicBlock ssaBlock) {
        this.label = prefix + label;
        insts = new ArrayList<>();
        pred = new ArrayList<>();
        this.ssaBlock = ssaBlock;
    }

    public void addAllBeforeJump(Collection<? extends AsmInst> is) {
        if (insts.size() != 0 && insts.get(insts.size() - 1) instanceof BrInst) {
            int ind = insts.size() - 1;
            while (ind >= 0 && insts.get(ind) instanceof BrInst) {
                ind -= 1;
            }
            ind += 1;
            // 有br指令
            insts.addAll(ind, is);
        } else { // 无br指令
            insts.addAll(is);
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(label).append(":\n");
        for (var inst : insts) {
            sb.append("\t").append(inst.toString());
            sb.append(inst.getCommentStr());
            sb.append("\n");
        }

        return sb.toString();
    }
}
