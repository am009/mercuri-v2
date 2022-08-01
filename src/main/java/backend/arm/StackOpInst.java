package backend.arm;

import backend.StackOperand;

public interface StackOpInst {
    public boolean isImmFit(StackOperand so);
}
