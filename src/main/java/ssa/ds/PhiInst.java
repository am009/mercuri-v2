package ssa.ds;

import java.util.StringJoiner;

/**
 * operands里用值的顺序要和parent的predecessor对应，即和BasicBlockValue里的Use列表的顺序对应。
 */
public class PhiInst extends Instruction {

    public PhiInst(BasicBlock b) {
        parent = b;
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
        // assert oprands.size() == parent.pred().size();
        for(int i=0;i<s;i++) {
            var valStr = oprands.get(i).value.toValueString();
            var blkStr = parent.pred().get(i).getValue().toValueString();
            if (blkStr.startsWith("label ")) {
                blkStr = blkStr.substring(6);
            }
            sj.add(String.format("[ %s, %s ]", valStr, blkStr));
        }
        return prefix + sj.toString();
    }
}
