package backend;

import java.util.ArrayList;
import java.util.List;

public class AsmBlock {
    // 双向链表
    public AsmBlock prev;
    public AsmBlock next;

    public String label;
    public List<AsmInst> insts;
    public int termInstCount; // 基本块末尾的跳转指令的数量

    // 前驱和后继节点。pred需要初始化，succ由那边生成跳转指令的时候初始化。
    public List<AsmBlock> pred;
    public List<AsmBlock> succ;
    
    public AsmBlock(String label) {
        this.label = label;
        insts = new ArrayList<>();
        pred = new ArrayList<>();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(label).append(":\n");
        for (var inst: insts) {
            sb.append("\t").append(inst.toString()).append("\n");
        }

        return sb.toString();
    }
}
