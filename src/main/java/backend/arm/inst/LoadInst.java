package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.StackOperand;
import backend.arm.StackOpInst;

/**
 * 同时表示
 * 1. 普通的基于寄存器的加载
 * 3. 基于sp/bp带偏移的加载
 * dest必须是寄存器，addr可以是寄存器，StackOperand，Imm立即数。
 */
public class LoadInst extends AsmInst implements StackOpInst {
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

    public boolean isImmFit(StackOperand so) {
        return LoadInst.isImmFitStatic(so);
    }

    // `LDR/STR Rd, [Rn {, #<offset>}]` -4095 to +4095
    public static boolean isImmFitStatic(StackOperand so) {
        // 范围暂时放窄一些，等测例都过了再改回来
        if (so.offset <= 4070 && so.offset >= -4070) {
            return true;
        }
        return false;
    }
}
