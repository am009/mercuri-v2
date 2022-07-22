package backend.arm;

import backend.AsmOperand;

public class Reg extends AsmOperand {
    enum Type {
        // args and return value (caller saved)
        r0,
        r1,
        r2,
        r3,
        // local variables (callee saved)
        r4,
        r5,
        r6,
        r7,
        r8,
        r9,
        r10,
        fp,
        // special purposes
        ip,
        sp,
        lr,
        pc,
        // some aliases
        // fp = r11,  // frame pointer (omitted), allocatable
        // ip = r12,  // ipc scratch register, used in some instructions (caller saved)
        // sp = r13,  // stack pointer
        // lr = r14,  // link register (caller saved)
        // pc = r15,  // program counter
    }
    Type ty;
    String comment;

    public Reg(Type t) {
        ty = t;
    }
}
