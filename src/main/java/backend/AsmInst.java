package backend;

import java.util.ArrayList;
import java.util.List;

public class AsmInst {
    public AsmBlock parent;
    // public List<AsmOperand> operands;
    // 保存参数的操作数 和用于liveness分析（只分析里面的VirtReg类型）
    public List<AsmOperand> defs = new ArrayList<>();
    public List<AsmOperand> uses = new ArrayList<>();

    // 注释
    public String comment;

    public String getCommentStr() {
        if (comment != null) {
            return "\t\t@ " + comment;
        }
        return "";
    }
}
