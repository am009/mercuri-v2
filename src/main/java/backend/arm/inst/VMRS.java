package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;

public class VMRS extends AsmInst {
    
    public VMRS(AsmBlock p) {
        parent = p;
    }

    @Override
    public String toString() {
        return "vmrs\tAPSR_nzcv, FPSCR";
    }
}
