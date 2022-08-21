package ssa.pass.ds;

import java.util.*;

import common.Pair;
import ssa.ds.BasicBlock;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.PhiInst;

public class LoopNode {
    LoopAnalysis loopAnalysis;

    private BasicBlock header; //
    private Set<BasicBlock> loopBlocks;
    private Set<BasicBlock> uniqueLoopBlocks;
    private Set<BasicBlock> exitBlocks; //

    private LoopNode father;
    private ArrayList<LoopNode> children;

    private int depth; //???

    public LoopNode(BasicBlock header, LoopAnalysis loopAnalysis) {
        this.header = header;
        this.loopAnalysis = loopAnalysis;
        this.loopBlocks = new HashSet<>();
        this.uniqueLoopBlocks = null;
        this.exitBlocks = null;
        this.father = null;
        this.children = new ArrayList<>();
        this.depth = 0;
    }

    public void mergeLoopNode(LoopNode loop) {
        assert this.header == loop.header;
        this.loopBlocks.addAll(loop.loopBlocks);
    }

    public void removeUniqueLoopBlocks(LoopNode child) {
        assert uniqueLoopBlocks.containsAll(child.loopBlocks);
        uniqueLoopBlocks.removeAll(child.loopBlocks);
    }

    @Override
    public String toString() {
        return "LoopNode " + header.label;
    }

    public LoopAnalysis getLoopAnalysis() {
        return loopAnalysis;
    }

    public void setLoopAnalysis(LoopAnalysis loopAnalysis) {
        this.loopAnalysis = loopAnalysis;
    }

    public BasicBlock getHeader() {
        return header;
    }

    public void setHeader(BasicBlock header) {
        this.header = header;
    }

    public void setLoopBlocks(Set<BasicBlock> loopBlocks) {
        this.loopBlocks = loopBlocks;
    }

    public void setUniqueLoopBlocks(Set<BasicBlock> uniqueLoopBlocks) {
        this.uniqueLoopBlocks = uniqueLoopBlocks;
    }

    public LoopNode getFather() {
        return father;
    }

    public void setChildren(ArrayList<LoopNode> children) {
        this.children = children;
    }

    public void addLoopBlock(BasicBlock block) {
        this.loopBlocks.add(block);
    }

    public Set<BasicBlock> getLoopBlocks() {
        return loopBlocks;
    }

    public Set<BasicBlock> getUniqueLoopBlocks() {
        return uniqueLoopBlocks;
    }

    public Set<BasicBlock> getExitBlocks() {
        return exitBlocks;
    }

    public void setExitBlocks(Set<BasicBlock> exitBlocks) {
        this.exitBlocks = exitBlocks;
    }

    public void setFather(LoopNode father) {
        this.father = father;
    }

    public boolean hasFather() {
        return father != null;
    }

    public void addChild(LoopNode child) {
        children.add(child);
    }

    public ArrayList<LoopNode> getChildren() {
        return children;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
