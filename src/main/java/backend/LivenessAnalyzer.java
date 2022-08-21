package backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.inst.ConstrainRegInst;
import backend.lsra.LiveInfo;
import ds.Global;

/**
 * 考虑到分配架构的变化，本类重写自 src/main/java/backend/lsra/LiveIntervalAnalyzer.java
 * 主要改动在于，从基于 module 的分析变成基于 func 的分析。
 */
public class LivenessAnalyzer {

    /* 原始的 liveInfo. 如果要用，可考虑后面的函数 liveInfoOf 可以确保不会得到 null*/
    public Map<AsmBlock, LiveInfo> liveInfo = new HashMap<AsmBlock, LiveInfo>();
    private AsmFunc func;

    public LivenessAnalyzer(AsmFunc func) {
        this.func = func;
    }

    private void logDebugInfo() {
        var debugStr = new StringBuilder();
        debugStr.append("liveInfo of " + func.label + ":");
        for (var blk : func) {
            debugStr.append("\n" + blk.label + ":");
            debugStr.append("\n" + liveInfo.get(blk));
            debugStr.append("\n");
        }
        Global.logger.info(debugStr.toString());
    }

    public void execute() {
        for (var block : func.bbs) {
            analyzeUseDef(block);
        }

        var stable = false;
        var counter = 0;
        // FIXME: 这里的 reversedBlocks 简单采用了 func.bbs 的逆序，尚未证明一定正确。
        // 如果发现顺序有问题，可以改成正规的遍历方式，来创建 reversedBlocks。
        var reversedBlocks = new LinkedList<AsmBlock>();
        for (var block : func.bbs) {
            reversedBlocks.add(0, block);
        }

        while (!stable) {
            stable = true;
            for (var block : reversedBlocks) {
                var blockStable = analyzeInOut(block);
                if (!blockStable) {
                    stable = false;
                }
            }
            // 安全检查，防止死循环。
            counter++;
            if (counter > 100000) {
                Global.logger.warning("LiveIntervalAnalyzer: too many iterations");
            }
        }
        logDebugInfo();
    }

    // 从 Map 获取指定 block 的 LiveInfo。如果没有，则创建一个空的。
    public LiveInfo liveInfoOf(AsmBlock block) {
        if (!liveInfo.containsKey(block)) {
            liveInfo.put(block, new LiveInfo());
        }
        return liveInfo.get(block);
    }

    // 对给定的 BB 进行 liveUse 和 liveOut 分析
    private void analyzeUseDef(AsmBlock block) {
        LiveInfo liveInfo = liveInfoOf(block);
        for (AsmInst inst : block.insts) {
            for (AsmOperand operand : inst.uses) {
                if (operand instanceof VirtReg) {
                    var vr = (VirtReg) operand;
                    if (!liveInfo.liveDef.contains(vr)) {
                        liveInfo.liveUse.add(vr);
                    }
                } else {
                    Global.logger.warning(
                            "operand is not VirtReg, but " + operand.getClass().getSimpleName() + " " + operand);
                }
            }
            for (AsmOperand operand : inst.defs) {
                if (operand instanceof VirtReg) {
                    liveInfo.liveDef.add((VirtReg) operand);
                } else {
                    Global.logger.warning(
                            "operand is not VirtReg, but " + operand.getClass().getSimpleName() + " " + operand);

                }
            }
        }
    }

    // 对给定的 BB 进行 LiveIn/LiveOut 分析。如果 LiveOut 不产生变化，即稳定，返回 true
    private boolean analyzeInOut(AsmBlock block) {
        var blive = liveInfoOf(block);
        var liveOutBefore = new HashSet<VirtReg>(blive.liveOut);
        for (var succbb : block.succ) {
            blive.liveOut.addAll(liveInfoOf(succbb).liveIn);
        }
        // b.live_gen 是否包括其自己定义的？
        // 答案是不包括，参见 JavaHotSpot LSRA 62 页
        // b.live_in = (b.live_out – b.live_kill) ∪ b.live_gen
        blive.liveIn.clear();
        blive.liveIn.addAll(blive.liveOut);
        blive.liveIn.removeAll(blive.liveDef);
        blive.liveIn.addAll(blive.liveUse);
        var stable = liveOutBefore.equals(blive.liveOut);
        Global.logger.trace("block " + block.label + " liveIn: " + blive.liveIn + " liveOut: " + blive.liveOut
                + " stable: " + stable);
        return stable;
    }

}
