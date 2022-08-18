package ssa.ds;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;


public class MemPhiInst extends PhiInst {

    public List<Use> preds = new ArrayList<>();

    public MemPhiInst(BasicBlock b, int npreds) {
        super(b);
        for (int i = 0; i < npreds; i++) {
            preds.add(new Use(this, new PlaceHolder()));
        }
    }

    public void setIncomingVals(int predIndex, Value value) {
        
    }
}
