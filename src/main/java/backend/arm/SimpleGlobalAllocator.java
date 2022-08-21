package backend.arm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.LivenessAnalyzer;
import backend.StackOperand;
import backend.VirtReg;
import backend.arm.inst.CallInst;
import backend.arm.inst.ConstrainRegInst;
import backend.arm.inst.LoadInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.StoreInst;
import backend.arm.inst.VLDRInst;
import backend.arm.inst.VMovInst;
import backend.arm.inst.VSTRInst;
import backend.lsra.LiveInfo;

public class SimpleGlobalAllocator {
    public static final int CORE_REG_COUNT = 11;

    AsmFunc func;
    Map<VirtReg, AsmOperand> registerMapping = new HashMap<>();
    // Map<AsmOperand, AsmOperand> registerMapping = new HashMap<>();
    // Map<VirtReg, Integer> stackMapping = new HashMap<>();
    public Map<VirtReg, StackOperand> stackMapping = new HashMap<>();
    Map<VirtReg, Integer> addressMapping = new HashMap<>(); // for alloca
    public Set<Integer> usedReg = new HashSet<>();
    public Set<Integer> usedVfpReg = new HashSet<>();
    public static Reg[] temps = { new Reg(Reg.Type.ip), new Reg(Reg.Type.lr) };
    public static VfpReg[] fpTemps = {new VfpReg(14), new VfpReg(15)};
    public List<VirtReg> callerSaved = new ArrayList<>();
    public List<VirtReg> calleeSaved = new ArrayList<>();
    public List<VirtReg> callerSavedFp = new ArrayList<>();
    public List<VirtReg> calleeSavedFp = new ArrayList<>();
    List<Integer> initAllow = IntStream.range(0, 11).boxed().collect(Collectors.toList());
    List<Integer> initAllowFp = IntStream.range(0, 32).boxed().collect(Collectors.toCollection(ArrayList::new));

    // std::map<Value *, std::map<Value *, double>>
    // 倾向于和谁一样
    Map<VirtReg, Map<VirtReg, Double>> movBonus = new HashMap<>();
    // 倾向于用哪个
    Map<VirtReg, Map<Integer, Double>> abiBonus = new HashMap<>();
    // 是否先分配
    Map<VirtReg, Double> weight = new HashMap<>();

    int vregInd = -1;
    VirtReg getNewVreg(boolean isFloat, String comment) {
        var ret = new VirtReg(vregInd, isFloat);
        ret.comment = comment;
        vregInd -= 1;
        return ret;
    }

    /**
     * interference graph
     */
    Map<VirtReg, Set<VirtReg>> IG = new HashMap<>();
    private Map<AsmBlock, LiveInfo> liveInfo; // filled in doAnalysis()
    // 本函数出现的所有 opr
    private Set<AsmOperand> allValues;

    public SimpleGlobalAllocator(AsmFunc f) {
        func = f;
        initAllowFp.remove(Integer.valueOf(14));
        initAllowFp.remove(Integer.valueOf(15));
        for (int i=0;i<4;i++) {
            var reg = new Reg(Reg.Type.values[i]);
            var vreg = getNewVreg(false, "precolored");
            callerSaved.add(vreg);
            registerMapping.put(vreg, reg);
        }
        for (int i=4;i<11;i++) {
            var reg = new Reg(Reg.Type.values[i]);
            var vreg = getNewVreg(false, "precolored");
            calleeSaved.add(vreg);
            registerMapping.put(vreg, reg);
        }
        for (int i=0;i<14;i++) {
            var reg = new VfpReg(i);
            var vreg = getNewVreg(true, "precolored");
            callerSavedFp.add(vreg);
            registerMapping.put(vreg, reg);
        }
        for (int i=16;i<32;i++) {
            var reg = new VfpReg(i);
            var vreg = getNewVreg(true, "precolored");
            calleeSavedFp.add(vreg);
            registerMapping.put(vreg, reg);
        }
    }

    public static AsmModule process(AsmModule m) {
        for (var f : m.funcs) {
            var g = new SimpleGlobalAllocator(f);
            g.doAnalysis();
        }
        return m;
    }

    public void doAnalysis() {
        this.initAllValuesSet();
        // liveness analysis
        var livenessAnalyzer = new LivenessAnalyzer(func);
        livenessAnalyzer.execute();
        this.liveInfo = livenessAnalyzer.liveInfo;
        // build graph and color
        this.buildInterferenceGraph();
        // color
        this.doColor();
        // fixup
        doFixUp();
    }

    public void doColor() {
        Stream<Map.Entry<VirtReg, Double>> sorted = weight.entrySet().stream()
            .sorted(Map.Entry.comparingByValue());
        sorted.forEach(ent -> {
            var vreg = ent.getKey();
            if (registerMapping.containsKey(vreg)) {
                return;
            }
            var allowedRegs = getAllowedRegsList(vreg.isFloat);
            for (var igVreg: IG.getOrDefault(vreg, Set.of())) {
                if (vreg.isFloat == igVreg.isFloat && registerMapping.containsKey(igVreg)) {
                    allowedRegs.remove(Integer.valueOf(LocalRegAllocator.reg2ind(registerMapping.get(igVreg), vreg.isFloat)));
                }
            }
            if (allowedRegs.isEmpty()) {
                // add spill cost
                return;
            }
            // 找最大
            double maxBonus = -1;
            int maxId = allowedRegs.get(0);
            for (var id: allowedRegs) {
                double bonus = 0;
                for (var movEnt: movBonus.getOrDefault(vreg, Map.of()).entrySet()) {
                    if (registerMapping.containsKey(movEnt.getKey())) {
                        var realReg = registerMapping.get(movEnt.getKey());
                        if (vreg.isFloat == realReg.isFloat && LocalRegAllocator.reg2ind(realReg, vreg.isFloat) == id.intValue()) {
                            bonus += movEnt.getValue();
                        }
                    }
                }
                bonus += abiBonus.getOrDefault(vreg, Map.of()).getOrDefault(Integer.valueOf(id), 0.0);
                if (bonus > maxBonus) {
                    maxBonus = bonus;
                    maxId = id;
                }
            }
            var realReg = LocalRegAllocator.ind2Reg(maxId, vreg.isFloat);
            if (realReg.isFloat) {
                usedVfpReg.add(maxId);
            } else {
                usedReg.add(maxId);
            }
            registerMapping.put(vreg, realReg);
        });
    }

    public List<Integer> getAllowedRegsList(boolean isFloat) {
        if (isFloat) {
            return new ArrayList<>(initAllowFp);
        } else {
            return new ArrayList<>(initAllow);
        }
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
            var live = liveInfoOf(bb).liveOut;
            int size = bb.insts.size();
            for (int i=size-1;i>=0;i--) {
                var inst = bb.insts.get(i);
                // if (inst instanceof )
                List<VirtReg> uses = LocalRegAllocator.filterVirtReg(inst.uses);
                List<VirtReg> defs = LocalRegAllocator.filterVirtReg(inst.defs);
                live.addAll(defs);
                for (var vreg: defs) {
                    for (var vreg2: live) {
                        addEdge(vreg, vreg2);
                    }
                }
                live.removeAll(defs);
                // caller saved reg
                if (inst instanceof CallInst) {
                    for (var vreg: live) {
                        if (vreg.isFloat) {
                            for (var cr: callerSavedFp) {
                                addEdge(vreg, cr);
                            }
                        } else {
                            for (var cr: callerSaved) {
                                addEdge(vreg, cr);
                            }
                        }
                    }
                }
                live.addAll(uses);

            }

            // // process all instructions
            // for (var inst : bb.insts) {
            //     for (var opr : operandsOf(inst)) {
            //         // 原 if (values.count(inst)) 的代码我没处理
            //     }
            // }
        }
        // 各种权重
        double moveCost = 1.0;
        int nestLevel = 0;
        double loadCost = 16.0;
        double scale_factor = Math.pow(10.0, nestLevel);
        for (var bb : func.bbs) {
            for (var inst: bb.insts) {
                if (inst instanceof MovInst) {
                    var op1 = inst.uses.get(0);
                    var op2 = inst.defs.get(0);
                    // assert op1 instanceof VirtReg && op2 instanceof VirtReg;
                    
                    if (op1 instanceof VirtReg && op2 instanceof VirtReg) {
                        double bonus = (moveCost*scale_factor);
                        var map1 = movBonus.computeIfAbsent((VirtReg)op1, x -> new HashMap<>());
                        map1.put((VirtReg)op2, map1.getOrDefault(op2, 0.0) + bonus );
                        var map2 = movBonus.computeIfAbsent((VirtReg)op2, x -> new HashMap<>());
                        map2.put((VirtReg)op1, map2.getOrDefault(op1, 0.0) + bonus );
                        // weight
                        weight.put((VirtReg)op1, weight.getOrDefault((VirtReg)op1, 0.0) + bonus);
                        weight.put((VirtReg)op2, weight.getOrDefault((VirtReg)op2, 0.0) + bonus);
                    }
                }
                if (inst instanceof ConstrainRegInst) {
                    var inCons = new HashMap<>(((ConstrainRegInst) inst).getInConstraints());
                    var outCons = new HashMap<>(((ConstrainRegInst) inst).getOutConstraints());
                    double bonus = (moveCost*scale_factor);
                    for (var vreg_: inst.uses) {
                        if (vreg_ instanceof VirtReg) {
                            var vreg = (VirtReg) vreg_;
                            var cons = getRegFromConstraint(inCons, vreg);
                            if (cons != null) {
                                int ind = LocalRegAllocator.reg2ind(cons, vreg.isFloat);
                                var map1 = abiBonus.computeIfAbsent(vreg, x -> new HashMap<>());
                                map1.put(Integer.valueOf(ind), map1.getOrDefault(Integer.valueOf(ind), 0.0) + (moveCost*scale_factor) );
                                // weight
                                weight.put(vreg, weight.getOrDefault(vreg, 0.0) + bonus);
                            }
                        }
                        
                    }
                    for (var vreg_: inst.defs) {
                        if (vreg_ instanceof VirtReg) {
                            var vreg = (VirtReg) vreg_;
                            var cons = getRegFromConstraint(outCons, vreg);
                            if (cons != null) {
                                int ind = LocalRegAllocator.reg2ind(cons, vreg.isFloat);
                                var map1 = abiBonus.computeIfAbsent(vreg, x -> new HashMap<>());
                                map1.put(Integer.valueOf(ind), map1.getOrDefault(Integer.valueOf(ind), 0.0) + (moveCost*scale_factor) );
                                // weight
                                weight.put(vreg, weight.getOrDefault(vreg, 0.0) + bonus);
                            }
                        }
                    }
                }
                double bonus = (loadCost * scale_factor);
                for (var vreg_: inst.uses) {
                    if (vreg_ instanceof VirtReg) {
                        var vreg = (VirtReg) vreg_;
                        weight.put(vreg, weight.getOrDefault(vreg, 0.0) + bonus);
                    }
                }
                for (var vreg_: inst.defs) {
                    if (vreg_ instanceof VirtReg) {
                        var vreg = (VirtReg) vreg_;
                        weight.put(vreg, weight.getOrDefault(vreg, 0.0) + bonus);
                    }
                }
            }
        }
    }

    private void addEdge(VirtReg vreg, VirtReg vreg2) {
        if (vreg != vreg2) {
            IG.computeIfAbsent(vreg, x -> new HashSet<>()).add(vreg2);
            IG.computeIfAbsent(vreg2, x -> new HashSet<>()).add(vreg);
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
                Set<Integer> dead = new HashSet<>();
                Set<Integer> deadFp = new HashSet<>();
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
                                boolean isDead = false;
                                if (realReg.isFloat) {
                                    isDead = deadFp.contains(LocalRegAllocator.reg2ind(realReg, realReg.isFloat));
                                } else {
                                    isDead = dead.contains(LocalRegAllocator.reg2ind(realReg, realReg.isFloat));
                                }
                                if (isDead) {
                                    if (freeTemp.size() > 1) {
                                        int alloc_ = freeTemp.iterator().next();
                                        AsmOperand tmp;
                                        if (vreg.isFloat){
                                            tmp = fpTemps[alloc_];
                                        } else {
                                            tmp = temps[alloc_];
                                        }
                                        AsmInst mov1 = makeMov(blk, vreg.isFloat, tmp, realReg, "SimpleGlob escape dead");
                                        toInsertBefore.add(0, mov1);
                                        AsmInst mov = makeMov(blk, vreg.isFloat, constraintReg, tmp, "SimpleGlob use mov1");
                                        toInsertBefore.add(mov);
                                        inst.uses.set(j, constraintReg);
                                    } else {
                                        // 开头先保存到栈上，然后后面再加载出来
                                        List<AsmInst> tmp = new ArrayList<>();
                                        spillToStack(realReg, vreg, freeTemp, blk, tmp, false);
                                        toInsertBefore.addAll(0, tmp);
                                        tmp.clear();
                                        loadToReg(constraintReg, vreg, freeTemp, blk, toInsertBefore, false);
                                        inst.uses.set(j, constraintReg);
                                    }
                                } else {
                                    AsmInst mov = makeMov(blk, vreg.isFloat, constraintReg, realReg, "SimpleGlob use mov1");
                                    toInsertBefore.add(mov);
                                    inst.uses.set(j, constraintReg);
                                }
                                if (constraintReg.isFloat) {
                                    deadFp.add(LocalRegAllocator.reg2ind(constraintReg, constraintReg.isFloat));
                                } else {
                                    dead.add(LocalRegAllocator.reg2ind(constraintReg, constraintReg.isFloat));
                                }
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
                dead = new HashSet<>();
                deadFp = new HashSet<>();
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
                                boolean isDead = false;
                                if (constraintReg.isFloat) {
                                    isDead = deadFp.contains(LocalRegAllocator.reg2ind(constraintReg, constraintReg.isFloat));
                                } else {
                                    isDead = dead.contains(LocalRegAllocator.reg2ind(constraintReg, constraintReg.isFloat));
                                }
                                if (isDead) { 
                                    // constraintReg -->  realReg, 
                                    // 开头先保存到栈上，然后后面再加载出来
                                    List<AsmInst> tmp = new ArrayList<>();
                                    spillToStack(constraintReg, vreg, freeTemp, blk, tmp, false);
                                    toInsertAfter.addAll(0, tmp);
                                    tmp.clear();
                                    loadToReg(realReg, vreg, freeTemp, blk, toInsertAfter, false);
                                    inst.defs.set(j, constraintReg);
                                } else {
                                    // 如果有寄存器，有约束：不匹配的则生成move
                                    AsmInst mov = makeMov(blk, vreg.isFloat, realReg, constraintReg, "SimpleGlob use mov1");
                                    toInsertAfter.add(mov);
                                    inst.defs.set(j, constraintReg);
                                }
                                if (realReg.isFloat) {
                                    deadFp.add(LocalRegAllocator.reg2ind(realReg, realReg.isFloat));
                                } else {
                                    dead.add(LocalRegAllocator.reg2ind(realReg, realReg.isFloat));
                                }
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
        // assert !registerMapping.containsKey(vreg);
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
        // assert !registerMapping.containsKey(vreg);
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
