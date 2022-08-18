package ssa.pass;

import ssa.ds.BasicBlock;
import ssa.ds.Func;

import java.util.ArrayList;
import java.util.BitSet;

import ds.Global;

public class DomInfo {

    public static void computeDominanceInfo(Func func) {
        // TODO bbs[0] 一定是 entry 吗？
        BasicBlock funcEntry = func.entry();
        int numNode = func.bbs.size();
        ArrayList<BitSet> domers = new ArrayList<>(numNode);
        ArrayList<BasicBlock> bbList = new ArrayList<>();

        int index = 0;
        // 初始化
        for (var bb : func.bbs) {
            bb.domers.clear();
            bb.idoms.clear();
            bbList.add(bb);
            domers.add(new BitSet());

            if (bb == funcEntry) {
                domers.get(index).set(index);
            } else {
                domers.get(index).set(0, numNode);
            }

            domers.get(index).set(index);
            index++;
        }

        // 计算支配者
        boolean unstable = true;
        while (unstable) {
            unstable = false;
            index = 0;

            for (var bb : func.bbs) {
                // no need to consider entry node
                if (bb != funcEntry) {
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

        computeDominanceLevel(funcEntry, 0);
    }

    public static void computeDominanceFrontier(Func func) {
        for (var bb : func.bbs) {
            bb.domiFrontier.clear();
        }

        for (var a : func.bbs) {
            // a--->b
            for (BasicBlock b : a.succ()) {
                BasicBlock x = a;
                while (x == b || !b.domers.contains(x)) {
                    if (!x.domiFrontier.contains(b)) {
                        x.domiFrontier.add(b);
                    }
                    if (x.idomer == null) {
                        Global.logger.warning("no idomer for " + x.label + " of " + func.name);
                        break;
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

    public static void debugDomInfo(Func func) {
        var sb = new StringBuilder();
        for (var bb : func.bbs) {
            sb.append("\nBB " + bb.label + " of " + func.name);
            sb.append("\n\tdomers: ");
            bb.domers.forEach(b -> {
                sb.append(b.label);
                sb.append(" of ");
                sb.append(b.owner.name);
                sb.append(", ");
            });
            sb.append("\n\tidoms: ");

            bb.idoms.forEach(b -> {
                sb.append(b.label);
                sb.append(" of ");
                sb.append(b.owner.name);
                sb.append(", ");
            });
            sb.append("\n\tdomiFrontier: ");
            bb.domiFrontier.forEach(b -> {
                sb.append(b.label);
                sb.append(" of ");
                sb.append(b.owner.name);
                sb.append(", ");
            });
            sb.append("\n\tidomer: ");
            if (bb.idomer == null) {
                sb.append("(null)");
            } else {
                sb.append(bb.idomer.label);
                sb.append(", ");
            }
            sb.append("\n\tdomLevel: ");
            sb.append(bb.domLevel);
        }
        Global.logger.trace("dom info for func " + func.name + sb.toString());
    }
}
