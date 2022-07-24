package backend.arm.inst;

import backend.AsmBlock;

// 代表了需要插入epilogue和返回的抽象指令
// use可能为空，代表空的return，也可能返回一个值
public class RetInst extends ConstrainRegInst {
    // 寄存器分配的约束-返回值

    public RetInst(AsmBlock p) {
        parent = p;
    }

    public static String format = "mov\tsp, fp\t@ %s\n"
                                    + "\tpop\t{fp, lr}\n"
                                    + "\tbx\tlr";

    @Override
    public String toString() {
        String comment;
        if (uses.size() > 0) {
            comment = "ret " + uses.get(0).toString();
        } else {
            comment = "ret void";
        }
        return String.format(format, comment);
    }
}
