package ssa.ds;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * operands里用值的顺序要和preds的对应，同时和基本块的所有前驱对应
 */
public class PhiInst extends Instruction {

    // incomings
    public List<Use> preds = new ArrayList<>();

    public PhiInst(BasicBlock b) {
        parent = b;
    }

    public void addOperand(Value val, BasicBlockValue from) {
        oprands.add(new Use(this, val));
        preds.add(new Use(this, from));
    }

    public void replacePredUseWith(Use oldu, Use newu) {
        int ind = preds.indexOf(oldu);
        assert ind != -1;
        preds.set(ind, newu);
    }

    @Override
    public String getOpString() {
        return "phi";
    }

    // %indvar = phi i32 [ 0, %LoopHeader ], [ %nextindvar, %Loop ]
    @Override
    public String toString() {
        var prefix = String.format("%s = phi %s ", toValueString(), type.toString());
        var sj = new StringJoiner(", ");        
        int s = oprands.size();
        assert oprands.size() == preds.size();
        // must consider all predecessor
        assert preds.size() == parent.pred().size();
        for(int i=0;i<s;i++) {
            var valStr = oprands.get(i).value.toValueString();
            var blkStr = preds.get(i).value.toValueString();
            if (blkStr.startsWith("label ")) {
                blkStr = blkStr.substring(6);
            }
            sj.add(String.format("[ %s, %s ]", valStr, blkStr));
        }
        return prefix + sj.toString();
    }
}
