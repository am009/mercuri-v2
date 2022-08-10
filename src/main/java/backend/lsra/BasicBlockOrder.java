package backend.lsra;

import java.util.List;

import backend.AsmBlock;
import backend.AsmModule;

public class BasicBlockOrder {
    AsmModule module;

    public BasicBlockOrder(AsmModule m) {
        this.module = m;
    }

    private List<AsmBlock> worklist;
    void computeBlockOrder() {
        worklist.clear();
        for (var f : module.funcs) {
            
        }
    }
}
