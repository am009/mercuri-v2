package backend.arm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
import ssa.ds.Use;
import ssa.ds.Value;

public class Generator {
    // Contex 变量
    public AsmModule module;
    // public AssemblyBlock memCurrent; // 当前生成到的双向链表的末尾

    HashMap<BasicBlock, AsmBlock> bbMap; // 当前已经生成的基本块。
    HashMap<Value, AsmOperand> vregMap; // 为IR的temp值分配的虚拟寄存器
    HashMap<GlobalVariable, AsmGlobalVariable> gvMap;
    HashMap<Func, AsmFunc> funcMap;

    public static HashMap<Func, VfpCallingConvention> ccMap = new HashMap<>(); // CallingConvention解析结果

    public int vregInd = 0;

    public Generator(Module m) {
        module = new AsmModule(m.name);
        bbMap = new HashMap<>();
        vregMap = new HashMap<>();
        gvMap = new HashMap<>();
        funcMap = new HashMap<>();
    }

    public VfpCallingConvention getCC(Func f) {
        if (ccMap.containsKey(f)) {
            return ccMap.get(f);
        }
        VfpCallingConvention ret;
        List<ssa.ds.Type> params = f.argType.stream().map(pv -> pv.type).collect(Collectors.toList());
        if (f.isVariadic) {
            // 变参函数需要在调用处临时计算CallingConvention
            // ret = new BaseCallingConvention().resolve(params, f.retType);
            throw new UnsupportedOperationException();
        } else {
            ret = new VfpCallingConvention().resolve(params, f.retType);
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
                // 怎么加载到浮点寄存器里 TODO
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
        if (v.type.baseType.isFloat() && !v.type.isArray() && !v.type.isPointer) {
            ret.isFloat = true;
        }
        vregMap.put(v, ret);

        // 如果是参数且在内存中，则生成load指令
        // 使用CallingConvention的解析结果。
        if(v instanceof ParamValue) {
            var pv = (ParamValue)v;
            var index = func.ssaFunc.argType.indexOf(pv);
            assert index != -1;
            var cc = getCC(func.ssaFunc); // 有函数体的必然不是vararg的。
            var loc = cc.selfArg.get(index);
            assert loc instanceof StackOperand; // 其他的应该在前面就取到了vreg。
            if (loc instanceof StackOperand) {
                // 生成Load指令加载内存里的值到虚拟寄存器里。
                var load = new backend.arm.inst.LoadInst(abb, ret, loc);
                abb.insts.addAll(expandStackOperandLoadStore(load));
            }
        }

        // 增加注释便于Debug
        ret.comment = v.name;
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
        funcMap.put(f, asmFunc);
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

        var prologue = new Prologue(asmFunc.entry, asmFunc);
        asmFunc.entry.insts.add(prologue);
        // 把在寄存器里的参数也预先分配VReg。对于内存中的参数由getVReg生成load指令
        var cc = getCC(f);
        for (int i=0; i<f.argType.size();i++) {
            var pv = f.argType.get(i);
            var loc = cc.selfArg.get(i);
            VirtReg vreg;
            if (loc instanceof Reg) {
                vreg = getVReg();
                prologue.setConstraint(vreg, (Reg)loc);
                vregMap.put(pv, vreg);
                prologue.defs.add(vreg);
            } else if (loc instanceof VfpReg) {
                vreg = getVReg(); vreg.isFloat = true;
                prologue.setConstraint(vreg, (VfpReg)loc);
                vregMap.put(pv, vreg);
                prologue.defs.add(vreg);
            } else {
                // 留给convertValue处理
                assert loc instanceof StackOperand;
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
            j.comment = inst.toString();
            abb.insts.add(j);
            
            // 前驱后继维护
            abb.succ = Collections.singletonList(target);
            target.pred.add(abb);
            return;
        }

        if (inst_ instanceof RetInst) {
            var inst = (RetInst) inst_;
            var r = new backend.arm.inst.RetInst(abb, func);
            if (inst.oprands.size() > 0) {
                var cc = getCC(func.ssaFunc);
                var op = convertValue(inst.getOperand0(), func, abb);
                expandImm(op, r.uses, abb.insts, abb);
                op = r.uses.get(0);
                assert op instanceof VirtReg;
                if (cc.retReg instanceof VfpReg) {
                    r.setConstraint((VirtReg)op, (VfpReg)cc.retReg);
                } else if (cc.retReg instanceof Reg) {
                    r.setConstraint((VirtReg)op, (Reg)cc.retReg);
                } else { throw new UnsupportedOperationException(); }
            }
            abb.insts.add(r);
            r.comment = inst.toString();
            abb.succ = Collections.emptyList();
            return;
        }

        if (inst_ instanceof BranchInst) {
            var inst = (BranchInst) inst_;
            var cond = convertValue(inst.getOperand0(), func, abb);
            var tb = bbMap.get(((BasicBlockValue)inst.getOperand1()).b);
            var fb = bbMap.get(((BasicBlockValue)inst.getOperand2()).b);
            // generate cmp and bnz.
            abb.insts.addAll(expandCmpImm(new CMPInst(abb, cond, new NumImm(0))));
            if (tb == abb.next) { //  ==0 跳转到false
                abb.insts.add(new BrInst.Builder(abb, fb).addCond(Cond.EQ).addComment(inst.toString()).build());
            } else if (fb == abb.next) { // != 0跳转到true
                abb.insts.add(new BrInst.Builder(abb, tb).addCond(Cond.NE).addComment(inst.toString()).build());
            } else {
                abb.insts.add(new BrInst.Builder(abb, tb).addCond(Cond.NE).addComment(inst.toString()).build());
                abb.insts.add(new BrInst.Builder(abb, fb).build());
            }
            abb.succ = new ArrayList<>(List.of(tb, fb));
            return;
        }
        throw new RuntimeException("Unknown Terminator Inst.");
    }

    private void visitNonTermInst(AsmFunc func, Instruction inst_, AsmBlock abb) {
        if (inst_ instanceof AllocaInst) {
            var inst = (AllocaInst) inst_;
            long offset = func.sm.allocLocal(getSize(inst.ty));
            var bin = new BinOpInst(abb, BinaryOp.SUB, convertValue(inst, func, abb), new Reg(Reg.Type.fp), new NumImm(Math.toIntExact(offset)));
            bin.comment = inst.toString();
            abb.insts.addAll(expandBinOp(bin));
            return;
        }

        if (inst_ instanceof BinopInst) { // sub i32 0, 1
            var inst = (BinopInst) inst_;
            var op1 = convertValue(inst.oprands.get(0).value, func, abb);
            var op2 = convertValue(inst.oprands.get(1).value, func, abb);
            if (!inst.op.isBoolean()) {
                var bin = new BinOpInst(abb, inst.op, convertValue(inst, func, abb), op1, op2);
                bin.comment = inst.toString();
                abb.insts.addAll(expandBinOp(bin));
            } else { // LOG_GE 生成CMP; MOVW dst, #0; MOV<cond> dst #1;
                var cmp = new CMPInst(abb, op1, op2);
                cmp.comment = inst.toString();
                abb.insts.addAll(expandCmpImm(cmp));
                var dest = convertValue(inst, func, abb);
                abb.insts.addAll(MovInst.loadImm(abb, dest, new NumImm(0)));
                var mov = new MovInst(abb, MovInst.Ty.MOVW, dest, new NumImm(1));
                mov.cond = convertLogicOp(inst.op);
                mov.comment = inst.toString();
                abb.insts.add(mov);
            }
            return;
        }

        // CompUnit的变量/常量/函数声明的作用域从声明处开始到文件结尾。所以不会出现前面的函数调用后面的函数的找不到的情况
        if (inst_ instanceof CallInst) {
            var inst = (CallInst) inst_;
            backend.arm.inst.CallInst call;
            // 解析调用约定
            var ssaFunc = ((FuncValue)inst.oprands.get(0).value).func;
            // var targetAsmFunc = funcMap.get(ssaFunc);
            // assert targetAsmFunc != null; // sylib的函数目前没有对应的AsmFunc
            if(!ssaFunc.isVariadic) {
                var cc = getCC(ssaFunc);
                call = new backend.arm.inst.CallInst(abb, new LabelImm(ssaFunc.name), cc);
                for (int i=0;i<ssaFunc.argType.size();i++) {
                    var loc = cc.callParam.get(i);
                    var op = convertValue(inst.oprands.get(i+1).value, func, abb);
                    processCallArg(call, op, loc, abb);
                }
            } else { // isVariadic
                List<ssa.ds.Type> params = ssaFunc.argType.stream().map(pv -> pv.type).collect(Collectors.toList());
                var cc = new BaseCallingConvention().resolve(params, ssaFunc.retType);
                call = new backend.arm.inst.CallInst(abb, new LabelImm(ssaFunc.name), cc);
                for (int i=0;i<inst.oprands.size()-1;i++) {
                    var val = inst.oprands.get(i+1).value;
                    if (i >= ssaFunc.argType.size()) { // 是额外的参数
                        cc.addParam(val.type);
                    }
                    var loc = cc.callParam.get(i);
                    var op = convertValue(val, func, abb);
                    processCallArg(call, op, loc, abb);
                }
            }
            // 更新def
            var retReg = call.cc.getRetReg();
            if (retReg != null) {
                var ret = convertValue(inst, func, abb);
                call.defs.add(ret);
                assert ret instanceof VirtReg;
                if (retReg instanceof Reg) {
                    call.setConstraint((VirtReg)ret, (Reg)retReg, false);
                } else if (retReg instanceof VfpReg) {
                    call.setConstraint((VirtReg)ret, (VfpReg)retReg, false);
                } else {throw new UnsupportedOperationException();}
                
            }
            // 更新当前函数需要的最大函数调用栈空间的大小
            func.sm.preserveArgSize(call.cc.getStackSize());
            call.comment = inst.toString();
            abb.insts.add(call);
            return;
        }

        if (inst_ instanceof CastInst) {
            var inst = (CastInst) inst_;
            // __aeabi_i2f
            // __aeabi_i2d
            
            return;
        }

        if (inst_ instanceof GetElementPtr) { // getelementptr [4 x [2 x i32]], [4 x [2 x i32]]* %b_1, i32 0, i32 0
            var inst = (GetElementPtr) inst_;
            var addr = convertValue(inst.oprands.get(0).value, func, abb);
            var nums = inst.oprands.subList(1, inst.oprands.size());
            // var nums = inst.oprands.subList(1, inst.oprands.size()).stream().map(u -> ((Integer)((ConstantValue)u.value).val)).collect(Collectors.toList());
            // long offset = calcGep(getSize(inst.base), new ArrayList<>(inst.base.dims), nums);
            calcGep(func, abb, inst, addr, nums);
            // if (offset != 0) {
            //     var bin = new BinOpInst(abb, BinaryOp.ADD, convertValue(inst, func, abb), addr, new NumImm(Math.toIntExact(offset)));
            //     bin.comment = inst.toString();
            //     abb.insts.addAll(expandBinOp(bin));
            // } else {
            //     vregMap.put(inst, addr);
            // }
            return;
        }

        if (inst_ instanceof LoadInst) { // load i32, i32* %a_0
            var inst = (LoadInst) inst_;
            var addr = convertValue(inst.oprands.get(0).value, func, abb);
            var asm = new backend.arm.inst.LoadInst(abb, convertValue(inst, func, abb), addr);
            asm.comment = inst.toString();
            abb.insts.addAll(expandInstImm(asm));
            return;
        }

        if (inst_ instanceof StoreInst) { // store i32 10, i32* %a_0
            var inst = (StoreInst) inst_;
            var val = convertValue(inst.oprands.get(0).value, func, abb);
            var addr = convertValue(inst.oprands.get(1).value, func, abb);
            var sto = new backend.arm.inst.StoreInst(abb, val, addr);
            sto.comment = inst.toString();
            abb.insts.addAll(expandInstImm(sto));
            return;
        }
        throw new RuntimeException("Unknown Terminator Inst.");
    }

    private void calcGep(AsmFunc func, AsmBlock abb, GetElementPtr inst, AsmOperand addr, List<Use> ops) {
        var baseSize = getSize(inst.base);
        ArrayList<Integer> dims;
        if (inst.base.dims != null) {
            dims = new ArrayList<>(inst.base.dims);
        } else {
            dims = new ArrayList<>();
        }
        
        AsmOperand current = addr;
        long offset = 0;
        for (var use: ops) {
            if (use.value instanceof ConstantValue) {
                int num = (Integer)((ConstantValue)use.value).val;
                assert baseSize != Long.MIN_VALUE;
                offset += baseSize * num;
                if (dims.size() > 0) {
                    baseSize = baseSize / dims.remove(0);
                } else {
                    // Set as invalid
                    baseSize = Long.MIN_VALUE;
                }
            } else {
                if (offset != 0) {
                    current = GepMakeAdd(current, new NumImm(Math.toIntExact(offset)), abb, inst.toString());
                    offset = 0;
                }
                String comment = inst.toString()+" ("+use.value.name+")";

                var target = getVReg();
                var mul = new BinOpInst(abb, BinaryOp.MUL, target, convertValue(use.value, func, abb), new NumImm(Math.toIntExact(baseSize)));
                mul.comment = comment;
                abb.insts.addAll(expandBinOp(mul));
                if (dims.size() > 0) {
                    baseSize = baseSize / dims.remove(0);
                } else {
                    // Set as invalid
                    baseSize = Long.MIN_VALUE;
                }

                current = GepMakeAdd(current, target, abb, comment);
            }
        }
        if (offset != 0) {
            current = GepMakeAdd(current, new NumImm(Math.toIntExact(offset)), abb, inst.toString());
            offset = 0;
        }
        // var target = convertValue(inst, func, abb);
        vregMap.put(inst, current);
    }

    private AsmOperand GepMakeAdd(AsmOperand prev, AsmOperand offset, AsmBlock abb, String comment) {
        var target = getVReg();
        var bin = new BinOpInst(abb, BinaryOp.ADD, target, prev, offset);
        bin.comment = comment;
        abb.insts.addAll(expandBinOp(bin));
        return target;
    }

    private void processCallArg(backend.arm.inst.CallInst call, AsmOperand op, AsmOperand loc, AsmBlock abb) {
        // 如果是常量，转换一下
        if (op instanceof Imm) {
            var tmp = getVReg();
            abb.insts.addAll(MovInst.loadImm(abb, tmp, (Imm)op));
            op = tmp;
        }
        assert op instanceof VirtReg;
        var vreg = (VirtReg) op;
        if (loc instanceof Reg) { // 维护寄存器分配约束
            call.setConstraint(vreg, (Reg)loc, true);
            call.uses.add(vreg);
        } else if (loc instanceof VfpReg) { // 维护寄存器分配约束
            call.setConstraint(vreg, (VfpReg)loc, true);
            // 维护use
            call.uses.add(vreg);
        } else if (loc instanceof StackOperand) { // 对内存中的参数生成store
            var store = new backend.arm.inst.StoreInst(abb, vreg, loc);
            store.comment = "store argument to stack: "+vreg.comment;
            abb.insts.addAll(expandStackOperandLoadStore(store));
        }
    }

    private Cond convertLogicOp(BinaryOp op) {
        switch (op) {
            case LOG_EQ: return Cond.EQ;
            case LOG_GE: return Cond.GE;
            case LOG_GT: return Cond.GT;
            case LOG_LE: return Cond.LE;
            case LOG_LT: return Cond.LT;
            case LOG_NEQ: return Cond.NE;
            default:
                throw new UnsupportedOperationException();
        }
    }

    // 检查第二个参数StackOperand是否满足要求，不满足则展开为多个指令
    // Load dst, addr
    // Store val, addr
    private List<AsmInst> expandStackOperandLoadStore(AsmInst inst) {
        List<AsmInst> ret = new ArrayList<>();
        List<AsmOperand> newuse = new ArrayList<>();
        // expandImm(inst.uses.get(0), newuse, ret, inst.parent);
        // expandStackOperand(inst.uses.get(1), newuse, ret, inst.parent);
        assert inst instanceof backend.arm.inst.LoadInst || inst instanceof backend.arm.inst.StoreInst;
        if (inst instanceof backend.arm.inst.StoreInst) {
            newuse.add(inst.uses.get(0));
            expandStackOperand(inst.uses.get(1), newuse, ret, inst.parent);
        } else if (inst instanceof backend.arm.inst.LoadInst) {
            expandStackOperand(inst.uses.get(0), newuse, ret, inst.parent);
        }
        inst.uses = newuse;
        ret.add(inst);
        return ret;
    }

    private List<AsmInst> expandCmpImm(CMPInst inst) {
        List<AsmInst> ret = new ArrayList<>();
        List<AsmOperand> newuse = new ArrayList<>();
        expandImm(inst.uses.get(0), newuse, ret, inst.parent);
        expandOperand2(inst.uses.get(1), newuse, ret, inst.parent);
        inst.uses = newuse;
        ret.add(inst);
        return ret;
    }

    private List<AsmInst> expandInstImm(AsmInst inst) {
        List<AsmInst> ret = new ArrayList<>();
        List<AsmOperand> newuse = new ArrayList<>();
        for(var op: inst.uses) {
            expandImm(op, newuse, ret, inst.parent);
        }
        inst.uses = newuse;
        ret.add(inst);
        return ret;
    }

    private void expandImm(AsmOperand op, List<AsmOperand> newOps, List<AsmInst> insts, AsmBlock p) {
        if (op instanceof Imm) {
            AsmOperand tmp;
            tmp = getVReg();
            insts.addAll(MovInst.loadImm(p, tmp, (Imm)op));
            newOps.add(tmp);
        } else {
            // 不变
            newOps.add(op);
        }
    }

    private void expandStackOperand(AsmOperand op, List<AsmOperand> newOps, List<AsmInst> insts,
                                        AsmBlock p) {
        if (op instanceof StackOperand) {
            var so = (StackOperand) op;
            // – 4095 to +4095 then OK。范围暂时放窄一些，等测例都过了再改回来
            if (so.offset <= 4070 && so.offset >= -4070) {
                newOps.add(so);
                return;
            }
            // 超出了4095，则add sub的imm字段也放不下
            AsmOperand tmp= getVReg();
            var tmp2 = getVReg();
            assert so.type != StackOperand.Type.SPILL; // 指令选择阶段不能分配spill空间。
            if (so.type == StackOperand.Type.SELF_ARG) {
                insts.addAll(MovInst.loadImm(p, tmp, new NumImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.LOCAL) {
                insts.addAll(MovInst.loadImm(p, tmp, new NumImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.SUB, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.CALL_PARAM) {
                insts.addAll(MovInst.loadImm(p, tmp, new NumImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.sp), tmp));
            } else {throw new UnsupportedOperationException();}

            newOps.add(tmp2);
        } else {
            // 不变
            newOps.add(op);
        }
    }

    // Flexible Operand 2 can be Imm8m
    private void expandOperand2(AsmOperand op, List<AsmOperand> newOps, List<AsmInst> insts, AsmBlock p) {
        if (op instanceof NumImm) {
            var immop = (NumImm) op;
            // within 8bit then OK
            if (immop.highestOneBit() < 255) {
                newOps.add(immop);
                return;
            }
            AsmOperand tmp;
            tmp = getVReg();
            insts.addAll(MovInst.loadImm(p, tmp, immop));
            newOps.add(tmp);
        } else {
            // 不变
            newOps.add(op);
        }
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

    // 如果立即数字段无法放下，则借助IP寄存器额外生成MOV指令。详见BinOpInst注释
    public static List<AsmInst> expandBinOp(BinOpInst bin) {
        List<AsmInst> ret = new ArrayList<>();
        var op1 = bin.uses.get(0);
        var op2 = bin.uses.get(1);
        if (op1 instanceof Imm) { // 如果是label也要展开
            var tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
            ret.addAll(MovInst.loadImm(bin.parent, tmp, ((Imm)op1)));
            op1 = tmp;
        }
        if (op2 instanceof Imm) {
            // 0-4095的#imm12仅在Thumb模式下有。
            if (op2 instanceof NumImm && (bin.op == BinaryOp.ADD || bin.op == BinaryOp.SUB) && ((NumImm) op2).highestOneBit() < 255) {
                // OK to use imm
            } else {
                var tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
                ret.addAll(MovInst.loadImm(bin.parent, tmp, ((NumImm)op2)));
                op2 = tmp;
            }
        }
        bin.uses = new ArrayList<>(List.of(op1, op2));
        ret.add(bin);
        return ret;
    }

    // 检查第二个参数StackOperand是否满足要求，不满足则展开为多个指令
    // 给寄存器分配使用的公开版本
    public static List<AsmInst> expandStackOperandLoadStoreIP(AsmInst inst) {
        List<AsmInst> ret = new ArrayList<>();
        List<AsmOperand> newuse = new ArrayList<>();
        assert inst instanceof backend.arm.inst.LoadInst || inst instanceof backend.arm.inst.StoreInst;
        if (inst instanceof backend.arm.inst.StoreInst) {
            assert inst.uses.get(0) instanceof Reg || inst.uses.get(0) instanceof VfpReg;
            newuse.add(inst.uses.get(0));
            expandStackOperandIP(inst.uses.get(1)/*可能的StackOperand*/, inst.uses.get(0), newuse, ret, inst.parent);
        } else if (inst instanceof backend.arm.inst.LoadInst) {
            assert inst.defs.get(0) instanceof Reg || inst.defs.get(0) instanceof VfpReg;
            expandStackOperandIP(inst.uses.get(0), inst.defs.get(0), newuse, ret, inst.parent);
        }
        inst.uses = newuse;
        ret.add(inst);
        return ret;
    }

    // 使用IP作为临时寄存器
    public static void expandStackOperandIP(AsmOperand op, AsmOperand target, List<AsmOperand> newOps, List<AsmInst> insts,
                                        AsmBlock p) {
        if (op instanceof StackOperand) {
            var so = (StackOperand) op;
            // – 4095 to +4095 then OK。但由于后面要加减一些？，所以范围放窄一些？TODO
            if (so.offset <= 4070 && so.offset >= -4070) {
                newOps.add(so);
                return;
            }
            // 超出了4095，则add sub的imm字段也放不下
            AsmOperand tmp= new Reg(Reg.Type.ip);
            var tmp2 = target;
            if (so.type == StackOperand.Type.SELF_ARG) {
                insts.addAll(MovInst.loadImm(p, tmp, new NumImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.LOCAL || so.type == StackOperand.Type.SPILL) {
                insts.addAll(MovInst.loadImm(p, tmp, new NumImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.SUB, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.CALL_PARAM) {
                insts.addAll(MovInst.loadImm(p, tmp, new NumImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.sp), tmp));
            } else {throw new UnsupportedOperationException();}

            newOps.add(tmp2);
        } else {
            // 不变
            newOps.add(op);
        }
    }

}
