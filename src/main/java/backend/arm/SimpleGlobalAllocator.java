package backend.arm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.LivenessAnalyzer;
import backend.StackOperand;
import backend.VirtReg;
import backend.arm.inst.ConstrainRegInst;
import backend.arm.inst.LoadInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.StoreInst;
import backend.arm.inst.VLDRInst;
import backend.arm.inst.VMovInst;
import backend.arm.inst.VSTRInst;
import backend.lsra.LiveInfo;
import backend.lsra.LiveIntervalAnalyzer;
import ssa.ds.Instruction;

public class SimpleGlobalAllocator {
    AsmFunc func;
    Map<VirtReg, AsmOperand> registerMapping = new HashMap<>();
    // Map<VirtReg, Integer> stackMapping = new HashMap<>();
    public Map<VirtReg, StackOperand> stackMapping = new HashMap<>();
    Map<VirtReg, Integer> addressMapping = new HashMap<>(); // for alloca
    public Set<Integer> usedReg = new HashSet<>();
    public Set<Integer> usedVfpReg = new HashSet<>();
    public static Reg[] temps = { new Reg(Reg.Type.ip), new Reg(Reg.Type.lr) };
    public static VfpReg[] fpTemps = {new VfpReg(14), new VfpReg(15)};

    /**
     * interference graph
     */
    Map<VirtReg, Set<VirtReg>> IG = new HashMap<>();
    private Map<AsmBlock, LiveInfo> liveInfo; // filled in doAnalysis()
    // 本函数出现的所有 opr
    private Set<AsmOperand> allValues;

    public SimpleGlobalAllocator(AsmFunc f) {
        func = f;
    }

    public static AsmModule process(AsmModule m) {
        for (var f : m.funcs) {
            var g = new SimpleGlobalAllocator(f);
            g.doAnalysis();
        }
        return m;
    }

    public void doAnalysis() {
        // this.initAllValuesSet();
        // // liveness analysis        
        // var livenessAnalyzer = new LivenessAnalyzer(func);
        // livenessAnalyzer.execute();
        // this.liveInfo = livenessAnalyzer.liveInfo;
        // // build graph and color
        // this.buildInterferenceGraph();
        // fixup
        doFixUp();
    }

    private void initAllValuesSet() {
        allValues = new HashSet<>();
        for (var b : func.bbs) {
            for (var inst : b.insts) {
                allValues.addAll(inst.uses);
                allValues.addAll(inst.defs);
            }
        }
    }

    private LiveInfo liveInfoOf(AsmBlock b) {
        var ret = liveInfo.get(b);
        assert ret != null;
        return ret;
    }

    // 获取指令所涉及的所有 vreg, 包括 def 和 use
    // 注意如果 defs uses 有重复，不会去重
    private ArrayList<AsmOperand> operandsOf(AsmInst i) {
        var ret = new ArrayList<AsmOperand>();
        ret.addAll(i.defs);
        ret.addAll(i.uses);
        return ret;
    }

    private void buildInterferenceGraph() {
        for (var bb : func.bbs) {
            var living = liveInfoOf(bb).liveIn;
            var remain = new HashMap<VirtReg, Integer>();

            // get initial remain
            for (var vreg : living) {
                for (var inst : bb.insts) {
                    // inst.defs + inst.uses
                    var oprs = operandsOf(inst);
                    for (var opr : oprs) {
                        if (opr.equals(vreg)) {
                            remain.put(vreg, remain.getOrDefault(vreg, 0) + 1);
                        }
                    }
                }
                if (liveInfoOf(bb).liveOut.contains(vreg)) {
                    remain.put(vreg, remain.getOrDefault(vreg, 0) + 1);
                }
            }
            // connect all phis
            // - NOT needed for us

            // process all instructions
            for (var inst : bb.insts) {
                for (var opr : operandsOf(inst)) {
                    // 原 if (values.count(inst)) 的代码我没处理
                }
            }
        }
    }

    // 使用IP和LR，修补任何没有分配到寄存器的值。
    public void doFixUp() {
        for (var blk : func) {
            int addedInstCount = 0;
            for (int i = 0; i < blk.insts.size(); i++) {
                var inst = blk.insts.get(i);
                List<AsmInst> toInsertBefore = new ArrayList<>();
                List<AsmInst> toInsertAfter = new ArrayList<>();
                Map<AsmOperand, VirtReg> inConstraints = null;
                Map<AsmOperand, VirtReg> outConstraints = null;
                if (inst instanceof ConstrainRegInst) {
                    inConstraints = new HashMap<>(((ConstrainRegInst) inst).getInConstraints());
                    outConstraints = new HashMap<>(((ConstrainRegInst) inst).getOutConstraints());
                }

                // 先处理use
                Set<Integer> freeTemp = new HashSet<>();
                freeTemp.add(0);
                freeTemp.add(1);
                for (int j=0;j<inst.uses.size();j++) {
                    var vreg_ = inst.uses.get(j);
                    if (!(vreg_ instanceof VirtReg))
                        continue;
                    var vreg = (VirtReg) vreg_;
                    var realReg = registerMapping.get(vreg);
                    var constraintReg = getRegFromConstraint(inConstraints, vreg);
                    // 如果有寄存器：有约束。匹配直接跳过，不匹配的则生成move
                    //   没约束的直接用寄存器
                    // 没寄存器的要先load值到临时寄存器，然后用临时寄存器作为op。
                    //  有约束的最后move到约束寄存器。
                    if (realReg != null) { // 有寄存器
                        if (constraintReg != null) {
                            if (!realReg.equals(constraintReg)) {
                                // 如果有寄存器，有约束：不匹配的则生成move
                                AsmInst mov = makeMov(blk, vreg.isFloat, constraintReg, realReg, "SimpleGlob use mov1");
                                toInsertBefore.add(mov);
                                inst.uses.set(j, constraintReg);
                            }
                        } else { // 有寄存器没约束的直接用寄存器
                            inst.uses.set(j, realReg);
                        }
                    } else {
                        if (constraintReg != null) {
                            // 理论上不会占用寄存器
                            loadToReg(constraintReg, vreg, freeTemp, blk, toInsertBefore, true);
                            inst.uses.set(j, constraintReg);
                        } else {
                            AsmOperand to = loadToReg(null, vreg, freeTemp, blk, toInsertBefore, true);
                            inst.uses.set(j, to);
                        }
                    }
                }
                // 前面即使 用了，use分配结束也free出来了。
                freeTemp = new HashSet<>();
                freeTemp.add(0);
                freeTemp.add(1);
                // 再处理def
                // 没寄存器的：没约束先用临时寄存器，栈上分配临时位置，然后立刻store到栈上。
                //  有约束的直接从约束寄存器拿到栈上。
                // 有寄存器的: 没约束直接用，有约束可能生成move
                for (int j=0;j<inst.defs.size();j++) {
                    var vreg_ = inst.defs.get(j);
                    if (!(vreg_ instanceof VirtReg))
                        continue;
                    var vreg = (VirtReg) vreg_;
                    var realReg = registerMapping.get(vreg);
                    var constraintReg = getRegFromConstraint(outConstraints, vreg);
                    if (realReg != null) {
                        if (constraintReg != null) {
                            if (!realReg.equals(constraintReg)) {
                                // 如果有寄存器，有约束：不匹配的则生成move
                                AsmInst mov = makeMov(blk, vreg.isFloat, realReg, constraintReg, "SimpleGlob use mov1");
                                toInsertAfter.add(mov);
                                inst.defs.set(j, constraintReg);
                            }
                        } else { // 有寄存器没约束的直接用寄存器
                            inst.defs.set(j, realReg);
                        }
                    } else { //没寄存器的立刻store到栈上。
                        if (constraintReg != null) {
                            spillToStack(constraintReg, vreg, freeTemp, blk, toInsertAfter, true);
                            inst.defs.set(j, constraintReg);
                        } else {
                            // 分配一个spill
                            AsmOperand to = spillToStack(null, vreg, freeTemp, blk, toInsertAfter, true);
                            inst.defs.set(j, to);
                        }
                    }
                }

                // 插入需要增加的指令
                blk.insts.addAll(i, toInsertBefore);
                i += toInsertBefore.size();

                blk.insts.addAll(i+1, toInsertAfter);
                i += toInsertAfter.size();

            }
        }
        // 最后处理用到的reg和设置func.usedCalleeSavedReg，并且插入相关的保存和恢复指令。
        // 最后更新一下用到的callee saved register到函数内
        List<Entry<AsmOperand, StackOperand>> used = new ArrayList<>();
        for (int ind : usedReg) {
            Reg.Type t = Reg.Type.values()[ind];
            if (t.isCalleeSaved()) {
                used.add(Map.entry(new Reg(t), new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4))));
            }
        }
        for (int ind : usedVfpReg) {
            if (VfpReg.isCalleeSaved(ind)) {
                used.add(Map.entry(new VfpReg(ind), new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4))));
            }
        }
        func.usedCalleeSavedReg = used;
        LocalRegAllocator.insertSaveReg(func);
    }

    // 从val存到vreg
    public AsmOperand spillToStack(AsmOperand val, VirtReg vreg, Set<Integer> freeTemp, AsmBlock blk,
            List<AsmInst> toInsertAfter, boolean exclude) {
        if (val == null) {
            // get one from temp
            assert freeTemp.size() > 0;
            int alloc_ = freeTemp.iterator().next();
            if (vreg.isFloat){
                val = fpTemps[alloc_];
            } else {
                val = temps[alloc_];
            }
            
            if (exclude) {
                freeTemp.remove(Integer.valueOf(alloc_)); // 占用一个临时寄存器用来分配给那边的def
            }
        }
        assert !registerMapping.containsKey(vreg);
        // assert stackMapping.containsKey(vreg);
        var spilledLoc = allocateOrGetSpill(vreg);
        AsmInst store;
        if (vreg.isFloat) {
            store = new VSTRInst(blk, val, spilledLoc);
        } else {
            store = new StoreInst(blk, val, spilledLoc);
        }
        store.comment = "Spill "+ vreg.comment;
        toInsertAfter.addAll(Generator.expandStackOperandLoadStoreTmp(store, temps[freeTemp.iterator().next()]));
        return val;
    }

    public AsmOperand loadToReg(AsmOperand to, VirtReg vreg, Set<Integer> freeTemp, AsmBlock blk, List<AsmInst> toInsertBefore, boolean exclude) {
        int alloc_ = -1;
        if (to == null) {
            // get one from temp
            assert freeTemp.size() > 0;
            alloc_ = freeTemp.iterator().next();
            if (vreg.isFloat){
                to = fpTemps[alloc_];
            } else {
                to = temps[alloc_];
            }
            if (exclude) {
                freeTemp.remove(Integer.valueOf(alloc_));
            }
        }
        assert !registerMapping.containsKey(vreg);
        assert stackMapping.containsKey(vreg);
        var spilledLoc = allocateOrGetSpill(vreg);
        AsmInst load;
        if (vreg.isFloat) {
            load = new VLDRInst(blk, to, spilledLoc);
        } else {
            load = new LoadInst(blk, to, spilledLoc);
        }
        load.comment = "load spilled "+vreg.comment;
        if (!vreg.isFloat){
            toInsertBefore.addAll(Generator.expandStackOperandLoadStoreTmp(load, to));
        } else {
            AsmOperand tmp;
            if (alloc_ != -1) {
                tmp = temps[alloc_];
            } else {
                tmp = temps[freeTemp.iterator().next()];
            }
            toInsertBefore.addAll(Generator.expandStackOperandLoadStoreTmp(load, tmp));
        }
        return to;
    }

    public static AsmOperand getRegFromConstraint(Map<AsmOperand, VirtReg> inConstraints, VirtReg vreg) {
        AsmOperand phyReg = null;
        if (inConstraints == null) {
            return null;
        }
        for (var ent : inConstraints.entrySet()) {
            if (ent.getValue().equals(vreg)) {
                phyReg = ent.getKey();
                inConstraints.remove(phyReg);
                break;
            }
        }
        return phyReg;
    }

    private AsmInst makeMov(AsmBlock blk, boolean isFloat, AsmOperand regTo, AsmOperand regFrom, String comment) {
        AsmInst mov;
        if (isFloat) {
            mov = new VMovInst(blk, VMovInst.Ty.CPY, regTo, regFrom);
        } else {
            mov = new MovInst(blk, MovInst.Ty.REG, regTo, regFrom);
        }

        mov.comment = comment;
        return mov;
    }

    private StackOperand allocateOrGetSpill(VirtReg vreg) {
        StackOperand spilledLoc;
        spilledLoc = stackMapping.get(vreg);
        if (spilledLoc == null) {
            spilledLoc = new StackOperand(StackOperand.Type.SPILL, func.sm.allocSpill(4));
            spilledLoc.comment = vreg.comment;
            stackMapping.put(vreg, spilledLoc);
        }
        return spilledLoc;
    }

}
