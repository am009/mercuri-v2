package ir.ds;

import java.util.List;

public class BasicBlock {
    public Integer id;
    public List<BasicBlock> predecessors;
    public List<BasicBlock> successors;
    public List<BasicBlock> dominators; // this basicblock is dominating `dominators`
    public List<BasicBlock> postdominators; // this basicblock is dominatied by `postdominators`
    public BasicBlock immDominator; // immediate dominator
    public Integer level; // level of this basicblock in the dominator tree

    public List<Instruction> instructions;
}
