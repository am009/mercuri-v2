package ssa.ds;

import java.util.List;
import java.util.stream.Collectors;

// 终止基本块的指令，包括Branch、Jump、Return
public abstract class TerminatorInst extends Instruction {
    List<BasicBlockValue> getSuccessors() {
        return oprands.stream().map(u -> {return (BasicBlockValue) u.value;}).collect(Collectors.toList());
    }
}
