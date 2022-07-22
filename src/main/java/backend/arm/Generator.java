package backend.arm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import backend.AsmBlock;
import backend.AsmFunc;
import backend.AsmGlobalVariable;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.StackOperand;
import backend.VirtReg;
import backend.arm.inst.BinOpInst;
import backend.arm.inst.BrInst;
import backend.arm.inst.CMPInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.Prologue;
import dst.ds.BinaryOp;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BasicBlockValue;
import ssa.ds.BinopInst;
import ssa.ds.BranchInst;
import ssa.ds.CallInst;
import ssa.ds.CastInst;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.FuncValue;
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
import ssa.ds.Type;
import ssa.ds.Value;

public class Generator {
    // Contex 变量
    public AsmModule module;
    // public AssemblyBlock memCurrent; // 当前生成到的双向链表的末尾

    HashMap<BasicBlock, AsmBlock> bbMap; // 当前已经生成的基本块。
    HashMap<Value, VirtReg> vregMap; // 为IR的temp值分配的虚拟寄存器
    HashMap<GlobalVariable, AsmGlobalVariable> gvMap;

    // TODO 之后寄存器分配也要用到，放在哪里合适？
    public static HashMap<Func, CallingConvention> ccMap = new HashMap<>(); // CallingConvention解析结果

    public int vregInd = 0;

    public Generator(Module m) {
        module = new AsmModule(m.name);
        bbMap = new HashMap<>();
        vregMap = new HashMap<>();
        gvMap = new HashMap<>();
    }

    public CallingConvention getCC(Func f) {
        if (ccMap.containsKey(f)) {
            return ccMap.get(f);
        }
        CallingConvention ret;
        if (f.isVariadic) {
            ret = CallingConvention.resolveBase(f.argType);
        } else {
            ret = CallingConvention.resolveVFP(f.argType);
        }
        ccMap.put(f, ret);
        return ret;
    }

    // 对指令返回的值分配Vreg
    // 对其他Value进行转换
    // 该函数还需要为内存中的参数生成必要的Load指令
    public AsmOperand convertValue(Value v, AsmFunc func, AsmBlock abb) {
        if (vregMap.containsKey(v)) {
            return vregMap.get(v);
        }

        // BasicBlovkValue，FuncValue，在对应的指令预先判断处理。
        if (v instanceof BasicBlockValue || v instanceof FuncValue) {
            throw new UnsupportedOperationException();
        }

        // 如果是ConstantValue则需要转为Imm
        if (v instanceof ConstantValue) {
            var cv = (ConstantValue) v;
            assert !cv.isArray();
            if (cv.val instanceof Float) {
                return new NumImm(Float.floatToRawIntBits((Float)(cv.val)));
            } else if (cv.val instanceof Integer) {
                return new NumImm((Integer) cv.val);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        // IR那边GlobalVariable直接引用也代表地址，所以不用Load
        if (v instanceof GlobalVariable) {
            return gvMap.get(v).imm;
        }

        var ret = new VirtReg(vregInd++);
        vregMap.put(v, ret);

        // 如果是参数且在内存中，则生成load指令
        // 使用CallingConvention的解析结果。
        if(v instanceof ParamValue) {
            var cc = getCC(func.ssaFunc);
            var loc = cc.getLoc((ParamValue)v);
            if (loc instanceof StackOperand) {
                // 生成Load指令加载内存里的值到虚拟寄存器里。
                abb.insts.add(new backend.arm.inst.LoadInst(abb, ret, loc));
            }
        }

        return ret;
    }

    // 使用临时寄存器的场景
    VirtReg getVReg() {
        return new VirtReg(vregInd++);
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
        agv.size = getSize(gv.varType);
        // 加入module
        if (gv.init != null) {
            module.dataGlobs.add(agv);
        } else {
            module.bssGlobs.add(agv);
        }
        gvMap.put(gv, agv);
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
        asmFunc.paramPreAlloc = new ArrayList<>();
        var cc = getCC(f);
        for (var pv: f.argType) {
            var loc = cc.getLoc(pv);
            if (loc instanceof Reg) {
                var vreg = getVReg();
                vregMap.put(pv, vreg);
                asmFunc.paramPreAlloc.add(Map.entry(((Reg)loc), vreg));
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
                r.uses.add(convertValue(inst.getOperand0(), func, abb));
            }

            abb.succ = Collections.emptyList();
            return;
        }

        if (inst_ instanceof BranchInst) {
            var inst = (BranchInst) inst_;
            var cond = convertValue(inst.getOperand0(), func, abb);
            var tb = bbMap.get(((BasicBlockValue)inst.getOperand1()).b);
            var fb = bbMap.get(((BasicBlockValue)inst.getOperand2()).b);
            // generate cmp and bnz.
            abb.insts.add(new CMPInst(abb, cond, new NumImm(0)));
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
            long offset = func.sm.allocLocal(getSize(inst.ty));
            var bin = new BinOpInst(abb, BinaryOp.SUB, convertValue(inst, func, abb), new Reg(Reg.Type.fp), new NumImm(Math.toIntExact(offset)));
            abb.insts.addAll(expandBinOp(bin));
            return;
        }

        if (inst_ instanceof BinopInst) { // sub i32 0, 1
            var inst = (BinopInst) inst_;
            var op1 = convertValue(inst.oprands.get(0).value, func, abb);
            var op2 = convertValue(inst.oprands.get(1).value, func, abb);
            var bin = new BinOpInst(abb, inst.op, convertValue(inst, func, abb), op1, op2);
            abb.insts.addAll(expandInstImm(bin));
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

        if (inst_ instanceof LoadInst) { // load i32, i32* %a_0
            var inst = (LoadInst) inst_;
            var addr = convertValue(inst.oprands.get(0).value, func, abb);
            var sto = new backend.arm.inst.LoadInst(abb, convertValue(inst, func, abb), addr);
            abb.insts.addAll(expandInstImm(sto));
            return;
        }

        if (inst_ instanceof StoreInst) { // store i32 10, i32* %a_0
            var inst = (StoreInst) inst_;
            var val = convertValue(inst.oprands.get(0).value, func, abb);
            var addr = convertValue(inst.oprands.get(1).value, func, abb);
            var sto = new backend.arm.inst.StoreInst(abb, val, addr);
            abb.insts.addAll(expandInstImm(sto));
            return;
        }
        throw new RuntimeException("Unknown Terminator Inst.");
    }

    private List<AsmInst> expandInstImm(AsmInst inst) {
        List<AsmInst> ret = new ArrayList<>();
        List<AsmOperand> newuse = new ArrayList<>();
        boolean ipUsed = false;
        for(var op: inst.uses) {
            if (op instanceof NumImm) {
                AsmOperand tmp;
                if (ipUsed) {
                    tmp = getVReg();
                } else {
                    tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
                    ipUsed = true;
                }
                ret.addAll(MovInst.loadImm(inst.parent, tmp, ((NumImm)op)));
                newuse.add(tmp);
            } else {
                // 不变
                newuse.add(op);
            }
        }
        inst.uses = newuse;
        ret.add(inst);
        return ret;
    }

    private long getSize(Type ty) {
        long size;
        if (! ty.isArray()) {
            size = ty.baseType.getByteSize();
            assert size == 4;
            return size;
        } else {
            long s = ty.baseType.getByteSize();
            for (int d: ty.dims) {
                s = s * d;
            }
            return s;
        }
    }

    // 如果立即数字段无法放下则额外生成MOV32。详见BinOpInst注释
    public List<AsmInst> expandBinOp(BinOpInst bin) {
        List<AsmInst> ret = new ArrayList<>();
        var op1 = bin.uses.get(0);
        var op2 = bin.uses.get(1);
        if (op1 instanceof NumImm) {
            var tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
            ret.addAll(MovInst.loadImm(bin.parent, tmp, ((NumImm)op1)));
            op1 = tmp;
        }
        if (op2 instanceof NumImm) {
            var immop2 = (NumImm) op2;
            if ((bin.op == BinaryOp.ADD || bin.op == BinaryOp.SUB) && immop2.highestOneBit() < 4095) {
                // OK to use imm
            } else {
                var tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
                ret.addAll(MovInst.loadImm(bin.parent, tmp, ((NumImm)op2)));
                op2 = tmp;
            }
        }
        bin.uses = List.of(op1, op2);
        ret.add(bin);
        return ret;
    }

}
