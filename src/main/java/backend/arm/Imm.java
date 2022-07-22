package backend.arm;

import backend.AsmOperand;

public abstract class Imm extends AsmOperand {
    public abstract long highestOneBit();

    // 基本是为了MOVW+MOVT
    public abstract Imm getLow16();
    public abstract Imm getHigh16();

    @Override
    public abstract String toString();
}
