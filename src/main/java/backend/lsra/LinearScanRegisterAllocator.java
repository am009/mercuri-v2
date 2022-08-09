package backend.lsra;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import backend.AsmModule;
import backend.VirtReg;
import backend.arm.Reg;

public class LinearScanRegisterAllocator {
    DagLinearFlow flow;
    AsmModule m;

    private int nextSpillSlot = LsraConsts.SPILL_OFFSET;

    private int getNextSpillSlot() {
        return nextSpillSlot++;
    }

    public LinearScanRegisterAllocator(AsmModule m, DagLinearFlow flow) {
        this.m = m;
        this.flow = flow;

        initUsePosAndBlockPos();
        initFreePos();
    }

    public static AsmModule process(AsmModule m) {
        // 构建 interval
        var flow = LiveIntervalAnalyzer.process(m);
        var g = new LinearScanRegisterAllocator(m, flow);
        g.init();
        g.walkAndAlloc();
        return m;
    }

    // 初始化 LSRA 所需的区间信息，得到排序后的所有 interval
    private void init() {
        unhandled = flow.getSortedRanges();
    }

    // 线性扫描的上下文

    // 尚未处理的区间. 各个 vreg 的活跃区间, 按 subRange 的 start 排序
    List<LiveInterval> unhandled;
    // 与扫描光标有交集的区间
    List<LiveInterval> active = new LinkedList<LiveInterval>();
    // 不活跃的区间, 即那些虽然开始结束范围涵盖当前 pos, 但实际上当前 pos 处于其中的空洞
    List<LiveInterval> inactive = new LinkedList<LiveInterval>();
    // 处理完毕的区间, 即 end 已经被 当前 pos 超越
    List<LiveInterval> handled = new LinkedList<LiveInterval>();

    // 线性扫描
    // 参考 Java Hotspot 论文 p67
    private void walkAndAlloc() {

        while (!unhandled.isEmpty()) {
            // 获取一个最靠前的未处理区间
            var current = unhandled.iterator().next();
            unhandled.remove(current);

            // 扫描游标
            var position = current.first().start;

            // 判断 active 中的区间是否过期或者不活跃
            for (var it : active) {
                if (it.last().end < position) {
                    active.remove(it);
                    handled.add(it);
                } else if (!it.covers(position)) {
                    active.remove(it);
                    inactive.add(it);
                }
            }
            // 判断 inactive 中的区间是否过期或者活跃
            for (var it : inactive) {
                if (it.last().end < position) {
                    inactive.remove(it);
                    handled.add(it);
                } else if (it.covers(position)) {
                    inactive.remove(it);
                    active.add(it);
                }
            }

            // 寻找一个空闲的寄存器, 分配给 current 区间
            var result = tryAllocFreeReg(position, current);
            if (null == result) {
                // 如果没有空闲的，就试图抢一个，抢不到就 spill
                allocBlockedReg(current);
            }
            if (current.isAssigned()) {
                active.add(current);
            }
        }
    }

    /**
     * 我们会选择 use_pos 最高的寄存器作为 current 区间的最佳候选
     * 
     * 三种情况:
     * 1. 如果 current 的所分配的寄存器的第一个使用位置在最高 use_pos 之后,
     *  则更倾向于 spill current
     * 2. 否则, current 获取获得选择的寄存器. 所有此寄存器的(活跃和非活跃区间)
     * 与 current 的交集都要进行分割, 分割点在 start of current 前, 且 spill 到栈
     * 3. 第三种情况是第二种的扩展. 如果被选择的寄存器有一个 block_pos, 且 block_pos 位于 current 的中间, 那么此寄存器在整个 lifetime 不可用
     * 那么 current 将在 block_pos 前分割, 分割出的 split child 将放入 unhandled
     * 
     * 这个算法保证了 current 要么得到一个指派的寄存器, 要么他自己被 spill
     * 
     * 很多 new split children 会被创建并有序加入到 unhandled list
     * 但所有 split children 其 start position 一定在 current 的 start 之后
     * 从而保证分配器是只前进的, 不会回退或者无限循环
     */

    // 存储已经分配了寄存器 reg 的区间的下一个使用位置
    // key: reg, value: next use position
    Map<Integer, Long> usePos = new HashMap<Integer, Long>();
    // 存储一个寄存器的硬限制位置, 即从此该寄存器不能被 spill 掉
    // key: reg, value: no spill position
    Map<Integer, Long> blockPos = new HashMap<Integer, Long>();

    private void initUsePosAndBlockPos() {
        for (int i = 0; i < LsraConsts.CORE_REG_COUNT; i++) {
            usePos.put(i, Long.MAX_VALUE);
            blockPos.put(i, Long.MAX_VALUE);
        }
        for (int i = 0; i < LsraConsts.VFP_REG_COUNT; i++) {
            usePos.put(i + LsraConsts.VFP_REG_OFFSET, Long.MAX_VALUE);
            blockPos.put(i + LsraConsts.VFP_REG_OFFSET, Long.MAX_VALUE);
        }
    }

    private void setUsePos(LiveInterval it, long pos) {
        usePos.put(it.allocatedReg, pos);
    }

    Map<LiveInterval, Boolean> fixed = new HashMap<LiveInterval, Boolean>();

    // 为当前的区间抢一个寄存器
    private void allocBlockedReg(LiveInterval current) {

        // 初始化各区间的下一次使用位置
        for (var it : active) {
            if (fixed.get(it))
                continue;

            setUsePos(it, it.nextUsage(current.first().start));
        }
        for (var it : inactive) {
            if (fixed.get(it))
                continue;

            if (it.nextIntersection(current.first()) != -1) {
                setUsePos(it, it.nextUsage(current.first().start));
            }
        }

        for (var it : active) {
            if (!fixed.get(it))
                continue;

            setUsePos(it, 0);
        }
        for (var it : inactive) {
            if (!fixed.get(it))
                continue;

            var nextInter = it.nextIntersection(current.first());
            if (nextInter != -1) {
                setUsePos(it, nextInter);
            }
        }

        var reg = getRegisterWithHighestUsePos();
        // 如果被选出的最远寄存器的下一次使用位置在“current 第一次使用位置”之前，这意味着无法抢占，则需要 spill
        if (usePos.get(reg) < current.firstUsage()) {
            // 分配  spill slot
            current.allocatedReg = getNextSpillSlot();
            // 获取一个在“current 第一次使用位置”之前 的最优位置
            var bestPos = getOptimalPosBefore(current.firstUsage());
            /**
             * |<---------------------------->|
             *          ^ first usage
             *         ^ best pos
             */
            // 然后在此处分割。这意味着在此之后的区间可以被重新分配到其它寄存器
            // 只有在分割点之前的部分会 spill
            current.splitAt(bestPos);
        }
        // 如果最远寄存器的硬限制位置大于 current 区间的结尾，
        // 那么这意味着此寄存器对于 current 是可借用的，因此我们借用直到 current 最后一次使用
        else if (blockPos.get(reg) > current.last().end) {
            // spill made a register free for whole current interval
            current.allocatedReg = reg;
            var bestPos = getOptimalPosBefore(current.lastUsage());
            current.splitAt(bestPos);
        }

    }

    // optimal position before first use position that requires a register
    // 获取一个在“current 第一次使用位置”之前 的最优位置
    private long getOptimalPosBefore(long firstUsage) {
        long bestPos = Long.MIN_VALUE;
        for (var pos : usePos.keySet()) {
            if (usePos.get(pos) < firstUsage) {
                bestPos = Math.max(bestPos, pos);
            }
        }
        return bestPos;
    }

    // 获取一个使用距离最远的寄存器
    private int getRegisterWithHighestUsePos() {
        var max = Long.MIN_VALUE;
        var reg = -1;
        for (var it : usePos.keySet()) {
            if (usePos.get(it) > max) {
                max = usePos.get(it);
                reg = it;
            }
        }
        return reg;
    }

    // freee util
    // 一个寄存器是空闲的, 直到它的 freePos
    // 也就是说, 只要当前 pos 小于 freePos, 那么这个寄存器就可以被分配
    // key: reg idx, value: freePos
    Map<Integer, Long> freePos = new HashMap<Integer, Long>();

    void initFreePos() {
        for (int i = 0; i < LsraConsts.CORE_REG_COUNT; i++) {
            freePos.put(i, Long.MAX_VALUE);
        }
        for (int i = 0; i < LsraConsts.VFP_REG_COUNT; i++) {
            freePos.put(i + LsraConsts.VFP_REG_OFFSET, Long.MAX_VALUE);
        }
    }

    /**
     * 规则类怪谈:
     * 1. 所有 active 区间使用的 reg 必须是排他的, 因此会将 freePos 设置为 0
     * 2. 所有与 current 区间无交集的非活跃区间都将被忽略
     * 3. 非活跃区间使用的寄存器的 freePos 设置为下一个交点. 也即: 该寄存器一开始是可用的, 直到交点. 因此对 curent 而言只是部分可用的
     * 4. setFreePos 用于设置被分配给某区间的某寄存器的 freePos
     */

    Map<LiveInterval, List<Integer>> Inteval2Register = new HashMap<LiveInterval, List<Integer>>();

    void setFreePos(LiveInterval interval, long FreePos) {
        var regs = Inteval2Register.get(interval);
        assert (regs != null);
        for (var reg : regs) {
            freePos.put(reg, FreePos);
        }
    }

    // 尝试寻找一个空闲的寄存器，这样不用 spill 操作
    Boolean tryAllocFreeReg(long position, LiveInterval current) {
        Boolean success = false;
        // 将活跃区间占用的寄存器标记为已占用
        for (var another : active) {
            setFreePos(another, 0);
        }
        // 看看哪些非活跃区间和当前区间有交集
        /**
         * 下面这种情况, 属于没交集
         * |<----->|       |<-------->| another
         *          |<->|               current
         * 
         * 
         * 下面这种情况
         *      |<---------------->| another
         *  |<----------------->|    current
         *      ^ inter_sect_pos
         * 直到 inter_sect_pos, another 所占用的寄存器都可以借给 current
         * 
         * 下面这种情况
         * |<----->|       |<-------->| another
         *              |<----->|       current
         *                 ^ inter_sect_pos
         * 相交发生在 another 的非首段, 而寄存器已经分给了 another
         * 但是中间空洞部分, 是可以被 current 借用的.
         * 我们可以清楚地看到从 current.start 直到 inter_sect_pos(不含) 都可以被 current 借用
         * 
         */
        for (var another : inactive) {
            // 寻找下一个交点, 如果找到, 则将此区间的寄存器可用性标记为直到交点
            var nextIntersection = another.nextIntersection(current.findSegAt(position));
            if (nextIntersection != -1) {
                setFreePos(another, nextIntersection);
            }
        }
        var reg = getRegisterWithHighestFreePos();
        var highFreePos = freePos.get(reg);
        if (highFreePos == 0) {
            success = false;
            return success;
        }
        if (highFreePos > current.last().end) {
            // 这说明寄存器对 current 的整个大区间可用
            current.allocatedReg = reg;
            success = true;
            return success;
        }
        // 否则, 说明寄存器只对 current 的 第一个 subRange 可用
        /** If free_pos lies somewhere in the middle of current, the register is available for the 
         * first part of the interval only. The register is assigned to current, but current is split 
         * at free_pos (or even before). The split child is sorted into the unhandled list and will 
         * be processed later. A move operation between the two intervals is inserted at the 
         * split position.
         */
        var splitChild = current.splitAt(highFreePos);
        addToUnhandled(splitChild);
        current.allocatedReg = reg;
        success = true;
        return success;
    }

    private void addToUnhandled(LiveInterval splitChild) {
        assert (splitChild.allocatedReg == -1);
        var start = splitChild.first().start;
        for (int i = 0; i < unhandled.size(); i++) {
            if (unhandled.get(i).first().start > start) {
                unhandled.add(i, splitChild);
                return;
            }
        }
    }

    private int getRegisterWithHighestFreePos() {
        int result = 0;
        long highpos = freePos.get(0);
        for (var entry : freePos.entrySet()) {
            if (entry.getValue() > highpos) {
                highpos = entry.getValue();
                result = entry.getKey();
            }
        }
        return result;
    }

    /**
     * LSRA 方法将 CFG 简化为基本块的线性表。活跃区间保存了虚拟寄存器存放有效值 的时常。
     * 这些信息在分割之前都是有效的。
     * 
     * 然而，一旦分割一次，就会插入一些 move 操作，这样数据流在基本块中是正确的，但是线性块表对控制流的建模是不荃湾的。
     * 因此，需要一个额外的 resolving 步骤。
     * 
     * 
     * Java Hotspot p73
     * 
     * 个人理解：
     * 比如说，我们在分割点造成了移动。那么如果跳转到的点恰好从移动到的栈上拿回了数据，那么这还好。但如果跳转到的点还在认之前的寄存器，那就不对了。
     */

     /**
      * 对于每个参作数
      */
    private void resolveDataFlow() {
        var resolver = new MoveResolver();
        for (var func : m.funcs) {
            for (var fromBlock : func.bbs) {
                // 收集所有必要的 resolving moves
                for (var toBlock : fromBlock.succ) {
                    flow.liveInfo.get(toBlock).liveIn.forEach(opr -> {
                        var vreg = (VirtReg) opr;
                        var parentInterval = flow.intervalOf(vreg);
                        var lastFromBlockInst = fromBlock.insts.get(fromBlock.insts.size() - 1);
                        // fromInterval 是指 from 最末的 vreg 所在的区间
                        var fromInterval = parentInterval.childAt(flow.instSlotIdx.get(lastFromBlockInst));
                        assert (fromInterval != null);
                        var firstToBlockInst = toBlock.insts.get(0);
                        // toInterval 是指 to 的第一个指令所在的区间
                        var toInterval = parentInterval.childAt(flow.instSlotIdx.get(firstToBlockInst));
                        assert (toInterval != null);

                        if (fromInterval != toInterval) {
                            resolver.addMapping(fromInterval, toInterval);
                        }
                    });

                    resolver.findInsertPos(fromBlock, toBlock);
                    resolver.resolveMappings();
                }

            }
        }
    }
}
