package backend.arm.inst;

import backend.AsmBlock;
import backend.AsmInst;

public class Prologue extends AsmInst {
    public Prologue(AsmBlock p) {
        parent = p;
    }
}
