package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;

/**
 * 同时表示
 * 1. 普通的基于寄存器的加载
 * 2. float加载到s0这样的寄存器
 * 3. 基于sp/bp带偏移的加载
 * 4. 从给定内存地址中加载，生成MOV32和load两个指令。
 * dest必须是寄存器，addr可以是寄存器，StackOperand，Imm立即数。
 */
public class LoadInst extends AsmInst {
    public LoadInst(AsmBlock p, AsmOperand dest, AsmOperand addr) {
        parent = p;
        defs.add(dest);
        uses.add(addr);
    }

    @Override
    public String toString() {
        return String.format("LDR\t%s, [%s]",
            defs.get(0).toString(), uses.get(0).toString());
    }
}
