package ssa.ds;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// 终止基本块的指令，包括 Branch、Jump、Return
public abstract class TerminatorInst extends Instruction {
    List<BasicBlock> getSuccessors() {
        var ret = new ArrayList<BasicBlock>();
        oprands.forEach(u->{
            if (u.value instanceof BasicBlockValue) {
                ret.add(((BasicBlockValue)u.value).b);
            }
        });
        return ret;
        // return oprands.stream().map(u -> {return ((BasicBlockValue) u.value).b;}).collect(Collectors.toList());
    }
}
