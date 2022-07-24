package backend.arm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.misc.OrderedHashSet;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.StackOperand;
import backend.VirtReg;
import backend.arm.inst.ConstrainRegInst;
import backend.arm.inst.LoadInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.Prologue;
import backend.arm.inst.StoreInst;
import ds.Global;

// 简单的寄存器分配算法，尽量保证正确性即可。之后应当实现图着色来替换
// 由于未优化的IR可能没有global（跨基本块使用）的值，所以相关的正确性有待进一步测试。
// Bottom-Up Local Register Allocation
// TODO 维护函数的 usedCalleeSavedReg
public class LocalRegAllocator {
    public AsmFunc func;
    public Set<VirtReg> globs = new HashSet<>(); // 跨基本块的全局值
    public Map<VirtReg, StackOperand> globSpill = new HashMap<>();
    public Map<AsmBlock, BlockData> blockData = new HashMap<>();
    public LocalRegAllocator(AsmFunc f) {
        this.func = f;
    }

    int vregInd = -1;
    VirtReg getNewVreg(String comment) {
        var ret = new VirtReg(vregInd);
        ret.comment = comment;
        vregInd -= 1;
        return ret;
    }

    public class BlockData {
        // public Set<VirtReg> live = new HashSet<>();
        public Map<VirtReg, List<Integer>> useLists = new HashMap<>();// 里面的值应该是降序的。
        public Map<VirtReg, AsmOperand> allocHint = new HashMap<>();
    }

    public class RegClass {
        int count;
        VirtReg[] current;
        int[] next;
        // free优先使用index靠前的
        OrderedHashSet<Integer> free = new OrderedHashSet<>();
        public RegClass(int count) {
            this.count = count;
            current = new VirtReg[count];
            next = new int[count];
            for (int i=0;i<count;i++) {
                free.add(i);
                next[i] = Integer.MAX_VALUE;
            }
        }
    }

    public class BlockAllocator {
        public RegClass[] rcs;
        public Map<VirtReg, StackOperand> localSpill = new HashMap<>();

        public BlockAllocator() {
            rcs = new RegClass[2];
            // 11 normal reg
            rcs[0] = new RegClass(11);
            // 32 vfp reg
            rcs[1] = new RegClass(32);
        }

        public int checkInPhyReg(VirtReg vr) {
            RegClass rc = rcs[b2i(vr.isFloat)];
            for (int i=0;i<rc.count;i++) {
                if (vr.equals(rc.current[i])) {
                    return i;
                }
            }
            return -1;
        }

        public int allocateReg(VirtReg vreg, int hint, AsmBlock blk, List<AsmInst> toInsertBefore) {
            int ret = -1;
            RegClass rc = rcs[b2i(vreg.isFloat)];
            if (rc.free.size() > 0) {
                if (hint != -1 && rc.free.contains(hint)) {
                    ret = hint;
                } else {
                    ret = rc.free.get(0);
                }
            } else {
                ret = findMaxInd(rc.next);
                spillInd(ret, vreg.isFloat, blk, toInsertBefore);
            }
            assert ret != -1;
            rc.current[ret] = vreg;
            rc.next[ret] = -1;
            rc.free.remove(ret);
            return ret;
        }

        // 仅spill动作，不会释放对应寄存器
        private void spillInd(int ind, boolean isFloat, AsmBlock blk, List<AsmInst> toInsertBefore) {
            RegClass rc = rcs[b2i(isFloat)];
            var toSpillVreg = rc.current[ind];
            StackOperand spilledLoc = allocateOrGetSpill(toSpillVreg);
            var store = new StoreInst(blk, ind2Reg(ind, isFloat), spilledLoc);
            store.comment = "Spill "+toSpillVreg.comment;
            toInsertBefore.addAll(Generator.expandStackOperandLoadStoreIP(store));
        }

        private StackOperand allocateOrGetSpill(VirtReg vreg) {
            StackOperand spilledLoc;
            if (globs.contains(vreg)) {
                spilledLoc = globSpill.get(vreg);
                if (spilledLoc == null) {
                    spilledLoc = new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4));
                    spilledLoc.comment = vreg.comment;
                    globSpill.put(vreg, spilledLoc);
                }
            } else {
                spilledLoc = localSpill.get(vreg);
                if (spilledLoc == null) {
                    spilledLoc = new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4));
                    spilledLoc.comment = vreg.comment;
                    localSpill.put(vreg, spilledLoc);
                }
            }
            return spilledLoc;
        }

        public StackOperand getSpill(VirtReg vreg) {
            StackOperand spilledLoc;
            if (globs.contains(vreg)) {
                spilledLoc = globSpill.get(vreg);
            } else {
                spilledLoc = localSpill.get(vreg);
            }
            return spilledLoc;
        }

        // 将from移动到to。要检查to是否是free的
        public void moveTo(int from, int to, boolean isFloat, AsmBlock blk, List<AsmInst> toInsertBefore) {
            assert from >= 0 && to >= 0;
            RegClass rc = rcs[b2i(isFloat)];
            if (!rc.free.contains(to)) {
                spillInd(to, isFloat, blk, toInsertBefore);
            }
            // generate mov inst;
            AsmOperand regTo = ind2Reg(to, isFloat);
            AsmOperand regFrom = ind2Reg(from, isFloat);
            var mov = new MovInst(blk, MovInst.Ty.REG, regTo, regFrom);
            mov.comment = "LocalRegAllocator.moveTo";
            toInsertBefore.add(mov);

            rc.free.remove(to);
            rc.current[to] = rc.current[from];
            rc.next[to] = rc.next[from];
            free(from, isFloat);
        }

        public void allocateTo(VirtReg vreg, int ind) {
        }

        public void free(int ind, boolean isFloat) {
            RegClass rc = rcs[b2i(isFloat)];
            rc.free.add(ind);
            rc.current[ind] = null;
            rc.next[ind] = Integer.MAX_VALUE;
        }

        public void setNext(int ind, boolean isFloat, int next) {
            RegClass rc = rcs[b2i(isFloat)];
            assert rc.next[ind] == -1;
            rc.next[ind] = next;
        }

        // 所有寄存器都没有被占用，如果有则一定是global的值，spill到内存里。
        public void onBlockEnd(AsmBlock blk, List<AsmInst> toInsertBefore) {
            for (RegClass rc: rcs) {
                boolean isFloat = rc.count == 32;
                for(int i=0;i<rc.count;i++) {
                    if (rc.current[i] != null) {
                        var vreg = rc.current[i];
                        assert globs.contains(vreg);
                        spillInd(i, isFloat, blk, toInsertBefore);
                    }
                }
            }
        }
    }

    public static AsmModule process(AsmModule m) {
        for (var f: m.funcs) {
            var g = new LocalRegAllocator(f);
            g.doAnalysis();
        }
        return m;
    }

    // 把每个基本块里，每个虚拟寄存器的使用处index记录下来组合成一个list，便于实现Dist函数
    // 需要每个寄存器的最后一个使用点，用于判断某寄存器是否可以被Free掉。
    // global值的发现：如果遍历到开头时发现某变量被使用而没有被定义，则说明是global的。
    // 保存每个基本块使用过的global值。以便于在后面用到的时候从内存中取出
    private void preAnalysis() {
        for (var blk: func.bbs) {
            BlockData ba = new BlockData();
            blockData.put(blk, ba);
            Set<VirtReg> live = new HashSet<>();
            Map<VirtReg, List<Integer>> useList = ba.useLists;
            Set<VirtReg> dead = new HashSet<>(); // 确保每个VirtReg仅有一个live range。
            for (int j = blk.insts.size() - 1; j >= 0; j--) {
                var inst = blk.insts.get(j);
                for (var u: inst.uses) {
                    if (u instanceof VirtReg) {
                        live.add((VirtReg)u);
                        if (useList.containsKey(u)) {
                            useList.get(u).add(j);
                        } else {
                            useList.computeIfAbsent(((VirtReg)u), k -> new ArrayList<>()).add(j);
                        }
                        if (dead.contains(u)) {throw new RuntimeException("Multiple live ranges use same VirtReg!");}
                    }
                }
                for (var def: inst.defs) {
                    if (def instanceof VirtReg) {
                        live.remove((VirtReg)def);
                        dead.add((VirtReg)def);
                    }
                }
                if (inst instanceof ConstrainRegInst) {
                    // 替换式加入，因此仅保留最靠前的约束
                    ba.allocHint.putAll(((ConstrainRegInst)inst).getConstraints());
                }
            }
            globs.addAll(live);
        }
    }

    // 对每个指令 op vri1 vri2 -> vri3
    // 首先分配vri1和vri2，即use的值。
    private void doAnalysis() {
        preAnalysis();
        for (var blk: func.bbs) {
            var bd = blockData.get(blk);
            var state = new BlockAllocator();
            // // 把所有需要添加的指令都延迟到分配后添加。里面的值分别表示要在index处加入这个list的指令。
            // List<Map.Entry<Integer,List<AsmInst>>> addSeqs = new ArrayList<>();
            int addedInstCount = 0;
            for (int i=0;i<blk.insts.size();i++) {
                var inst = blk.insts.get(i);
                List<VirtReg> uses = filterVirtReg(inst.uses);
                List<AsmOperand> newUses = new ArrayList<>();
                List<AsmInst> toInsertBefore = new ArrayList<>();
                Map<VirtReg, AsmOperand> constraints = null;
                if (inst instanceof ConstrainRegInst) {
                    constraints = ((ConstrainRegInst)inst).getConstraints();
                }
                
                // for (var vreg: uses) {
                for (int j=0;j<uses.size();j++) {
                    var vreg = uses.get(j);
                    // 首先检查有没有约束
                    // 没有约束的就直接看是不是寄存器，不是的话有两种情况，要么spill了，要么是global的spill，就分配一个寄存器并且把值加载出来。
                    // 有约束的就得首先获得约束，然后检查是否在目标寄存器里，不在的就还是要强行分配寄存器
                    // 如果已经在寄存器里，强行分配寄存器就先spill原有的值，然后mov过去？
                    if (constraints == null || !constraints.containsKey(vreg)) { // 没有约束，正常分配
                        var hint = bd.allocHint.get(vreg);
                        AsmOperand phyReg = ind2Reg(state.checkInPhyReg(vreg), vreg.isFloat);
                        if (phyReg != null) {
                            newUses.add(phyReg);
                            continue;
                        }
                        // 没有约束，且没有分配寄存器，先随便分配寄存器，然后找到值，加载进来。
                        int regind = state.allocateReg(vreg, reg2ind(hint, vreg.isFloat), blk, toInsertBefore);
                        StackOperand spilledLoc = state.getSpill(vreg);
                        assert spilledLoc != null;
                        var load = new LoadInst(blk, phyReg, spilledLoc);
                        load.comment = "load spilled "+vreg.comment;
                        toInsertBefore.addAll(Generator.expandStackOperandLoadStoreIP(load));
                        newUses.add(ind2Reg(regind, vreg.isFloat));
                    } else {
                        var phyReg = constraints.get(vreg);
                        int currentind = state.checkInPhyReg(vreg);
                        boolean inReg = currentind != -1;
                        int ind = reg2ind(phyReg, vreg.isFloat);
                        assert ind >= 0;
                        phyReg.comment = vreg.comment;
                        newUses.add(phyReg);
                        if (currentind == ind) {
                            continue;
                        }
                        // 必须要强行分配到ind寄存器中
                        if (inReg) {
                            state.moveTo(currentind, ind, vreg.isFloat, blk, toInsertBefore);
                            continue;
                        }
                        state.allocateTo(vreg, ind);
                        StackOperand spilledLoc = state.getSpill(vreg);
                        var load = new LoadInst(blk, phyReg, spilledLoc);
                        load.comment = "load spilled "+vreg.comment;
                        toInsertBefore.addAll(Generator.expandStackOperandLoadStoreIP(load));
                    }
                }
                assert uses.size() == newUses.size();
                // 如果use不需要了当前的值，则free寄存器。
                // 缓存结果
                ArrayList<Boolean> notNeeded = new ArrayList<>();
                for(var vreg: uses) {
                    int lastUse = bd.useLists.get(vreg).get(0); // global value may have size 0.
                    boolean notNeed = (i+addedInstCount) >= lastUse; // 是最后一个用该VirtReg的指令
                    notNeeded.add(notNeed);
                    if (notNeed) { // TODO check notNeed 是否判断正确
                        int currentInd = state.checkInPhyReg(vreg);
                        assert currentInd != -1;
                        if (globs.contains(vreg)) {
                            state.spillInd(currentInd, vreg.isFloat, blk, toInsertBefore);
                        }
                        state.free(currentInd, vreg.isFloat);
                    }
                }
                // 给Def分配寄存器
                List<VirtReg> defs = filterVirtReg(inst.defs);
                List<AsmOperand> newDefs = new ArrayList<>();
                assert inst instanceof Prologue || defs.size() <= 1; // 应该是吧
                for (var vreg: defs) {
                    if (constraints == null || !constraints.containsKey(vreg)) {
                        var hint = bd.allocHint.get(vreg);
                        int regind = state.allocateReg(vreg, reg2ind(hint, vreg.isFloat), blk, toInsertBefore);
                        var phyReg = ind2Reg(regind, vreg.isFloat);
                        phyReg.comment = vreg.comment;
                        newDefs.add(phyReg);
                    } else {
                        var phyReg = constraints.get(vreg);
                        phyReg.comment = vreg.comment;
                        state.allocateTo(vreg, reg2ind(phyReg, vreg.isFloat));
                        newDefs.add(phyReg);
                    }
                }
                assert newDefs.size() == defs.size();
                // rewrite i as opi rx , ry -> rz
                // newUses 替换 use 需要小心，仅替换VirtReg
                var newUsesTmp = new ArrayList<>(newUses);
                for (int j=0;j<inst.uses.size();j++) {
                    if (inst.uses.get(j) instanceof VirtReg) {
                        inst.uses.set(j, newUsesTmp.remove(0));
                    }
                }
                var newDefsTmp = new ArrayList<>(newDefs);
                for (int j=0;j<inst.defs.size();j++) {
                    if (inst.defs.get(j) instanceof VirtReg) {
                        inst.defs.set(j, newDefsTmp.remove(0));
                    }
                }
                // 设置相关的Next值。
                for (int j=0;j<uses.size();j++) {
                    if (!notNeeded.get(j)) { // 仍然需要寄存器
                        var vreg = uses.get(j);
                        var phyReg = newUses.get(j);
                        boolean isFloat = vreg.isFloat;
                        int ind = reg2ind(phyReg, isFloat);
                        int next = calcDist((i - addedInstCount), bd.useLists.get(vreg));
                        state.setNext(ind, isFloat, next);
                    }
                }
                // 设置def的next值
                for(int j=0;j<defs.size();j++) {
                    var vreg = defs.get(j);
                    var phyReg = newDefs.get(j);
                    boolean isFloat = vreg.isFloat;
                    int ind = reg2ind(phyReg, isFloat);
                    int next = calcDist((i - addedInstCount), bd.useLists.get(vreg));
                    state.setNext(ind, isFloat, next);
                }
                // 插入需要增加的指令
                blk.insts.addAll(i, toInsertBefore);
                i += toInsertBefore.size();
                addedInstCount += toInsertBefore.size();
            }
            // // ~~插入所有指令~~

            // 结束的时候将还在寄存器里的，用到的global值放到栈上
            List<AsmInst> toInsert = new ArrayList<>();
            state.onBlockEnd(blk, toInsert);
            blk.insts.addAll(toInsert);
        }
    }

    private int calcDist(int currentInd, List<Integer> useList) {
        for (int j = useList.size() - 1; j >= 0; j--) {
            int ind = useList.get(j);
            if (ind > currentInd) {
                return ind-currentInd;
            }
        }
        Global.logger.warning("Global value?"); // TODO 
        return Integer.MAX_VALUE;
    }

    private List<VirtReg> filterVirtReg(List<AsmOperand> uses) {
        return uses.stream().filter(op -> op instanceof VirtReg).map(vr -> (VirtReg)vr).collect(Collectors.toList());
    }

    private static AsmOperand ind2Reg(int index, boolean isFloat) {
        if (index < 0) {
            return null;
        }
        if (isFloat) {
            return new VfpReg(index);
        } else {
            return new Reg(Reg.Type.values[index]);
        }
    }

    private static int reg2ind(AsmOperand reg, boolean isFloat) {
        if (reg == null) {
            return -1;
        }
        if (isFloat) {
            assert reg instanceof VfpReg;
            return Math.toIntExact(((VfpReg) reg).index);
        } else {
            assert reg instanceof Reg;
            return ((Reg)reg).ty.toInt();
        }
    }

    // 找到数组内最大值，若多个值相同返回靠前的。
    public static int findMaxInd(int[] next) {
        int max = Integer.MIN_VALUE;
        int maxind = -1;
        for(int i=0;i<next.length;i++) {
            if (next[i] > max) {
                max = next[i];
                maxind = i;
            }
        }
        return maxind;
    }

    public static int b2i(boolean b) {
        return b?1:0;
    }

}
