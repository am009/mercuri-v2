package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import backend.arm.Imm;
import backend.arm.ImmOpInst;
import backend.arm.Operand2;
import dst.ds.BinaryOp;

/**
 * 存放结果的to必须是寄存器，不能是常量
 * 
 * 1. ADD Rd, Rn, #<imm12> 立即数范围是 0-4095 否则就用ADD Rd, Rn, Rm
 * 2. SUB Rd, Rn, #<imm12> 同上
 * 3. MUL Rd, Rm, Rs 无法使用立即数，必须要转换了
 * 4. SDIV Rd, Rn, Rm 有符号除法，同上
 * 5. 取模：不支持，在IR那边转换为调用相关eabi函数
 */
public class BinOpInst extends AsmInst implements ImmOpInst {
    public BinaryOp op;

    public BinOpInst(AsmBlock p, BinaryOp op, AsmOperand to, AsmOperand op1, AsmOperand op2) {
        parent = p;
        this.op = op;
        defs.add(to);
        uses.add(op1);
        uses.add(op2);
    }

    public static String opToString(BinaryOp op) {
        switch(op) {
            case ADD: return "ADD";
            case DIV: return "SDIV";
            case MUL: return "MUL";
            case SUB: return "SUB";
            default: return null;
        }
    }

    @Override
    public String toString() {
        return String.format("%s\t%s, %s, %s", opToString(op), defs.get(0).toString(), 
                                    uses.get(0).toString(), uses.get(1).toString());
    }

    public boolean isImmFit(Imm m) {
        if (op == BinaryOp.ADD || op == BinaryOp.SUB) {
            return Operand2.isImmFit(m);
        }
        return false;
    }
}
