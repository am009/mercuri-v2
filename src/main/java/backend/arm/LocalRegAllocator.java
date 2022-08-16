package backend.arm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.StackOperand;
import backend.VirtReg;
import backend.arm.inst.CallInst;
import backend.arm.inst.ConstrainRegInst;
import backend.arm.inst.LoadInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.Prologue;
import backend.arm.inst.RetInst;
import backend.arm.inst.StoreInst;
import backend.arm.inst.VLDRInst;
import backend.arm.inst.VMovInst;
import backend.arm.inst.VSTRInst;
import ds.Global;

// 简单的寄存器分配算法，尽量保证正确性即可。之后应当实现图着色来替换
// 由于未优化的IR可能没有global（跨基本块使用）的值，所以相关的正确性有待进一步测试。
// Bottom-Up Local Register Allocation
public class LocalRegAllocator {
    public static final int CORE_REG_COUNT = 11;
    public static final int VFP_REG_COUNT = 32;

    public AsmFunc func;
    public Set<VirtReg> globs = new HashSet<>(); // 跨基本块的全局值
    public Map<VirtReg, StackOperand> globSpill = new HashMap<>();
    // asm bb 的上下文信息
    public Map<AsmBlock, BlockData> blockData = new HashMap<>();
    // 分配过的寄存器全部收集起来，之后过滤出callee saved的设置到函数里。
    public Set<Integer> usedReg = new HashSet<>();
    public Set<Integer> usedVfpReg = new HashSet<>();
    BlockData currentBD; // 处理每一个基本块时，传递数据的全局变量
    private void addUsedReg(int regind, boolean isFloat) {
        if (isFloat) {
            usedVfpReg.add(regind);
        } else {
            usedReg.add(regind);
        }
    }

    public LocalRegAllocator(AsmFunc f) {
        this.func = f;
    }

    // int vregInd = -1;
    // VirtReg getNewVreg(boolean isFloat, String comment) {
    //     var ret = new VirtReg(vregInd, isFloat);
    //     ret.comment = comment;
    //     vregInd -= 1;
    //     return ret;
    // }

    public class BlockData {
        // public Set<VirtReg> live = new HashSet<>();
        public Map<VirtReg, List<Integer>> useLists = new HashMap<>();// 里面的值应该是降序的。
        public Map<VirtReg, AllocHint> allocHint = new HashMap<>();
        public Set<VirtReg> dirtyGlobal = new HashSet<>();
    }

    public static class AllocHint {
        AsmOperand cons; // 如果后面存在约束，则优先直接分配到约束所在寄存器
        boolean isCrossCall; // 如果跨了函数调用，则优先分配callee saved reg。

        public AllocHint(AsmOperand cons, boolean isCrossCall) {
            this.cons = cons; this.isCrossCall = isCrossCall;
        }
    }

    public class RegClass {
        int count;
        VirtReg[] current;
        int[] next;
        // free优先使用index靠前的
        LinkedList<Integer> free = new LinkedList<>();
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
            // normal reg
            rcs[0] = new RegClass(CORE_REG_COUNT);
            // vfp reg
            rcs[1] = new RegClass(VFP_REG_COUNT);
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

        public int allocateReg(VirtReg vreg, AllocHint hint, AsmBlock blk, List<AsmInst> toInsertBefore) {
            int ret = -1;
            RegClass rc = rcs[b2i(vreg.isFloat)];
            if (rc.free.size() > 0) {
                var hintInd = -1;
                if (hint != null && hint.cons != null) {
                    hintInd = reg2ind(hint.cons, vreg.isFloat);
                }
                if (hintInd != -1 && rc.free.contains(hintInd)) {
                    ret = hintInd;
                } else if (hint != null && hint.isCrossCall && (getFreeCalleeSavedReg(rc, vreg.isFloat) != -1)) {
                    ret = getFreeCalleeSavedReg(rc, vreg.isFloat);
                } else {
                    // get first element
                    ret = rc.free.getFirst();
                }
            } else {
                ret = findMaxInd(rc.next);
                spillInd(ret, vreg.isFloat, blk, toInsertBefore);
            }
            assert ret != -1;
            rc.current[ret] = vreg;
            rc.next[ret] = -1;
            rc.free.remove(Integer.valueOf(ret));
            return ret;
        }

        // 仅spill动作，不会释放对应寄存器
        private void spillInd(int ind, boolean isFloat, AsmBlock blk, List<AsmInst> toInsertBefore) {
            RegClass rc = rcs[b2i(isFloat)];
            var toSpillVreg = rc.current[ind];
            StackOperand spilledLoc = allocateOrNullSpill(toSpillVreg);
            if(spilledLoc == null) {
                return;
            }
            AsmInst store;
            var to = ind2Reg(ind, isFloat);
            if (isFloat) {
                store = new VSTRInst(blk, to, spilledLoc);
            } else {
                store = new StoreInst(blk, to, spilledLoc);
            }
            store.comment = "Spill "+toSpillVreg.comment;
            toInsertBefore.addAll(Generator.expandStackOperandLoadStoreIP(store));
        }

        // allocateOrGetSpill的修改版，如果local值获取过spill区域，则说明这个值被保存过。
        // 而每个vreg只有一个live range，则说明不需要再额外生成store了
        private StackOperand allocateOrNullSpill(VirtReg vreg) {
            StackOperand spilledLoc;
            if (globs.contains(vreg)) {
                spilledLoc = globSpill.get(vreg);
                if (spilledLoc == null) {
                    assert currentBD.dirtyGlobal.contains(vreg);
                    spilledLoc = new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4));
                    spilledLoc.comment = vreg.comment;
                    globSpill.put(vreg, spilledLoc);
                } else {
                    // 非dirty的gloabl可以不spill
                    if (!currentBD.dirtyGlobal.contains(vreg)) {
                        return null;
                    }
                }
            } else {
                spilledLoc = localSpill.get(vreg);
                if (spilledLoc != null) {
                    return null;
                }
                if (spilledLoc == null) {
                    spilledLoc = new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4));
                    spilledLoc.comment = vreg.comment;
                    localSpill.put(vreg, spilledLoc);
                }
            }
            return spilledLoc;
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
        public void copyTo(int from, int to, boolean isFloat, AsmBlock blk, List<AsmInst> toInsertBefore) {
            assert from >= 0 && to >= 0;
            RegClass rc = rcs[b2i(isFloat)];
            if (!rc.free.contains(to)) {
                spillInd(to, isFloat, blk, toInsertBefore);
            }
            // generate mov inst;
            AsmOperand regTo = ind2Reg(to, isFloat);
            AsmOperand regFrom = ind2Reg(from, isFloat);
            AsmInst mov;
            if (isFloat) {
                mov = new VMovInst(blk, VMovInst.Ty.CPY, regTo, regFrom);
            } else {
                mov = new MovInst(blk, MovInst.Ty.REG, regTo, regFrom);
            }
            
            mov.comment = "LocalRegAllocator.moveTo";
            toInsertBefore.add(mov);

            rc.free.remove(Integer.valueOf(to));
            rc.current[to] = rc.current[from];
            rc.next[to] = rc.next[from];
        }

        public void allocateTo(int to, VirtReg vreg, AsmBlock blk, List<AsmInst> toInsertBefore) {
            assert to >= 0;
            RegClass rc = rcs[b2i(vreg.isFloat)];
            if (!rc.free.contains(to)) {
                spillInd(to, vreg.isFloat, blk, toInsertBefore);
                free(to, vreg.isFloat);
            }
            rc.free.remove(Integer.valueOf(to));
            rc.current[to] = vreg;
            rc.next[to] = -1;
        }

        public void free(int ind, boolean isFloat) {
            RegClass rc = rcs[b2i(isFloat)];
            rc.free.addFirst(ind);
            rc.current[ind] = null;
            rc.next[ind] = Integer.MAX_VALUE;
        }

        public void setNext(int ind, boolean isFloat, int next) {
            RegClass rc = rcs[b2i(isFloat)];
            // 刚分配的时候应该设置为了-1，但是可能已经在寄存器里。
            // assert rc.next[ind] == -1;
            rc.next[ind] = next;
        }

        // 所有寄存器都没有被占用，如果有则一定是global的值，spill到内存里。
        public void onBlockEnd(AsmBlock blk, List<AsmInst> toInsertBefore) {
            for (RegClass rc: rcs) {
                boolean isFloat = rc.count == VFP_REG_COUNT;
                for(int i=0;i<rc.count;i++) {
                    var vreg = rc.current[i];
                    if (vreg != null && globs.contains(vreg)) {
                        spillInd(i, isFloat, blk, toInsertBefore);
                    }
                }
            }
        }

        public void spillCallerSaved(Map<AsmOperand, VirtReg> inConstraints, AsmBlock blk, List<AsmInst> toInsertBefore) {
            var iargs = new HashSet<Integer>();
            var fargs = new HashSet<Long>();
            for (var ent: inConstraints.entrySet()) {
                var reg = ent.getKey();
                if (reg instanceof Reg) {
                    iargs.add(((Reg)reg).ty.toInt());
                } else if(reg instanceof VfpReg) {
                    fargs.add(((VfpReg)reg).index);
                } else {throw new RuntimeException();}
            }
            for (int i=0;i<CORE_REG_COUNT;i++) {
                Reg.Type r = Reg.Type.values[i];
                if (!r.isCalleeSaved() && !rcs[0].free.contains(i) && !iargs.contains(i)) {
                    // spill ind
                    spillInd(i, false, blk, toInsertBefore);
                    free(i, false);
                }
            }
            for (int i=0;i<VfpReg.count;i++) {
                if (!VfpReg.isCalleeSaved(i) && !rcs[1].free.contains(i) && !fargs.contains(Long.valueOf(i))) {
                    spillInd(i, true, blk, toInsertBefore);
                    free(i, true);
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

    // 从rc.free中找，是否有callee saved reg，有则返回index
    public int getFreeCalleeSavedReg(RegClass rc, boolean isFloat) {
        int startInd;
        if (!isFloat) { // r4+
            startInd = 4;
        } else { // s16+
            startInd = 16;
        }
        for (int ind: rc.free) {
            if (ind >= startInd) {
                return ind;
            }
        }
        return -1;
    }

    // 把每个基本块里，每个虚拟寄存器的使用处index记录下来组合成一个list，便于实现Dist函数
    // 需要每个寄存器的最后一个使用点，用于判断某寄存器是否可以被Free掉。
    // global值的发现：如果遍历到开头时发现某变量被使用而没有被定义，则说明是global的。
    // 保存每个基本块使用过的global值。以便于在后面用到的时候从内存中取出
    private void preAnalysis() {
        for (var blk: func) {
            BlockData ba = new BlockData();
            blockData.put(blk, ba);
            Set<VirtReg> live = new HashSet<>();
            Map<VirtReg, List<Integer>> useList = ba.useLists;
            Set<VirtReg> dead = new HashSet<>(); // 确保每个VirtReg仅有一个live range。
            for (int j = blk.insts.size() - 1; j >= 0; j--) {
                var inst = blk.insts.get(j);
                // 逆向分析，所以先处理def
                for (var def: inst.defs) {
                    if (def instanceof VirtReg) {
                        live.remove((VirtReg)def);
                        dead.add((VirtReg)def);
                    }
                }
                // 在call的use和def中间还活跃的值，就是跨越了函数调用的，优先分配callee saved寄存器
                if (inst instanceof CallInst) {
                    for (var vreg: live) {
                        ba.allocHint.getOrDefault(vreg, new AllocHint(null, false)).isCrossCall = true;
                    }
                }
                for (var u: inst.uses) {
                    if (u instanceof VirtReg) {
                        live.add((VirtReg)u);
                        if (useList.containsKey(u)) {
                            useList.get(u).add(j);
                        } else {
                            useList.computeIfAbsent(((VirtReg)u), k -> new ArrayList<>()).add(j);
                        }
                        if (dead.contains(u) && !globs.contains(u)) {
                            Global.logger.warning("Multiple live ranges use same VirtReg! (%s %s)", blk.label, u.toString());
                        }
                    }
                }
                if (inst instanceof ConstrainRegInst) {
                    // 替换式加入，因此仅保留最靠前的约束
                    for (var con: ((ConstrainRegInst)inst).getConstraints()) {
                        ba.allocHint.getOrDefault(con.getKey(), new AllocHint(null, false)).cons = con.getValue();
                    }
                }
            }
            globs.addAll(live);
        }
    }

    // 对每个指令 op vri1 vri2 -> vri3
    // 首先分配vri1和vri2，即use的值。
    private void doAnalysis() {
        preAnalysis();
        for (var blk: func) {
            var bd = blockData.get(blk);
            currentBD = bd;
            var state = new BlockAllocator();
            // // 把所有需要添加的指令都延迟到分配后添加。里面的值分别表示要在index处加入这个list的指令。
            // List<Map.Entry<Integer,List<AsmInst>>> addSeqs = new ArrayList<>();
            int addedInstCount = 0;
            for (int i=0;i<blk.insts.size();i++) {
                var inst = blk.insts.get(i);
                List<VirtReg> uses = filterVirtReg(inst.uses);
                List<AsmOperand> newUses = new ArrayList<>();
                List<AsmInst> toInsertBefore = new ArrayList<>();
                List<AsmInst> toInsertAfter = new ArrayList<>();
                Map<AsmOperand, VirtReg> inConstraints = null;
                Map<AsmOperand, VirtReg> outConstraints = null;
                if (inst instanceof ConstrainRegInst) {
                    inConstraints = new HashMap<>(((ConstrainRegInst)inst).getInConstraints());
                    outConstraints = new HashMap<>(((ConstrainRegInst)inst).getOutConstraints());
                }
                
                // for (var vreg: uses) {
                for (int j=0;j<uses.size();j++) {
                    var vreg = uses.get(j);
                    // 首先检查有没有约束
                    // 没有约束的就直接看是不是寄存器，不是的话有两种情况，要么spill了，要么是global的spill，就分配一个寄存器并且把值加载出来。
                    // 有约束的就得首先获得约束，然后检查是否在目标寄存器里，不在的就还是要强行分配寄存器
                    // 如果已经在寄存器里，强行分配寄存器就先spill原有的值，然后mov过去？
                    if (inConstraints == null || !inConstraints.containsValue(vreg)) { // 没有约束，正常分配
                        var hint = bd.allocHint.get(vreg);
                        int currentind = state.checkInPhyReg(vreg);
                        AsmOperand phyReg = ind2Reg(currentind, vreg.isFloat);
                        if (phyReg != null) {
                            addUsedReg(currentind, vreg.isFloat);
                            newUses.add(phyReg);
                            continue;
                        }
                        // 没有约束，且没有分配寄存器，先随便分配寄存器，然后找到值，加载进来。
                        int regind = state.allocateReg(vreg, hint, blk, toInsertBefore);
                        StackOperand spilledLoc = state.getSpill(vreg);
                        assert spilledLoc != null; // 
                        var to = ind2Reg(regind, vreg.isFloat);
                        AsmInst load;
                        if (to.isFloat) {
                            load = new VLDRInst(blk, to, spilledLoc);
                        } else {
                            load = new LoadInst(blk, to, spilledLoc);
                        }
                        load.comment = "load spilled "+vreg.comment;
                        toInsertBefore.addAll(Generator.expandStackOperandLoadStoreIP(load));
                        addUsedReg(regind, vreg.isFloat);
                        newUses.add(ind2Reg(regind, vreg.isFloat));
                    } else {
                        // get key from value
                        AsmOperand phyReg = null;
                        for (var ent: inConstraints.entrySet()) {
                            if (ent.getValue().equals(vreg)) {
                                phyReg = ent.getKey();
                                inConstraints.remove(phyReg);
                                break;
                            }
                        }
                        assert phyReg != null;
                        int currentind = state.checkInPhyReg(vreg);
                        boolean inReg = currentind != -1;
                        int ind = reg2ind(phyReg, vreg.isFloat);
                        assert ind >= 0;
                        phyReg.comment = vreg.comment;
                        addUsedReg(ind, vreg.isFloat);
                        newUses.add(phyReg);
                        if (currentind == ind) {
                            continue;
                        }
                        // 必须要强行分配到ind寄存器中
                        if (inReg) {
                            state.copyTo(currentind, ind, vreg.isFloat, blk, toInsertBefore);
                            continue;
                        }
                        state.allocateTo(ind, vreg, blk, toInsertBefore);
                        StackOperand spilledLoc = state.getSpill(vreg);
                        AsmInst load;
                        if (phyReg.isFloat) {
                            load = new VLDRInst(blk, phyReg, spilledLoc);
                        } else {
                            load = new LoadInst(blk, phyReg, spilledLoc);
                        }
                        load.comment = "load spilled "+vreg.comment;
                        toInsertBefore.addAll(Generator.expandStackOperandLoadStoreIP(load));
                    }
                }
                assert uses.size() == newUses.size();
                // 如果是call指令，则caller saved reg要spill掉。TODO 实现对跨越call的寄存器的hint，不要分配到这些寄存器？
                if (inst instanceof CallInst) {
                    state.spillCallerSaved(((CallInst)inst).getInConstraints(), blk, toInsertBefore);
                }

                // 如果use不需要了当前的值，则free寄存器。
                // 缓存结果
                ArrayList<Boolean> updateNext = new ArrayList<>();
                for(var vreg: uses) {
                    int lastUse = bd.useLists.get(vreg).get(0); // global value may have size 0.
                    boolean notNeed = (i-addedInstCount) >= lastUse; // 是最后一个用该VirtReg的指令
                    int currentInd = state.checkInPhyReg(vreg);
                    // assert currentInd != -1;
                    if (currentInd != -1) { // 指令多个操作数用到了相同的vreg
                        if (notNeed) { // TODO check notNeed 是否判断正确
                            if (globs.contains(vreg)) { // use不会改变vreg的值，如果spill过就不用spill？TODO
                                state.spillInd(currentInd, vreg.isFloat, blk, toInsertBefore);
                            }
                            state.free(currentInd, vreg.isFloat);
                        } else if (inst instanceof CallInst) { // 特殊情况，Call指令的参数（caller saved reg）即使你需要，也得spill&free掉。而且不用updateNext
                            notNeed = true; // 不计算Next。
                            state.spillInd(currentInd, vreg.isFloat, blk, toInsertBefore);
                            state.free(currentInd, vreg.isFloat);
                        }
                    }
                    updateNext.add(!notNeed);
                }
                // 给Def分配寄存器
                List<VirtReg> defs = filterVirtReg(inst.defs);
                List<AsmOperand> newDefs = new ArrayList<>();
                assert inst instanceof Prologue || defs.size() <= 1; // 应该是吧
                for (var vreg: defs) {
                    // 由于MOVW+MOVT的存在，会出现def复用寄存器的情况
                    int currentind = state.checkInPhyReg(vreg);
                    if (currentind != -1) {
                        assert inst instanceof MovInst;
                        AsmOperand phyReg = ind2Reg(currentind, vreg.isFloat);
                        addUsedReg(currentind, vreg.isFloat);
                        newDefs.add(phyReg);
                        continue;
                    }
                    if (outConstraints == null || !outConstraints.containsValue(vreg)) {
                        var hint = bd.allocHint.get(vreg);
                        int regind = state.allocateReg(vreg, hint, blk, toInsertBefore);
                        var phyReg = ind2Reg(regind, vreg.isFloat);
                        phyReg.comment = vreg.comment;
                        addUsedReg(regind, vreg.isFloat);
                        newDefs.add(phyReg);
                    } else {
                        // get key from value
                        AsmOperand phyReg = null;
                        for (var ent: outConstraints.entrySet()) {
                            if (ent.getValue().equals(vreg)) {
                                phyReg = ent.getKey();
                                outConstraints.remove(phyReg);
                                break;
                            }
                        }
                        assert phyReg != null;
                        phyReg.comment = vreg.comment;
                        int regind = reg2ind(phyReg, vreg.isFloat);
                        state.allocateTo(regind, vreg, blk, toInsertBefore);
                        addUsedReg(regind, vreg.isFloat);
                        newDefs.add(phyReg);
                    }
                    // 处理dirtyGlobal的维护
                    if (globs.contains(vreg)) {
                        bd.dirtyGlobal.add(vreg);
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
                    if (updateNext.get(j)) { // 仍然需要寄存器
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
                blk.insts.addAll(i+1, toInsertAfter);
                i += toInsertAfter.size();
                addedInstCount += toInsertAfter.size();

                blk.insts.addAll(i, toInsertBefore);
                i += toInsertBefore.size();
                addedInstCount += toInsertBefore.size();
            }
            // // ~~插入所有指令~~

            // 结束的时候将还在寄存器里的，用到的global值放到栈上
            List<AsmInst> toInsert = new ArrayList<>();
            state.onBlockEnd(blk, toInsert);
            // 插入到跳转指令之前
            blk.insts.addAll(blk.insts.size()-1, toInsert);
        }
        // 最后更新一下用到的callee saved register到函数内
        List<Entry<AsmOperand, StackOperand>> used = new ArrayList<>();
        for (int ind: usedReg) {
            Reg.Type t = Reg.Type.values()[ind];
            if (t.isCalleeSaved()) {
                used.add(Map.entry(new Reg(t), new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4))));
            }
        }
        for (int ind: usedVfpReg) {
            if(VfpReg.isCalleeSaved(ind)) {
                used.add(Map.entry(new VfpReg(ind), new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4))));
            }
        }
        func.usedCalleeSavedReg = used;
        insertSaveReg();
    }

    // 在Prologue后，Ret前分别插入指令，保存和恢复用到的Callee Saved Reg
    private void insertSaveReg() {
        List<AsmInst> stores = new ArrayList<>();

        for (var ent: func.usedCalleeSavedReg) {
            var reg = ent.getKey();
            var loc = ent.getValue();
            AsmInst store;
            if (reg instanceof Reg) {
                store = new StoreInst(func.entry, ent.getKey(), loc);
            } else if (reg instanceof VfpReg) {
                store = new VSTRInst(func.entry, ent.getKey(), loc);
            } else {throw new RuntimeException("Unknown Register Type to save.");}
            store.comment = "Store callee saved reg "+reg.toString();
            stores.addAll(Generator.expandStackOperandLoadStoreIP(store));
        }
        // 插入到prologue后面
        assert func.entry.insts.get(0) instanceof Prologue;
        func.entry.insts.addAll(1, stores);

        for (var abb: func) {
            // for (var inst: abb.insts) {
            for (int i=0;i<abb.insts.size();i++) {
                var inst = abb.insts.get(i);
                if (!(inst instanceof RetInst)) {continue;}
                List<AsmInst> loads = new ArrayList<>();
                for (var ent: func.usedCalleeSavedReg) {
                    var reg = ent.getKey();
                    var loc = ent.getValue();
                    if (reg instanceof Reg) {
                        var load = new LoadInst(func.entry, ent.getKey(), loc);
                        load.comment = "Load callee saved reg "+reg.toString();
                        loads.addAll(Generator.expandStackOperandLoadStoreIP(load));
                    } else if (reg instanceof VfpReg) {
                        var load = new VLDRInst(func.entry, ent.getKey(), loc);
                        load.comment = "Load callee saved reg "+reg.toString();
                        loads.addAll(Generator.expandStackOperandLoadStoreIP(load));
                    } else {throw new RuntimeException("Unknown Register Type to save.");}
                }
                abb.insts.addAll(i, loads);
                i += loads.size();
            }
        }
    }

    private int calcDist(int currentInd, List<Integer> useList) {
        if (useList == null) {
            Global.logger.warning("Cannot find usage of a virtual register. currentInd=" + currentInd);
            return Integer.MAX_VALUE;
        }
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
