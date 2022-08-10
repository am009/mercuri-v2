package backend.lsra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.text.html.HTML;

import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.Reg;
import backend.arm.VfpReg;
import ds.Global;

public class LiveInterval {

    VirtReg owner;

    // intervals 是 SubRange 按开始端点排序的有序列表
    private List<SubRange> subRanges = new ArrayList<>();
    // 此区间哪些指令是使用了值
    private List<Long> usePositions = new ArrayList<>();

    // 预先绑定的寄存器。例如调用规约导致的绑定。
    private AsmOperand precolorReg; // VfpReg or Reg

    public void setPrecolorReg(AsmOperand reg) {
        assert reg instanceof VfpReg || reg instanceof Reg;
        precolorReg = reg;
    }

    public void trySetPrecolorReg(AsmOperand reg) {
        if (reg instanceof VfpReg || reg instanceof Reg)
            precolorReg = reg;
    }

    public Boolean isFixed() {
        return precolorReg != null;
    }

    public Integer getPrecoloredRegId() {
        if (!isFixed()) {
            return null;
        }
        if (precolorReg instanceof VfpReg) {
            return (((VfpReg) precolorReg).getIndex()) + LsraConsts.VFP_REG_OFFSET;
        } else {
            return ((Reg) precolorReg).getIndex() + 0;
        }
    }

    public void addUsage(long position) {
        // 必须递增
        if (!(usePositions.isEmpty() || position > usePositions.get(usePositions.size() - 1))){
            assert false;
        }
        usePositions.add(position);
    }

    public long nextUsage(long position) {
        for (int i = 0; i < usePositions.size(); i++) {
            if (usePositions.get(i) > position) {
                return usePositions.get(i);
            }
        }
        return -1;
    }

    public LiveInterval(VirtReg owner) {
        this.owner = owner;
        Global.logger.trace("create LiveRange for " + owner.toString());
    }

    public List<SubRange> getSubRanges() {
        return subRanges;
    }

    @Override
    public String toString() {
        var sbuf = new StringBuffer();
        sbuf.append("interval. owner=" + owner.toString() + ", range=");
        for (SubRange subRange : subRanges) {
            sbuf.append("[");
            sbuf.append(subRange.start);
            sbuf.append(", ");
            sbuf.append(subRange.end);
            sbuf.append("] ");
        }
        sbuf.append("allocatedReg=");
        sbuf.append(allocatedReg);
        sbuf.append("\n");
        sbuf.append("precolorReg=");
        sbuf.append(getPrecoloredRegId());
        return sbuf.toString();
    }

    // 此处允许粘结
    public void extend(long start, long end) {
        Global.logger.trace("extend LiveRange for " + owner.toString() + " with args: [" + start + "," + end + "]");
        Global.logger.trace("before " + toString());

        assert (start <= end);
        SubRange newSeg = new SubRange(start, end);
        int i = findInsertPos(start);
        subRanges.add(i, newSeg);
        merge();

        Global.logger.trace("after " + toString());
    }

    // 由于有时候发现定值点，需要截断，但区间尚未建立起来，因此就先放到 todo 列表，最后再处理。
    Set<Long> todoBreaks = new HashSet<>();

    public int allocatedReg = -1;

    public void finishTodoBreaks() {
        if (todoBreaks.isEmpty()) {
            return;
        }
        Global.logger.trace("handle todo breaks for " + owner.toString());
        Global.logger.trace("before " + toString());
        while (!todoBreaks.isEmpty()) {
            var breakPoint = todoBreaks.iterator().next();
            var done = this.disconnect(breakPoint);
            if (done) {
            } else {
                Global.logger.warning("breakpoint " + breakPoint + " is not done");
            }
            // remove 
            todoBreaks.remove(breakPoint);
        }
        Global.logger.trace("after " + toString());
    }

    // 将被定值的变量的range在此处截断;
    public boolean disconnect(long breakIndex) {
        Global.logger.trace("break LiveRange for " + owner.toString() + " with args: " + breakIndex);
        Global.logger.trace("before " + toString());
        // try 只是为了在函数结束时打日志
        try {
            if (subRanges.size() == 0) {
                todoBreaks.add(breakIndex);
                Global.logger.trace("not break because no segs");
                return false;
            }

            var existing = getLiveSubRangeAt(breakIndex);
            if (existing == null) {
                todoBreaks.add(breakIndex);
                Global.logger.trace("not break because no existing");
                return false;
            }
            var existingIdx = subRanges.indexOf(existing);
            Global.logger.trace("found existing: " + existing + " at index: " + existingIdx);
            if (existing.start == breakIndex) {
                // <------existing-------->
                // ^ cut, define here
                // nothing todo
                /**
                [TRACE] break LiveRange for vreg13 with args: 22
                [TRACE] before [0, 15] [22, 24] 
                [TRACE] found existing: [22, 24] at index: 1
                [TRACE] after [0, 15] [22, 24] 
                 */
                return true;

            } else if (existing.end == breakIndex) {
                // <------existing-------->
                //                        ^ def here
                // existing.end = breakIndex - 1;
                existing.end = -1; // delete
            } else if (existing.start > breakIndex) {
                return false;
                //      <------existing-------->
                //  ^ def here
                // var newSeg = new SubRange(breakIndex, breakIndex);
                // var insPos = findInsertPos(breakIndex);
                // subRanges.add(insPos, newSeg);
                // Global.logger.trace("add new seg: " + newSeg + " at index: " + insPos);
                // return true;
            } else {
                // <------existing-------->
                //                 ^ def here (break)
                // becomes
                //                 <def--->
                //                  ^ new seg
                if (!(existing.start < breakIndex && breakIndex < existing.end)) {
                    assert (false);
                }
                var oldEnd = existing.end;
                existing.end = -1; // delete it from subRanges
                var newSeg = new SubRange(breakIndex, oldEnd);
                var insPos = findInsertPos(breakIndex);
                subRanges.add(insPos, newSeg);
                Global.logger.trace("add new seg: " + newSeg + " at index: " + insPos);
                assert (newSeg.start <= newSeg.end);
            }
            // 最后检查一下 existing 是否因为截断而被删除了
            if (existing.start > existing.end) {
                Global.logger.trace("remove existing seg because nolonger valid: " + existing);
                subRanges.remove(existingIdx);

            }
        } catch (Exception e) {
            assert (false);
        } finally {
            Global.logger.trace("after " + toString());
        }
        return true;
    }

    public int findInsertPos(long slot) {
        int l = 0, r = subRanges.size();
        while (l < r) {
            int mid = (r - l) / 2 + l;
            var val = subRanges.get(mid).start;
            if (val == slot) {
                return mid;
            } else if (val < slot) {
                l = mid + 1;
            } else if (val > slot) {
                r = mid;
            }
        }
        return r;
    }

    public SubRange findSegAt(long slot) {
        return subRanges.get(findSegIndexAtSlot(slot));
    }

    public int findSegIndexAtSlot(long slot) {
        int l = 0, r = subRanges.size();
        while (l < r) {
            int mid = (r - l) / 2 + l;
            var start = subRanges.get(mid).start;
            var end = subRanges.get(mid).end;
            if (start <= slot && slot <= end) {
                return mid;
            } else if (start > slot) {
                r = mid;
            } else if (end < slot) {
                l = mid + 1;
            }
        }
        return l;
    }

    public void merge() {
        for (int i = 0; i < subRanges.size() - 1; i++) {
            // assert sorted 
            if (!(subRanges.get(i).start <= subRanges.get(i + 1).start)) {
                Global.logger.error("LiveRange.merge: unsorted subRanges");
                assert (false);
            }
            if (!(subRanges.get(i).start <= subRanges.get(i).end)) {
                Global.logger.error("LiveRange.merge: invalid subRange");
                assert (false);
            }
            if (subRanges.get(i).end >= subRanges.get(i + 1).start) {
                subRanges.get(i).end = Math.max(subRanges.get(i).end, subRanges.get(i + 1).end);
                subRanges.remove(i + 1);
                i--;
                continue;
            }
            // 粘结 已经取消，因为应当放在建立区间时做
            // if (subRanges.get(i).end + 1 == subRanges.get(i + 1).start) {
            //     subRanges.get(i).end = subRanges.get(i + 1).end;
            //     subRanges.remove(i + 1);
            //     i--;
            //     continue;
            // }
        }
    }

    public Boolean isLiveAt(long index) {
        assert (index >= 0);
        int i = findSegIndexAtSlot(index);
        if (i < subRanges.size() && subRanges.get(i).start <= index && subRanges.get(i).end >= index) {
            return true;
        }
        return false;
    }

    public SubRange getLiveSubRangeAt(long index) {
        assert (index >= 0);
        int i = findSegIndexAtSlot(index);
        if (i < subRanges.size()) {
            if (subRanges.get(i).start <= index && index <= subRanges.get(i).end) {
                return subRanges.get(i);
            } else {
                ;
            }
        }
        return null;
    }

    public void extend(long slot) {
        extend(slot, slot);
    }

    public SubRange last() {
        assert (subRanges.size() > 0);
        return subRanges.get(subRanges.size() - 1);
    }

    public SubRange first() {
        assert (subRanges.size() > 0);
        return subRanges.get(0);
    }

    // 一个迭代器状态
    private SubRange current;

    public SubRange next() {
        if (current == null) {
            current = first();
        } else {
            current = subRanges.get(subRanges.indexOf(current) + 1);
        }
        return current;
    }

    public SubRange current() {
        return current;
    }

    public boolean covers(long position) {
        for (var seg : subRanges) {
            if (seg.start <= position && position <= seg.end) {
                return true;
            }
        }
        return false;
    }

    // 对于输入寄存器和临时寄存器，使用 covers 即可
    // 对于输出寄存器，则需要用 coversOutput 判断
    public boolean coversOutput(long position) {
        for (var seg : subRanges) {
            if (seg.start <= position && position < seg.end) {
                return true;
            }
        }
        return false;
    }

    // 此函数相当于寻找 curSeg 和 本 LiveInterval 的各个 Seg 的第一个交点

    // 此函数的逻辑请参考 tryAllocFreeReg 函数注释
    public long nextIntersection(SubRange curSeg) {
        for (var seg : subRanges) {
            if (seg.start <= curSeg.start) {
                // 忽略活跃的 seg
                continue;
            }
            var minstart = Math.min(seg.start, curSeg.start);
            var minend = Math.min(seg.end, curSeg.end);
            // 这里能不能取等号?
            if (minstart >= minend) {
                // 忽略无交集的 seg
                continue;
            }

            /**
             *           |<--------->|       |<--------->| seg
             *        |<----->|                       cur seg
             *           ^ret
             * 
             */
            return minstart;
        }
        return -1;
    }

    public List<LiveInterval> splitChildren = new ArrayList<>();

    /**
     * 返回真，如果 interval 在 holeFrom 到 holeTo 之间有任何的空洞（紧邻不算空洞，至少间隔 1 才算）
     * @param holeFrom
     * @param holeTo
     * @return
     */
    boolean hasHole(int holeFrom, int holeTo) {
        assert (holeFrom <= holeTo) : "should from <= to";
        for (int i = 0; i < subRanges.size() - 1; i++) {
            var seg = subRanges.get(i);
            var next = subRanges.get(i + 1);
            assert (seg.end < next.start)
                    : "should seg.end < next.start, seg: " + seg + ", next: " + next + ", not expecting zero-space";

            if (holeFrom < seg.start) {
                return true;
            }

            if (holeTo <= seg.end) {
                return false;
            }

            if (holeFrom <= seg.end) {
                return true;
            }
        }
        return false;
    }

    /**
     *  |<--------->| current
     *      ^splitPos
     *  |<->|<------>|
     *      ^splitPos
     */
    // 将一个 LiveInterval 从 splitPos 分割成两个 LiveInterval
    // 分割出来的后面的 LiveInterval 的 start 将是 splitPos, 并将被返回
    public LiveInterval splitAt(Long splitPos) {
        assert (splitPos >= 0 && splitPos != Long.MAX_VALUE);
        var newInterval = new LiveInterval(owner);
        newInterval.trySetPrecolorReg(this.precolorReg);
        var foundSegToBeSplited = false;
        for (var seg : subRanges) {
            // 如果已经找到分割点，那么后面 subrange 的全送给新 interval
            if (foundSegToBeSplited) {
                newInterval.subRanges.add(seg);
                subRanges.remove(seg);
                continue;
            }
            /**
             * |<--------->|
             * ^      ^    ^splitPos
             */
            if (seg.start <= splitPos && splitPos <= seg.end) {
                foundSegToBeSplited = true;
                var newSeg = new SubRange(splitPos, seg.end);
                seg.end = splitPos - 1;
                newInterval.subRanges.add(newSeg);
            }

            // after split
            if (seg.end < seg.start) {
                subRanges.remove(seg);
            }
        }
        newInterval.usePositions = splitUsePositions(splitPos);
        splitChildren.add(newInterval);
        return newInterval;
    }

    private List<Long> splitUsePositions(long splitPos) {
        List<Long> ret = new ArrayList<>();
        for (var usePos : usePositions) {
            if (usePos >= splitPos) {
                ret.add(usePos);
                usePositions.remove(usePos);
            }
        }
        return ret;
    }

    public long firstUsage() {
        return usePositions.get(0);
    }

    public long lastUsage() {
        return usePositions.get(usePositions.size() - 1);
    }

    public boolean isAssigned() {
        return getAllocatedRegisterType() != AllocatedRegisterType.NotAllocated;
    }

    public enum AllocatedRegisterType {
        NotAllocated,
        Spilled,
        General,
        Float,
    }

    public AllocatedRegisterType getAllocatedRegisterType() {
        if (allocatedReg == -1) {
            return AllocatedRegisterType.NotAllocated;
        } else if (allocatedReg >= LsraConsts.SPILL_OFFSET
                && allocatedReg < LsraConsts.SPILL_OFFSET + LsraConsts.SPILL_COUNT) {
            return AllocatedRegisterType.Spilled;
        } else if (allocatedReg >= LsraConsts.VFP_REG_OFFSET
                && allocatedReg < LsraConsts.VFP_REG_OFFSET + LsraConsts.VFP_REG_COUNT) {
            return AllocatedRegisterType.Float;
        } else if (allocatedReg >= 0 && allocatedReg < 0 + LsraConsts.CORE_REG_COUNT) {
            return AllocatedRegisterType.General;
        } else {
            throw new RuntimeException("invalid register");
        }
    }

    // 获取 被分割出去的子 LiveInterval 的列表中，哪一个包含了给定编号的指令。
    public LiveInterval childAt(long pos) {
        for (var child : splitChildren) {
            if (child.covers(pos)) {
                return child;
            }
        }
        return null;
    }

    // public long nextIntersection(LiveInterval current) {
    //     var allSegs = new ArrayList<SubRange>();
    //     allSegs.addAll(subRanges);
    //     allSegs.addAll(current.subRanges);
    //     allSegs.sort((SubRange s1, SubRange s2) -> {
    //         return (int) (s1.start - s2.start);
    //     });
    //     for(int i = 0; i < allSegs.size() - 1; i++) {
    //         if(allSegs.get(i).end >= allSegs.get(i + 1).start) {
    //             return allSegs.get(i).end;
    //         }
    //     }
    //     return -1;
    // }

}
