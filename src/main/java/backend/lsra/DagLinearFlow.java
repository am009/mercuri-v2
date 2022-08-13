/**
 * 此模块基于 LiveIntervalAnalyzer 生成的 liveInfo 构建最终的 LiveInterval, 以便给后续的 Walker 使用
 */
package backend.lsra;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.inst.ConstrainRegInst;
import ds.Global;

class DagLinearFlow {
    // injected by user
    AsmModule m;
    // injected by user
    Map<AsmBlock, LiveInfo> liveInfo;

    public Map<VirtReg, LiveInterval> liveIntervals = new HashMap<>();
    Map<AsmInst, Integer> instSlotIdx = new HashMap<>();

    public List<SubRange> getSortedSubRanges() {
        List<SubRange> subRanges = new LinkedList<>();
        for (var kv : liveIntervals.entrySet()) {
            subRanges.addAll(kv.getValue().getSubRanges());
        }
        // sort all subRanges by start
        subRanges.sort((x, y) -> (int) (x.start - y.start));
        return subRanges;
    }

    public List<LiveInterval> getSortedRanges() {
        List<LiveInterval> ranges = new LinkedList<>();
        for (var kv : liveIntervals.entrySet()) {
            ranges.add(kv.getValue());
        }
        // sort all ranges by start
        ranges.sort((x, y) -> (int) (x.first().start - y.first().start));
        return ranges;
    }

    public LiveInterval intervalOf(VirtReg vreg) {
        if (!liveIntervals.containsKey(vreg)) {
            var lr = new LiveInterval(vreg);
            lr.owner = vreg;
            lr.trySetPrecolorReg(vreg);

            liveIntervals.put(vreg, lr);
            return lr;
        }
        return liveIntervals.get(vreg);
    }

    public DagLinearFlow(AsmModule m, Map<AsmBlock, LiveInfo> liveInfo) {
        this.m = m;
        this.liveInfo = liveInfo;
    }

    public static DagLinearFlow build(AsmModule m, Map<AsmBlock, LiveInfo> liveInfo) {
        var linearFlow = new DagLinearFlow(m, liveInfo);
        linearFlow._build();
        return linearFlow;
    }
    // 计算Live Intervals

    private void _build() {

        // 对指令编号
        int linearInstSlotIndex = -1;

        // 逆序的线性 BB 表
        var reversedBlocks = new LinkedList<AsmBlock>();

        for (var func : m.funcs) {
            for (var block : func.bbs) {
                reversedBlocks.add(0, block);
            }
        }
        for(var rbb : reversedBlocks) {
            for (var inst : rbb.insts) {
                linearInstSlotIndex++;
                instSlotIdx.put(inst, linearInstSlotIndex);
            }
        }
        for (var block : reversedBlocks) {
            Global.logger.trace("build liveInterval for block " + block.label);
            int blockFrom = instSlotIdx.get(block.insts.get(0));
            int blockTo = instSlotIdx.get(block.insts.get(block.insts.size() - 1)) + 1; // + 1是为了连续到下一个块
            var blockLiveInfo = liveInfo.get(block);
            for (var liveOutVar : blockLiveInfo.liveOut) {
                Global.logger.trace("ref liveOut, init liveRange for " + liveOutVar);
                intervalOf(liveOutVar).extend(blockFrom, blockTo);
            }
            // int blockEndSlot = linearInstSlotIndex;
            for (var inst : block.insts) {
                int instIdx = instSlotIdx.get(inst);
                // Map<VirtReg, AsmOperand> constraints = null;
                Map<AsmOperand, VirtReg> inConstraints = null;
                Map<AsmOperand, VirtReg> outConstraints = null;
                if (inst instanceof ConstrainRegInst) {
                    inConstraints = ((ConstrainRegInst)inst).getInConstraints();
                    outConstraints = ((ConstrainRegInst)inst).getInConstraints();
                }
                for (var def : inst.defs) {
                    if (def instanceof VirtReg) {
                        // intervalOf((VirtReg) def).disconnect(instIdx);
                        // 发现一个问题，在上一轮循环中截断的，结果在下一轮循环又被连接上了。
                        // 这肯定不对，所以索性都加入到 todo,最后单独开个循环做截断
                        intervalOf((VirtReg) def).todoBreaks.add(Long.valueOf(instIdx));

                        if (outConstraints != null && outConstraints.containsValue(def)) {
                            // TODO
                            // intervalOf((VirtReg) def).trySetPrecolorReg();
                        }
                    } else {
                        Global.logger.warning("DagLinearFlow: def is not a VirtReg");
                        assert (false);
                    }
                }
                for (var use : inst.uses) {
                    if (use instanceof VirtReg) {
                        intervalOf((VirtReg) use).extend(blockFrom, instIdx);
                        intervalOf((VirtReg) use).addUsage(instIdx);

                        if (inConstraints != null && inConstraints.containsValue(use)) {
                            // TODO
                            // intervalOf((VirtReg) use).trySetPrecolorReg();
                        }
                    } else {
                        Global.logger.warning(
                                "DagLinearFlow: use is not a VirtReg. It is a " + use.getClass().getSimpleName()
                                        + " with value: " + use.toString());
                    }
                }
            }
        }
        for (var block : reversedBlocks) {
            var blockLiveInfo = liveInfo.get(block);
            for (var def : blockLiveInfo.liveDef) {
                intervalOf(def).finishTodoBreaks();
            }
        }
    }

}