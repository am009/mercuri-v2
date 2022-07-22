package backend.arm;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmGlobalVariable;
import backend.AsmModule;
import backend.VirtReg;
import backend.arm.inst.BrInst;
import backend.arm.inst.CMPInst;
import backend.arm.inst.Prologue;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BasicBlockValue;
import ssa.ds.BinopInst;
import ssa.ds.BranchInst;
import ssa.ds.CallInst;
import ssa.ds.CastInst;
import ssa.ds.Func;
import ssa.ds.GetElementPtr;
import ssa.ds.GlobalVariable;
import ssa.ds.Instruction;
import ssa.ds.JumpInst;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.ParamValue;
import ssa.ds.RetInst;
import ssa.ds.StoreInst;
import ssa.ds.TerminatorInst;
import ssa.ds.Value;

public class Generator {
    // Contex 变量
    public AsmModule module;
    // public AssemblyBlock memCurrent; // 当前生成到的双向链表的末尾

    HashMap<BasicBlock, AsmBlock> bbMap; // 当前已经生成的基本块。
    HashMap<Value, VirtReg> vregMap;
    public int vregInd = 0;

    public VirtReg getVReg(Value v, AsmFunc f) {
        if (vregMap.containsKey(v)) {
            return vregMap.get(v);
        }

        // 如果是参数且在内存中，则生成load指令
        // if(v instanceof ParamValue && TODO) {
        //     // Load positive SP space
        // }

        var ret = new VirtReg(vregInd++);
        vregMap.put(v, ret);
        return ret;
    }

    public Generator(Module m) {
        this.module = new AsmModule(m.name);
        this.bbMap = new HashMap<>();
    }

    public static AsmModule process(Module m) {
        var g = new Generator(m);
        g.visitModule(m);
        return g.module;
    }

    private void visitModule(Module m) {
        m.globs.forEach(gv -> this.visitGlobVar(gv));
        m.funcs.forEach(f -> this.visitFunc(f));
    }

    private void visitGlobVar(GlobalVariable gv) {
        assert !gv.varType.isPointer;
        var agv = new AsmGlobalVariable(gv);
        // set size
        if (! gv.varType.isArray()) {
            agv.size = gv.varType.baseType.getByteSize();
            assert agv.size == 4;
        } else {
            long s = gv.varType.baseType.getByteSize();
            for (int d: gv.varType.dims) {
                s = s * d;
            }
            agv.size = s;
        }
        // 加入module
        if (gv.init != null) {
            module.dataGlobs.add(agv);
        } else {
            module.bssGlobs.add(agv);
        }
    }

    private void visitFunc(Func f) {
        AsmFunc asmFunc = new AsmFunc(f);
        module.funcs.add(asmFunc);
        // 当前生成到的双向链表的末尾
        AsmBlock prev = null;
        // 对应生成AsmBlock并放到Map里。
        // 前驱后继关系推迟到跳转指令那边做。
        for(BasicBlock bb: f.bbs) {
            var abb = new AsmBlock(getBlockLabel(f, bb));
            if (asmFunc.entry == null) { // 初始化
                asmFunc.entry = abb;
            } else { // 插入链表
                prev.next = abb;
                abb.prev = prev;
            }
            asmFunc.bbs.add(abb);
            prev = abb;
            bbMap.put(bb, abb);
        }

        asmFunc.entry.insts.add(new Prologue(asmFunc.entry));
        // 把在寄存器里的参数也预先分配VReg。对于内存中的参数由getVReg生成load指令
        asmFunc.regPreAlloc = new HashMap<>();
        for (int i=0;i<f.argType.size();i++) {
            if (i < 4) {
                asmFunc.regPreAlloc.put(f.argType.get(i), getVReg(f.argType.get(i), asmFunc));
            }
        }

        for(BasicBlock bb: f.bbs) {
            visitBlock(asmFunc, bb, bbMap.get(bb));
        }
    }

    private String getBlockLabel(Func f, BasicBlock bb) {
        return f.name+"_"+bb.label;
    }

    private void visitBlock(AsmFunc func, BasicBlock bb, AsmBlock abb) {
        for (var inst: bb.insts) {
            if (inst instanceof TerminatorInst) {
                visitTermInst(func, inst, abb);
            } else {
                visitNonTermInst(func, inst, abb);
            }
        }
    }

    // 跳转指令的生成同时要维护前驱后继关系。
    private void visitTermInst(AsmFunc func, Instruction inst_, AsmBlock abb) {
        assert abb.succ == null || abb.succ.size() == 0;
        if (inst_ instanceof JumpInst) {
            var inst = (JumpInst) inst_;
            var targetBB = ((BasicBlockValue) inst.getOperand0()).b;
            var target = bbMap.get(targetBB);
            var j = new BrInst.Builder(abb, target).build();
            abb.insts.add(j);
            
            // 前驱后继维护
            abb.succ = Collections.singletonList(target);
            target.pred.add(abb);
            return;
        }

        if (inst_ instanceof RetInst) {
            var inst = (RetInst) inst_;
            var r = new backend.arm.inst.RetInst(abb);
            abb.insts.add(r);
            if (inst.oprands.size() > 0) {
                r.uses.add(getVReg(inst.getOperand0(), func));
            }

            abb.succ = Collections.emptyList();
            return;
        }

        if (inst_ instanceof BranchInst) {
            var inst = (BranchInst) inst_;
            var cond = getVReg(inst.getOperand0(), func);
            var tb = bbMap.get(((BasicBlockValue)inst.getOperand1()).b);
            var fb = bbMap.get(((BasicBlockValue)inst.getOperand2()).b);
            // generate cmp and bnz.
            abb.insts.add(new CMPInst(abb, cond, new ArmImm(0, 8)));
            if (tb == abb.next) { //  ==0 跳转到false
                abb.insts.add(new BrInst.Builder(abb, fb).addCond(Cond.EQ).build());
            } else if (fb == abb.next) { // != 0跳转到true
                abb.insts.add(new BrInst.Builder(abb, tb).addCond(Cond.NE).build());
            } else {
                abb.insts.add(new BrInst.Builder(abb, tb).addCond(Cond.NE).build());
                abb.insts.add(new BrInst.Builder(abb, fb).build());
            }
            abb.succ = List.of(tb, fb);
            return;
        }
        throw new RuntimeException("Unknown Terminator Inst.");
    }

    private void visitNonTermInst(AsmFunc func, Instruction inst_, AsmBlock abb) {
        if (inst_ instanceof AllocaInst) {
            var inst = (AllocaInst) inst_;

            return;
        }

        if (inst_ instanceof BinopInst) {
            var inst = (BinopInst) inst_;
            return;
        }

        if (inst_ instanceof CallInst) {
            var inst = (CallInst) inst_;
            return;
        }

        if (inst_ instanceof CastInst) {
            var inst = (CastInst) inst_;
            return;
        }

        if (inst_ instanceof GetElementPtr) {
            var inst = (GetElementPtr) inst_;
            return;
        }

        if (inst_ instanceof LoadInst) {
            var inst = (LoadInst) inst_;
            return;
        }

        if (inst_ instanceof StoreInst) {
            var inst = (StoreInst) inst_;
            return;
        }
        throw new RuntimeException("Unknown Terminator Inst.");
    }

}
