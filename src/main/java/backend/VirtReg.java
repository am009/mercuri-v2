package backend;

public class VirtReg extends AsmOperand {
    public int index;
    public VirtReg(int ind) {
        index = ind;
    }

    @Override
    public String toString() {
        return "vreg"+String.valueOf(index);
    }
}
