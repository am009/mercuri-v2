package ssa.pass;

import ssa.ds.BasicBlock;
import ssa.ds.Func;

import java.util.ArrayList;
import java.util.BitSet;

public class DomInfo {

    public static void computeDominanceInfo(Func function) {
        // TODO bbs[0] 一定是 entry 吗？
        BasicBlock entry = function.entry();
        int numNode = function.bbs.size();
        ArrayList<BitSet> domers = new ArrayList<>(numNode);
        ArrayList<BasicBlock> bbList = new ArrayList<>();

        int index = 0;
        // 初始化
        for (var bb : function.bbs) {
            bb.domers.clear();
            bb.idoms.clear();
            bbList.add(bb);
            domers.add(new BitSet());
            domers.get(index).set(index);
            index++;
        }

        // 计算支配者
        boolean unstable = true;
        while (unstable) {
            unstable = false;
            index = 0;

            for (var bb : function.bbs) {
                // no need to consider entry node
                if (bb != entry) {
                    BitSet temp = new BitSet();
                    temp.set(0, numNode);

                    // temp <- {index} \cup (\Bigcap_{j \in preds(index)} domer(j) )
                    for (BasicBlock pre_bb : bb.pred()) {
                        int preIndex = bbList.indexOf(pre_bb);
                        temp.and(domers.get(preIndex));
                    }
                    temp.set(index);

                    if (!temp.equals(domers.get(index))) {
                        // replace domers[index] with temp
                        domers.get(index).clear();
                        domers.get(index).or(temp);
                        unstable = true;
                    }
                }
                index++;
            }
        }

        for (int i = 0; i < numNode; i++) {
            BasicBlock bb = bbList.get(i);
            BitSet domerInfo = domers.get(i);
            for (int domerIndex = domerInfo.nextSetBit(0); domerIndex >= 0; domerIndex = domerInfo
                    .nextSetBit(domerIndex + 1)) {
                BasicBlock domerbb = bbList.get(domerIndex);
                bb.domers.add(domerbb);
            }
        }

        // calculate doms and idom
        for (int i = 0; i < numNode; i++) {
            BasicBlock bb = bbList.get(i);

            for (BasicBlock maybeIdomerbb : bb.domers) {
                if (maybeIdomerbb != bb) {
                    boolean isIdom = true;
                    for (BasicBlock domerbb : bb.domers) {
                        if (domerbb != bb && domerbb != maybeIdomerbb && domerbb.domers
                                .contains(maybeIdomerbb)) {
                            isIdom = false;
                            break;
                        }
                    }

                    if (isIdom) {
                        bb.idomer = maybeIdomerbb;
                        maybeIdomerbb.idoms.add(bb);
                        break;
                    }
                }
            }
        }

        // calculate dom level
        computeDominanceLevel(entry, 0);
    }

    public static void computeDominanceFrontier(Func function) {
        for (var bb : function.bbs) {
            bb.domiFrontier.clear();
        }

        for (var a : function.bbs) {
            for (BasicBlock b : a.succ()) {
                BasicBlock x = a;
                while (x == b || !b.domers.contains(x)) {
                    if (!x.domiFrontier.contains(b)) {
                        // maybe better to design the data structure of dominance frontier as a set
                        x.domiFrontier.add(b);
                    }
                    x = x.idomer;
                }
            }
        }
    }

    public static void computeDominanceLevel(BasicBlock bb, Integer domLevel) {
        bb.domLevel = domLevel;
        for (BasicBlock succ : bb.idoms) {
            computeDominanceLevel(succ, domLevel + 1);
        }
    }
}
