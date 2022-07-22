package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import dst.ds.BinaryOp;

/**
 * 抽象指令，如果立即数字段无法放下则额外生成MOV32
 * 存放结果的to必须是寄存器
 * MOVW会自动清空高16bit，所以立即数小于16bit范围的可以直接MOVW
 * 1. ADD Rd, Rn, #<imm12> 立即数范围是 0-4095 否则就用ADD Rd, Rn, Rm
 * 2. SUB Rd, Rn, #<imm12> 同上
 * 3. MUL Rd, Rm, Rs 无法使用立即数，必须要转换了
 * 4. SDIV Rd, Rn, Rm 有符号除法，同上
 * 5. 取模：不支持，转换为调用`___modsi3`
 * 转换函数在Generator
 */
public class BinOpInst extends AsmInst {
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
}
