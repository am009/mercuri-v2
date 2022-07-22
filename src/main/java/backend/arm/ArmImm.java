package backend.arm;

import backend.AsmOperand;

public class ArmImm extends AsmOperand {
    int value;
    // 对指令的imm范围要求，最小填8
    int bits;
    public ArmImm(int val, int b) {
        value = val;
        bits = b;
    }
}
