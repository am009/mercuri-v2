package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.arm.Generator;
import backend.arm.IntImm;
import backend.arm.Reg;
import dst.ds.BinaryOp;

public class Prologue extends ConstrainRegInst {
    // 寄存器分配的起始约束 - 参数

    // 所在函数
    AsmFunc func;

    public Prologue(AsmBlock p, AsmFunc f) {
        func = f;
        parent = p;
    }

    public static String format = "push\t{fp, lr}\n"
                                + "\tmov\tfp, sp\n";
    @Override
    public String toString() {
        var sb = new StringBuilder(format);
        var stackSize = Math.toIntExact(func.sm.totalStackSize());
        var subs = Generator.expandBinOp(new BinOpInst(null, BinaryOp.SUB, new Reg(Reg.Type.sp), new Reg(Reg.Type.sp), new IntImm(stackSize)));
        subs.forEach(inst -> {sb.append("\t").append(inst.toString()).append("\n");});
        return sb.toString();
    }
}
