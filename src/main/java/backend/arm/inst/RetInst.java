package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;

// 代表了需要插入epilogue和返回的抽象指令
// 参数是一个寄存器，需要手动生成mov放到r0
public class RetInst extends AsmInst {
    public RetInst(AsmBlock p) {
        parent = p;
    }

    @Override
    public String toString() {
        if (uses.size() > 0) {
            return "RetInst "+ uses.get(0).toString();
        } else {
            return "RetInst";
        }
    }
}
