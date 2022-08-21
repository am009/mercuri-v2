package ssa.pass.ds;

import java.util.*;

import backend.AsmBlock;
import ssa.ds.BasicBlock;
import ssa.ds.BranchInst;
import ssa.ds.Func;
import ssa.ds.Module;

public class LoopAnalysis {

    Module module;
    private Map<Func, LoopNode> rootNodes;
    private Map<BasicBlock, LoopNode> blockNodeMap;
    private Map<BasicBlock, LoopNode> headerLoopNodeMap;

    public LoopAnalysis(Module module) {
        this.module = module;
    }

    public Map<Func, LoopNode> getRootNodes() {
        return rootNodes;
    }

    public Map<BasicBlock, LoopNode> getBlockNodeMap() {
        return blockNodeMap;
    }

    public int getBlockDepth(AsmBlock ASMBlock) { //gugu
        BasicBlock irBlock = ASMBlock.ssaBlock;
        if (irBlock == null)
            return 0;
        return blockNodeMap.get(irBlock).getDepth();
    }

    public boolean run() {
        for (Func function : module.funcs) { //gugu changed
            if (null == function.isFunctional())
                return false;
        }

        rootNodes = new HashMap<>();
        blockNodeMap = new HashMap<>();
        headerLoopNodeMap = new HashMap<>();
        for (Func function : module.funcs)
            rootNodes.put(function, constructLoopTree(function));

        return false;
    }

    private LoopNode constructLoopTree(Func func) {
        LoopNode root = new LoopNode(func.entry(), this); //
        rootNodes.put(func, root);

        detectNaturalLoop(func.entry(), new HashSet<>(), root);
        constructLoopNestTree(func.entry(), new HashSet<>(), root);
        root.setDepth(0);
        dfsLoopTree(root);

        return root;
    }

    private void detectNaturalLoop(BasicBlock block, Set<BasicBlock> visit, LoopNode root) {
        visit.add(block);
        root.addLoopBlock(block); //always add to root
        for (BasicBlock succ : block.succ()) {
            if (succ.idominate(block)) {
                // the back egde block->successor(header)
                extractNaturalLoop(succ, block);
            } else if (!visit.contains(succ))
                detectNaturalLoop(succ, visit, root);
        }
    }

    private void constructLoopNestTree(BasicBlock block, Set<BasicBlock> visit, LoopNode currentLoop) {
        visit.add(block);

        LoopNode child = null;
        if (block == currentLoop.getHeader()) {
            // block == entrance block????????
            currentLoop.setUniqueLoopBlocks(new HashSet<>(currentLoop.getLoopBlocks())); //???
        } else if (headerLoopNodeMap.containsKey(block)) {
            child = headerLoopNodeMap.get(block);
            child.setFather(currentLoop);
            currentLoop.addChild(child);

            currentLoop.removeUniqueLoopBlocks(child);
            child.setUniqueLoopBlocks(new HashSet<>(child.getLoopBlocks()));
        }

        for (var successor : block.succ()) {
            if (!visit.contains(successor)) {
                LoopNode nextLoopNode;
                if (child != null)
                    nextLoopNode = child;
                else
                    nextLoopNode = currentLoop;

                while (nextLoopNode != null && !nextLoopNode.getLoopBlocks().contains(successor))
                    nextLoopNode = nextLoopNode.getFather();

                assert nextLoopNode != null;
                constructLoopNestTree(successor, visit, nextLoopNode);
            }
        }
    }

    private void dfsLoopTree(LoopNode loop) {
        for (var block : loop.getUniqueLoopBlocks())
            blockNodeMap.put(block, loop);

        for (LoopNode child : loop.getChildren()) {
            child.setDepth(loop.getDepth() + 1);
            dfsLoopTree(child);
        }

        //set exitblock
        Set<BasicBlock> exitBlocks = new HashSet<>();
        if (loop.hasFather()) {
            for (LoopNode child : loop.getChildren()) {
                for (var exitBlock : child.getExitBlocks()) {
                    assert exitBlock.getTerminator() instanceof BranchInst;
                    var exitInst = ((BranchInst) exitBlock.getTerminator());
                    if (!loop.getLoopBlocks().contains(exitInst.getTrueBlock())) {
                        exitBlocks.add(exitBlock);
                        break;
                    }
                    if (exitInst.getCond() != null && !loop.getLoopBlocks().contains(exitInst.getFalseBlock())) {
                        exitBlocks.add(exitBlock);
                        break;
                    }
                }
            }
            for (var exitBlock : loop.getUniqueLoopBlocks()) {
                assert exitBlock.getTerminator() instanceof BranchInst;
                var exitInst = ((BranchInst) exitBlock.getTerminator());
                if (!loop.getLoopBlocks().contains(exitInst.getTrueBlock())) {
                    exitBlocks.add(exitBlock);
                    break;
                }
                if (exitInst.getCond() != null && !loop.getLoopBlocks().contains(exitInst.getFalseBlock())) {
                    exitBlocks.add(exitBlock);
                    break;
                }
            }
        }
        loop.setExitBlocks(exitBlocks);
    }

    private void extractNaturalLoop(BasicBlock header, BasicBlock end) {
        LoopNode loop = new LoopNode(header, this);
        loop.addLoopBlock(header);
        HashSet<BasicBlock> visit = new HashSet<>();
        Queue<BasicBlock> queue = new LinkedList<>();
        queue.offer(end);
        visit.add(end);
        while (!queue.isEmpty()) {
            BasicBlock block = queue.poll();
            if (header.idominate(block))
                loop.addLoopBlock(block);
            for (BasicBlock predecessor : block.pred()) {
                if (predecessor != header && !visit.contains(predecessor)) {
                    queue.offer(predecessor);
                    visit.add(predecessor);
                }
            }
        }
        //merge loop with the same header, the result may be not natural loop
        if (!headerLoopNodeMap.containsKey(header))
            headerLoopNodeMap.put(header, loop);
        else {
            LoopNode existingHeader = headerLoopNodeMap.get(header);
            existingHeader.getLoopBlocks().addAll(loop.getLoopBlocks());
        }
    }

    public void setRootNodes(Map<Func, LoopNode> rootNodes) {
        this.rootNodes = rootNodes;
    }

    public void setBlockNodeMap(Map<BasicBlock, LoopNode> blockNodeMap) {
        this.blockNodeMap = blockNodeMap;
    }

    public Map<BasicBlock, LoopNode> getHeaderLoopNodeMap() {
        return headerLoopNodeMap;
    }

    public void setHeaderLoopNodeMap(Map<BasicBlock, LoopNode> headerLoopNodeMap) {
        this.headerLoopNodeMap = headerLoopNodeMap;
    }

}
