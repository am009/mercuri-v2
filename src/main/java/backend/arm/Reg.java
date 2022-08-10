package backend.arm;

import java.nio.channels.AsynchronousServerSocketChannel;

import backend.AsmOperand;

public class Reg extends AsmOperand {
    public enum Type {
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
        pc;
        // some aliases
        // fp = r11,  // frame pointer (omitted), allocatable
        // ip = r12,  // ipc scratch register, used in some instructions (caller saved)
        // sp = r13,  // stack pointer
        // lr = r14,  // link register (caller saved)
        // pc = r15,  // program counter

        public static Type[] values = Type.values();
        public int toInt() {
            switch (this) {
                case r0: return 0;
                case r1: return 1;
                case r2: return 2;
                case r3: return 3;
                case r4: return 4;
                case r5: return 5;
                case r6: return 6;
                case r7: return 7;
                case r8: return 8;
                case r9: return 9;
                case r10: return 10;
                case fp: return 11;
                case ip: return 12;
                case sp: return 13;
                case lr: return 14;
                case pc: return 15;
                default: throw new UnsupportedOperationException();
            }
        }
        public boolean isCalleeSaved() {
            int ind = this.toInt();
            assert ind <= 10;
            if (ind >=4 && ind <= 10) {
                return true;
            }
            return false;
        }    
    }
    public Type ty;

    public Reg(Type t) {
        isFloat = false;
        ty = t;
    }

    @Override
    public String toString() {
        return ty.toString();
    }

    public Integer getIndex(){
        assert !isFloat;
        return this.ty.toInt();
    }

}
