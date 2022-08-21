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
import backend.StackOperand;
import backend.VirtReg;
import backend.arm.inst.ConstrainRegInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.VMovInst;

public class SimpleGlobalAllocator {
    AsmFunc func;
    Map<VirtReg, AsmOperand> registerMapping = new HashMap<>();
    Map<VirtReg, Integer> stackMapping = new HashMap<>();
    Map<VirtReg, Integer> addressMapping = new HashMap<>(); // for alloca
    public Set<Integer> usedReg = new HashSet<>();
    public Set<Integer> usedVfpReg = new HashSet<>();
    public static Reg[] temps = {new Reg(Reg.Type.ip), new Reg(Reg.Type.lr)};

    public SimpleGlobalAllocator(AsmFunc f) {
        func = f;
    }

    public static AsmModule process(AsmModule m) {
        for (var f: m.funcs) {
            var g = new SimpleGlobalAllocator(f);
            g.doAnalysis();
        }
        return m;
    }

    public void doAnalysis() {
        // liveness analysis
        // build graph and color
        // fixup
        doFixUp();
    }

    // 使用IP和LR，修补任何没有分配到寄存器的值。
    public void doFixUp() {
        for (var blk: func) {
            int addedInstCount = 0;
            for (int i=0;i<blk.insts.size();i++) {
                var inst = blk.insts.get(i);
                List<AsmInst> toInsertBefore = new ArrayList<>();
                List<AsmInst> toInsertAfter = new ArrayList<>();
                Map<AsmOperand, VirtReg> inConstraints = null;
                Map<AsmOperand, VirtReg> outConstraints = null;
                if (inst instanceof ConstrainRegInst) {
                    inConstraints = new HashMap<>(((ConstrainRegInst)inst).getInConstraints());
                    outConstraints = new HashMap<>(((ConstrainRegInst)inst).getOutConstraints());
                }

                // 先处理use
                Set<Integer> usedTemp = new HashSet<>();
                for (int j=0;j<inst.uses.size();j++) {
                    var vreg_ = inst.uses.get(j);
                    if (!(vreg_ instanceof VirtReg)) continue;
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
                            loadToReg(constraintReg, vreg, usedTemp);
                            inst.uses.set(j, realReg);
                        } else {
                            AsmOperand to = loadToReg(null, vreg, usedTemp);
                            inst.uses.set(j, to);
                        }
                    }
                }

                for (int j=0;j<inst.defs.size();j++) {
                    var vreg_ = inst.defs.get(j);
                    if (!(vreg_ instanceof VirtReg)) continue;
                    var vreg = (VirtReg) vreg_;
                    var realReg = registerMapping.get(vreg);
                    var constraintReg = inConstraints != null ? inConstraints.get(vreg) : null;

                }
                // 再处理def
                // 没寄存器的：没约束先用临时寄存器，栈上分配临时位置，然后立刻store到栈上。
                //  有约束的直接从约束寄存器拿到栈上。
                // 有寄存器的: 没约束直接用，有约束可能生成move

                // 插入需要增加的指令
                blk.insts.addAll(i+1, toInsertAfter);
                i += toInsertAfter.size();

                blk.insts.addAll(i, toInsertBefore);
                i += toInsertBefore.size();

            }
        }
        // 最后处理用到的reg和设置func.usedCalleeSavedReg，并且插入相关的保存和恢复指令。
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
        LocalRegAllocator.insertSaveReg(func);
    }

    public static AsmOperand loadToReg(AsmOperand constraintReg, VirtReg vreg, Set<Integer> freeTemp) {
        if (constraintReg == null) {
            // get one from temp
        }
    }

    public static AsmOperand getRegFromConstraint(Map<AsmOperand, VirtReg> inConstraints, VirtReg vreg) {
        AsmOperand phyReg = null;
        if (inConstraints == null) {
            return null;
        }
        for (var ent: inConstraints.entrySet()) {
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

    
}
