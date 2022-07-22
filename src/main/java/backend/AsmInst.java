package backend;

import java.util.List;

public class AsmInst {
    public AsmBlock parent;
    // public List<AsmOperand> operands;
    // 优化liveness分析
    public List<AsmOperand> defs;
    public List<AsmOperand> uses;
}
