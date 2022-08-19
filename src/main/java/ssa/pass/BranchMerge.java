package ssa.pass;

import ds.Global;
import ssa.ds.BasicBlock;
import ssa.ds.BasicBlockValue;
import ssa.ds.BranchInst;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.JumpInst;
import ssa.ds.Module;
import ssa.ds.PhiInst;
import ssa.ds.Type;
import ssa.ds.Use;

/**
 * 完成的优化：
 * removeUselessPhi：如果基本块只剩一个前驱，phi指令可以替换
 * onlyOneUncondBr：基本块内只有一个无条件跳转，且去掉该基本块不会导致后继中的 phi
 * 歧义，则可以去掉该基本块
 * basicBlockMerging：基本块结尾只有一个无条件跳转，且目标基本块只有一个前驱，两个基本块可以合并
 * deadBlockElimination：去掉不可达基本块
 * constantCondBr：有条件跳转的条件为常量，或者两个目标基本块相同，可替换成无条件跳转
 */
public class BranchMerge {

    public static void process(Module m) {
        m.funcs.forEach(func -> {
            var self = new BranchMerge(func);
            self.runBranchOptimization();
        });
    }

    Func func;
    boolean isPhiRemoved;

    public BranchMerge(Func f) {
        this.func = f;
    }

    public boolean runBranchOptimization() {
        boolean completed = true;
        isPhiRemoved = false;
        do {
            completed = removeUselessPhi();
            completed &= onlyOneUncondBr();
            completed &= basicBlockMerging();
            completed &= constantCondBr();
            completed &= deadBlockElimination();
        } while(!completed);
        return isPhiRemoved;
    }

    private boolean removeUselessPhi() {
        boolean completed = true;
        for (var bb : func.bbs) {
            int npred = bb.pred().size();
            if (npred > 1) {
                continue;
            }
            var it = bb.insts.iterator();
            while (!it.hasNext()) {
                var inst = it.next();
                if (!(inst instanceof PhiInst)) {
                    break;
                }
                assert npred == 1;
                if (npred == 1) {
                    assert inst.oprands.size() == 1;
                    inst.replaceAllUseWith(inst.getOperand0());
                    bb.removeInstWithIterator(inst, it);
                    isPhiRemoved = true;
                    completed = false;
                }
            }
        }

        return completed;
    }

    private boolean onlyOneUncondBr() {
        boolean completed = true;
        for (var bb: func.bbs){
            var termInst = bb.getTerminator();
            var bbv = bb.getValue();
            var bbPreds = bb.pred();

            if (bb.insts.size() != 1) {
                continue;
            }
            if (!(termInst instanceof JumpInst)) {
                continue;
            }
            var branchInst = (JumpInst) termInst;
            var succV = (BasicBlockValue) branchInst.getOperand0();

            boolean phiMultiSource = false;
            var succ = succV.b;
            var succPreds = succ.pred();
            var nSuccPreds = succPreds.size();
            if (succ.entry() instanceof PhiInst) {
                for (var pred : bbPreds) {
                    // 如果跳转目标块的前驱包含本块的前驱，则不能去除该跳转
                    if (succPreds.contains(pred)) {
                        phiMultiSource = true;
                    }
                }
            }

            if (phiMultiSource) {
                continue;
            }

            Global.logger.trace("Eliminating basic block: "+bb.label);

            completed = false;

            // 维护 pred 的所有 br，维护 pred/succ 关系
            // int bbIndex = succ.pred().indexOf(bb);
            // succ.pred().remove(bb);

            for (var pred : bbPreds) {
                var predTerm = (Instruction) (pred.getTerminator());
                if (predTerm instanceof JumpInst) {
                    var jmp = (JumpInst) predTerm;
                    jmp.oprands.set(0, new Use(jmp, succV));
                } else if (predTerm instanceof BranchInst) {
                    var preBrInst = (BranchInst) predTerm;
                    for (int i = 1; i <= 2; i++) {
                        var target = preBrInst.oprands.get(i);
                        assert target.value instanceof BasicBlockValue;
                        var targetBBV = (BasicBlockValue) target.value;
                        if (targetBBV.b == bb) {
                            preBrInst.replaceUseWith(target, new Use(preBrInst, succV));
                        }
                    }
                }
            }

            // 维护 succ 的 phi
            for (var inst : succ.insts) {
                if (!(inst instanceof PhiInst)) {
                    break;
                }
                var phiInst = (PhiInst) inst;
                int pindex = -1;
                for (int i=0;i<nSuccPreds;i++) {
                    if (phiInst.preds.get(i).value == bbv) {
                        pindex = i;
                    }
                }
                assert pindex != -1;
                // Value valueFromBB = phiInst.oprands.get(pindex).value;
                // basicBlockValue那边的use也不用删了
                phiInst.preds.remove(pindex);
                Use valueFromBB = phiInst.oprands.remove(pindex);
                valueFromBB.value.removeUse(valueFromBB);
                for (var i = 0; i < bbPreds.size(); i++) {
                    phiInst.addOperand(valueFromBB.value, bbPreds.get(i).getValue());
                }
            }

            // 删掉 bb
            branchInst.removeAllOpr();
            func.bbs.remove(bb);

        }

        return completed;
    }

    private boolean basicBlockMerging() {
        new BasicBlockMerging(func).doAnalysis();
        return true;
    }

    private boolean removePredBasicBlock(BasicBlock pred, BasicBlock succ) {
        for (var inst: succ.insts) {
            if (!(inst instanceof PhiInst)) {
                break;
            }
            PhiInst phi = (PhiInst) inst;
            int size = phi.preds.size();
            for (int i=0;i<size;i++) {
                var bbv = (BasicBlockValue) phi.preds.get(i).value;
                if (bbv != pred.getValue()) {
                    continue;
                }
                // 移除这个operand
                var bbvUse = phi.preds.get(i);
                var valUse = phi.oprands.get(i);
                bbvUse.value.removeUse(bbvUse);
                valUse.value.removeUse(valUse);
                phi.preds.remove(i);
                phi.oprands.remove(i);
                // 仅剩1个oprand的情况留给前面的分析去remove
                // 0个oprand的情况交给DeadBlockElimination移除
                // 等于0的情况由于没有调用replaceAllUseWith应该不会影响GVN
                // if (phi.preds.size() <= 1) {
                //     isPhiRemoved = true;
                // }
            }
        }
        return true;
    }

    private boolean deadBlockElimination() {
        new DeadBlockElimination(func).doAnalysis();
        return true;
    }

    private boolean constantCondBr() {
        boolean completed = true;

        for (var bb : func.bbs) {

            var brInst = bb.getTerminator();
            if (brInst instanceof BranchInst) {
                assert brInst.oprands.size() == 3;
                if (brInst.getOperand1() == brInst.getOperand2()) {

                    var targetBBV = ((BasicBlockValue) (brInst.getOperand1()));
                    // targetBB 一定没有 phi
                    assert !targetBBV.b.hasPhi();
                    // 把BranchInst替换为JumpInst
                    var jmp = new JumpInst(bb, targetBBV);
                    // 移除了对BasicBlockValue的use就是移除了pred关系
                    brInst.removeAllOpr();
                    int ind = bb.insts.size()-1;
                    // 更改了就是移除了
                    bb.insts.set(ind, jmp);
                    
                    completed = false;
                } else if (brInst.getOperand0() instanceof ConstantValue) {
                    var cond = (ConstantValue) (brInst.getOperand0());
                    assert cond.type.equals(Type.Boolean);
                    var oval = cond.val;
                    assert oval instanceof Integer;
                    int ival = ((Integer)oval).intValue();
                    assert ival == 1 || ival == 0;
                    BasicBlockValue targetBBV;
                    BasicBlock unreachBB;
                    if (ival == 1) {
                        targetBBV = ((BasicBlockValue) (brInst.getOperand1()));
                        unreachBB = ((BasicBlockValue) (brInst.getOperand2())).b;
                    } else {
                        targetBBV = ((BasicBlockValue) (brInst.getOperand2()));
                        unreachBB = ((BasicBlockValue) (brInst.getOperand1())).b;
                    }

                    // 把BranchInst替换为JumpInst
                    var jmp = new JumpInst(bb, targetBBV);
                    // 移除了对BasicBlockValue的use就是移除了pred关系
                    brInst.removeAllOpr();
                    int ind = bb.insts.size()-1;
                    // 更改了Terminator就是移除了succ
                    bb.insts.set(ind, jmp);
                    removePredBasicBlock(bb, unreachBB);
                    completed = false;
                }
            }
        }

        return completed;
    }
}
