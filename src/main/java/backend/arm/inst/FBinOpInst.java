package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmOperand;
import dst.ds.BinaryOp;

/**
* 1. fadd - vadd.f32 Fd, Fn, Fm 不支持立即数
* 2. fsub - vsub.f32同上
* 3. fmul - vmul.f32
* 4. fdiv - vdiv.f32
* 5. 浮点数好像不支持取模
 */
public class FBinOpInst extends AsmInst {
    public BinaryOp op;

    public FBinOpInst(AsmBlock p, BinaryOp op, AsmOperand to, AsmOperand op1, AsmOperand op2) {
        parent = p;
        this.op = op;
        assert to.isFloat && op1.isFloat && op2.isFloat;
        defs.add(to);
        uses.add(op1);
        uses.add(op2);
    }

    public static String opToString(BinaryOp op) {
        switch(op) {
            case ADD: return "VADD.F32";
            case DIV: return "VDIV.F32";
            case MUL: return "VMUL.F32";
            case SUB: return "VSUB.F32";
            default: return null;
        }
    }

    @Override
    public String toString() {
        return String.format("%s\t%s, %s, %s", opToString(op), defs.get(0).toString(), 
                                    uses.get(0).toString(), uses.get(1).toString());
    }
}
