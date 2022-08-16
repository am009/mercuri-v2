package backend.arm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
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
import backend.arm.inst.FBinOpInst;
import backend.arm.inst.FCMPInst;
import backend.arm.inst.MovInst;
import backend.arm.inst.Prologue;
import backend.arm.inst.VCVTInst;
import backend.arm.inst.VLDRInst;
import backend.arm.inst.VMRS;
import backend.arm.inst.VMovInst;
import backend.arm.inst.VSTRInst;
import dst.ds.BinaryOp;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BasicBlockValue;
import ssa.ds.BinopInst;
import ssa.ds.BranchInst;
import ssa.ds.CallInst;
import ssa.ds.CastInst;
import ssa.ds.CastOp;
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
import ssa.ds.PhiInst;
import ssa.ds.PrimitiveTypeTag;
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

    // 仅当处理phi指令的时候的部分情况需要 beforeJump = true
    public AsmOperand convertValue(Value v, AsmFunc func, AsmBlock abb) {
        return convertValue(v, func, abb, false);
    }

    // 对指令返回的值分配Vreg
    // 对其他Value进行转换
    // 该函数还需要为内存中的参数生成必要的Load指令
    public AsmOperand convertValue(Value v, AsmFunc func, AsmBlock abb, boolean beforeJump) {
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
                return new FloatImm((Float)(cv.val));
            } else if (cv.val instanceof Integer) {
                return new IntImm((Integer) cv.val);
            } else {throw new UnsupportedOperationException();}
        }

        // IR那边GlobalVariable直接引用也代表地址，所以不用Load
        if (v instanceof GlobalVariable) {
            return gvMap.get(v).imm;
        }

        var ret = new VirtReg(vregInd++, v.type.isBaseFloat());
        if (v.type.isBaseFloat()) {
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
                if (pv.type.isBaseFloat()) {
                    var load = new VLDRInst(abb, ret, loc);
                    if (beforeJump) {
                        abb.addAllBeforeJump(expandStackOperandLoadStore(load));
                    } else {
                        abb.insts.addAll(expandStackOperandLoadStore(load));
                    }
                } else {
                    var load = new backend.arm.inst.LoadInst(abb, ret, loc);
                    if (beforeJump) {
                        abb.addAllBeforeJump(expandStackOperandLoadStore(load));
                    } else {
                        abb.insts.addAll(expandStackOperandLoadStore(load));
                    }
                }
                
            }
        }

        // 增加注释便于Debug
        ret.comment = v.name;
        return ret;
    }

    // 使用临时寄存器的场景
    VirtReg getVReg(boolean isFloat) {
        return new VirtReg(vregInd++, isFloat);
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
        agv.size = gv.varType.getSize();
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
                vreg = getVReg(false);
                prologue.setConstraint(vreg, (Reg)loc);
                vregMap.put(pv, vreg);
                prologue.defs.add(vreg);
            } else if (loc instanceof VfpReg) {
                vreg = getVReg(true);
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

        // phi指令在基本块的前驱后继关系构建完成后处理
        for (BasicBlock bb: f.bbs) {
            if (bb.hasPhi()) { // phi指令需要批量处理
                visitPhis(asmFunc, bb.getPhis(), bbMap.get(bb));
            }
        }
    }

    private String getBlockLabel(Func f, BasicBlock bb) {
        return f.name+"_"+bb.label;
    }

    private void visitBlock(AsmFunc func, BasicBlock bb, AsmBlock abb) {
        for (var inst: bb.insts) {
            if (inst instanceof PhiInst) {
                continue;
            }
            if (inst instanceof TerminatorInst) {
                visitTermInst(func, inst, abb);
            } else {
                visitNonTermInst(func, inst, abb);
            }
        }
    }

    private void visitPhis(AsmFunc func, List<PhiInst> phis, AsmBlock abb) {
        var preds = abb.pred;
        assert preds.size() != 0;
        // 已经split了critical edge，则要么仅有一个predcessor，要么每个predcessor仅有一个successor。
        if (preds.size() == 1) { // mov放到当前基本块
            List<Map.Entry<AsmOperand, AsmOperand>> parallelMovs = new ArrayList<>();
            for (var phi: phis) {
                var target = convertValue(phi, func, abb);
                if (target.comment == null) {
                    target.comment = phi.toValueString();
                }
                assert phi.oprands.size() == 1;
                for (var use:phi.oprands) {
                    var from = convertValue(use.value, func, abb);
                    if (from.comment == null) {
                        from.comment = use.value.toValueString();
                    }
                    parallelMovs.add(Map.entry(target, from));
                }
            }
            makeParallemMovs(abb, parallelMovs);
        } else {
            int size = preds.size();
            for (var pred: preds) {
                if (pred.succ.size() != 1) {
                    throw new RuntimeException(String.format("Unsplit critical edge: %s to %s.", pred.label, abb.label));
                }
                
                List<Map.Entry<AsmOperand, AsmOperand>> parallelMovs = new ArrayList<>();
                // mov放到pred的基本块
                for (var phi: phis) {
                    var target = convertValue(phi, func, abb);
                    if (target.comment == null) {
                        target.comment = phi.toValueString();
                    }
                    assert size == phi.oprands.size();
                    boolean found = false;
                    for (int i=0;i<size;i++) {
                        var fromBb = ((BasicBlockValue)phi.preds.get(i).value).b;
                        if (bbMap.get(fromBb) != pred) {
                            continue;
                        }
                        assert found == false;
                        found = true;
                        // 找到了对应的值
                        // 注意convertValue可能会生成load指令，要生成到pred结尾。
                        var from = convertValue(phi.oprands.get(i).value, func, pred, true);
                        if (from.comment == null) {
                            from.comment = phi.oprands.get(i).value.toValueString();
                        }
                        parallelMovs.add(Map.entry(target, from));
                    }
                    assert found == true;
                }
                makeParallemMovs(pred, parallelMovs);
            }
        }
    }

    // parallelMovs里的entry是从右移动到左。
    private void makeParallemMovs(AsmBlock abb, List<Entry<AsmOperand, AsmOperand>> parallelMovs) {
        Set<AsmOperand> killed = new HashSet<>();
        List<AsmInst> toAdd = new ArrayList<>();
        for (var ent: parallelMovs) {
            // 后面的mov使用到了前面mov覆盖了的值
            boolean isFloat = ent.getKey().isFloat;
            if (killed.contains(ent.getValue())) {
                // 在开头备份一下原来的值
                var temp = getVReg(isFloat);
                temp.comment = "phi mov backup";
                AsmInst backup;
                if (isFloat) {
                    backup = new VMovInst(abb, VMovInst.Ty.CPY, temp, ent.getValue());
                } else {
                    backup = new MovInst(abb, MovInst.Ty.REG, temp, ent.getValue());
                }
                backup.comment = "phi backup";
                toAdd.addAll(0, expandInstImm(backup));
                AsmInst mov;
                if (isFloat) {
                    mov = new VMovInst(abb, VMovInst.Ty.CPY, ent.getKey(), temp);
                } else {
                    mov = new MovInst(abb, MovInst.Ty.REG, ent.getKey(), temp);
                }
                mov.comment = "phi mov";
                toAdd.addAll(expandInstImm(mov));
                killed.add(ent.getKey());
            } else {
                AsmInst mov;
                if (isFloat) {
                    mov = new VMovInst(abb, VMovInst.Ty.CPY, ent.getKey(), ent.getValue());
                } else {
                    mov = new MovInst(abb, MovInst.Ty.REG, ent.getKey(), ent.getValue());
                }
                mov.comment = "phi mov";
                toAdd.addAll(expandInstImm(mov));
                killed.add(ent.getKey());
            }
        }
        abb.addAllBeforeJump(toAdd);
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
            abb.insts.addAll(expandCmpImm(new CMPInst(abb, cond, new IntImm(0))));
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
            long offset = func.sm.allocLocal(inst.ty.getSize());
            var bin = new BinOpInst(abb, BinaryOp.SUB, convertValue(inst, func, abb), new Reg(Reg.Type.fp), new IntImm(Math.toIntExact(offset)));
            bin.comment = inst.toString();
            abb.insts.addAll(expandBinOp(bin));
            return;
        }

        if (inst_ instanceof BinopInst) { // sub i32 0, 1
            var inst = (BinopInst) inst_;
            var op1 = convertValue(inst.oprands.get(0).value, func, abb);
            var op2 = convertValue(inst.oprands.get(1).value, func, abb);
            var to = convertValue(inst, func, abb);
            assert op1.isFloat == op2.isFloat;
            boolean isFloat = op1.isFloat;
            if (!inst.op.isBoolean()) {
                assert op1.isFloat == to.isFloat;
                AsmInst bin;
                if (isFloat) {
                    bin = new FBinOpInst(abb, inst.op, to, op1, op2);
                    abb.insts.addAll(expandInstImm(bin)); // 浮点运算指令不支持带着imm。
                } else {
                    bin = new BinOpInst(abb, inst.op, to, op1, op2);
                    abb.insts.addAll(expandBinOp((BinOpInst)bin));
                }
                bin.comment = inst.toString();
            } else { // LOG 逻辑二元运算：生成CMP+MOV Rd, 0+条件MOV Rd, 1
                AsmInst cmp;
                if (isFloat) {
                    cmp = new FCMPInst(abb, op1, op2);
                    abb.insts.addAll(expandInstImm(cmp)); // 如果某个操作数完全是0可以优化TODO
                    abb.insts.add(new VMRS(abb));
                } else {
                    cmp = new CMPInst(abb, op1, op2);
                    abb.insts.addAll(expandCmpImm((CMPInst)cmp));
                }
                cmp.comment = inst.toString();
                var dest = convertValue(inst, func, abb);
                assert !dest.isFloat;
                // MOVW dst, #0; MOV<cond> dst #1;
                abb.insts.addAll(MovInst.loadImm(abb, dest, new IntImm(0)));
                var mov = new MovInst(abb, MovInst.Ty.MOVW, dest, new IntImm(1));
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
                    processCallArg(call, op, loc, abb, false);
                }
            } else { // isVariadic
                List<ssa.ds.Type> params = ssaFunc.argType.stream().map(pv -> pv.type).collect(Collectors.toList());
                var cc = new BaseCallingConvention().resolve(params, ssaFunc.retType);
                call = new backend.arm.inst.CallInst(abb, new LabelImm(ssaFunc.name), cc);
                for (int i=0;i<inst.oprands.size()-1;i++) {
                    var val = inst.oprands.get(i+1).value;
                    boolean isLiftDouble = false;
                    if (i >= ssaFunc.argType.size()) { // 是额外的参数
                        cc.addParam(val.type);
                        if (val.type.isBaseType() && val.type.baseType == PrimitiveTypeTag.DOUBLE) {
                            isLiftDouble = true;
                        }
                    }
                    var loc = cc.callParam.get(i);
                    var op = convertValue(val, func, abb);
                    processCallArg(call, op, loc, abb, isLiftDouble);
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
            if (inst.op == CastOp.TYPE) {
                // 字符串到i8*等转换，好像不需要做什么
                vregMap.put(inst, convertValue(inst.oprands.get(0).value, func, abb));
            } else if (inst.op == CastOp.F2I) { // 生成vcvt.f32.s32 + VMOV
                var op = convertValue(inst.oprands.get(0).value, func, abb);
                assert op.isFloat;
                var mid = getVReg(true);
                var to = convertValue(inst, func, abb);
                assert !to.isFloat;
                var vcvt = new VCVTInst(abb, VCVTInst.Ty.F2I, mid, op);
                abb.insts.addAll(expandInstImm(vcvt));
                var vmov = new VMovInst(abb, VMovInst.Ty.S2A, to, mid);
                abb.insts.add(vmov);
            } else if (inst.op == CastOp.I2F) { // 生成VMOV + vcvt.s32.f32
                var op = convertValue(inst.oprands.get(0).value, func, abb);
                assert !op.isFloat;
                var mid = getVReg(true);
                var to = convertValue(inst, func, abb);
                assert to.isFloat;
                var vmov = new VMovInst(abb, VMovInst.Ty.A2S, mid, op);
                abb.insts.addAll(expandInstImm(vmov));
                var vcvt = new VCVTInst(abb, VCVTInst.Ty.I2F, to, mid);
                abb.insts.add(vcvt);
            } else if (inst.op == CastOp.FPEXT) { // 目前只需要处理vararg的float传参提升到double的情况，在这里直接无视，在后面传参时单独处理。
                vregMap.put(inst, convertValue(inst.oprands.get(0).value, func, abb));
            } else if (inst.op == CastOp.ZEXT) { // 目前只有从i1到i32的情况，似乎不要做什么
                vregMap.put(inst, convertValue(inst.oprands.get(0).value, func, abb));
            } else {throw new UnsupportedOperationException(inst.op.toString());}
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
            var to = convertValue(inst, func, abb);
            AsmInst asm;
            if(to.isFloat) {
                asm = new VLDRInst(abb, to, addr);
            } else {
                asm = new backend.arm.inst.LoadInst(abb, to, addr);
            }
            abb.insts.addAll(expandInstImm(asm)); // load +alloca属于跨指令的优化，交由之后窥孔优化处理。
            asm.comment = inst.toString();
            
            return;
        }

        if (inst_ instanceof StoreInst) { // store i32 10, i32* %a_0
            var inst = (StoreInst) inst_;
            var val = convertValue(inst.oprands.get(0).value, func, abb);
            var addr = convertValue(inst.oprands.get(1).value, func, abb);
            AsmInst sto;
            if (val.isFloat) {
                sto = new VSTRInst(abb, val, addr);
            } else {
                sto = new backend.arm.inst.StoreInst(abb, val, addr);
            }
            sto.comment = inst.toString();
            abb.insts.addAll(expandInstImm(sto));
            return;
        }
        throw new RuntimeException("Unknown Terminator Inst.");
    }

    private void calcGep(AsmFunc func, AsmBlock abb, GetElementPtr inst, AsmOperand addr, List<Use> ops) {
        var baseSize = inst.base.getSize();
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
                    current = GepMakeAdd(current, new IntImm(Math.toIntExact(offset)), abb, inst.toString());
                    offset = 0;
                }
                String comment = inst.toString()+" ("+use.value.name+")";

                var target = getVReg(false);
                var mul = new BinOpInst(abb, BinaryOp.MUL, target, convertValue(use.value, func, abb), new IntImm(Math.toIntExact(baseSize)));
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
            current = GepMakeAdd(current, new IntImm(Math.toIntExact(offset)), abb, inst.toString());
            offset = 0;
        }
        // var target = convertValue(inst, func, abb);
        vregMap.put(inst, current);
    }

    private AsmOperand GepMakeAdd(AsmOperand prev, AsmOperand offset, AsmBlock abb, String comment) {
        var target = getVReg(false);
        var bin = new BinOpInst(abb, BinaryOp.ADD, target, prev, offset);
        bin.comment = comment;
        abb.insts.addAll(expandBinOp(bin));
        return target;
    }

    /**
     * 维护寄存器分配约束
     * @param call 指令
     * @param op 转换后的参数，即常量或VirtReg.
     * @param loc CC给出的目标物理寄存器，也可能要放到栈上，此时需要增加相关Store指令
     * @param abb 基本块
     */
    private void processCallArg(backend.arm.inst.CallInst call, AsmOperand op, AsmOperand loc, AsmBlock abb, boolean isLiftDouble) {
        // 如果是常量，转换一下。确保参数都在寄存器内。
        if (op instanceof Imm) {
            var tmp = getVReg(op.isFloat);
            abb.insts.addAll(MovInst.loadImm(abb, tmp, (Imm)op));
            op = tmp;
        }
        assert op instanceof VirtReg;
        var vreg = (VirtReg) op;
        // 处理vararg提升到double的情况
        if (isLiftDouble) {
            // vararg 仅使用BaseCallingConvention
            assert !(loc instanceof VfpReg);
            // vcvt.f64.f32 d16 // 借助不在分配范围内的d16。
            var vcvt = new VCVTInst(abb, VCVTInst.Ty.F2D, new VfpDoubleReg(), vreg);
            vcvt.comment = "lift to double: " + vreg.comment;
            abb.insts.add(vcvt);
            if (loc instanceof Reg) { // vcvt.f64.f32 d16, Sn +  vmov r2, r3, d16
                assert ((Reg)loc).ty == Reg.Type.r0 || ((Reg)loc).ty == Reg.Type.r2;
                var vmov = new VMovInst(abb, VMovInst.Ty.S2A, loc, new VfpDoubleReg());
                var next = new Reg(Reg.Type.values[((Reg)loc).ty.toInt()+1]);
                vmov.defs.add(next);
                abb.insts.add(vmov);
            } else if (loc instanceof StackOperand) { // vcvt.f64.f32 d16, Sn +  vstr.64 d16, [sp]
                var vstr = new VSTRInst(abb, new VfpDoubleReg(), loc);
                abb.insts.addAll(expandStackOperandLoadStore(vstr));
            } else {throw new UnsupportedOperationException();}
            return;
        }
        // 回到正常情况。
        if (loc instanceof Reg) { // 维护寄存器分配约束
            call.setConstraint(vreg, (Reg)loc, true);
            call.uses.add(vreg);
        } else if (loc instanceof VfpReg) { // 维护寄存器分配约束
            call.setConstraint(vreg, (VfpReg)loc, true);
            // 维护use
            call.uses.add(vreg);
        } else if (loc instanceof StackOperand) { // 对内存中的参数生成store
            AsmInst store;
            if(vreg.isFloat) {
                store = new VSTRInst(abb, vreg, loc);
            } else {
                store = new backend.arm.inst.StoreInst(abb, vreg, loc);
            }
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
        assert inst instanceof backend.arm.inst.LoadInst || inst instanceof backend.arm.inst.StoreInst
                || inst instanceof VSTRInst || inst instanceof VLDRInst;
        if (inst instanceof backend.arm.inst.StoreInst || inst instanceof VSTRInst) {
            newuse.add(inst.uses.get(0));
            expandStackOperand((StackOpInst)inst, inst.uses.get(1), newuse, ret, inst.parent);
        } else if (inst instanceof backend.arm.inst.LoadInst || inst instanceof VLDRInst) {
            expandStackOperand((StackOpInst)inst, inst.uses.get(0), newuse, ret, inst.parent);
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
            tmp = getVReg(op.isFloat);
            insts.addAll(MovInst.loadImm(p, tmp, (Imm)op));
            newOps.add(tmp);
        } else {
            // 不变
            newOps.add(op);
        }
    }

    private void expandStackOperand(StackOpInst inst, AsmOperand op, List<AsmOperand> newOps, List<AsmInst> insts,
                                        AsmBlock p) {
        if (op instanceof StackOperand) {
            var so = (StackOperand) op;
            if (inst.isImmFit(so)) {
                newOps.add(so);
                return;
            }
            // 超出了范围一般add sub的imm字段也放不下？TODO
            AsmOperand tmp= getVReg(false);
            var tmp2 = getVReg(false);
            assert so.type != StackOperand.Type.SPILL; // 指令选择阶段不能分配spill空间。
            if (so.type == StackOperand.Type.SELF_ARG) {
                insts.addAll(MovInst.loadImm(p, tmp, new IntImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.LOCAL) {
                insts.addAll(MovInst.loadImm(p, tmp, new IntImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.SUB, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.CALL_PARAM) {
                insts.addAll(MovInst.loadImm(p, tmp, new IntImm(Math.toIntExact(so.offset))));
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
        if (op instanceof IntImm) {
            var immop = (IntImm) op;
            // within 8bit then OK
            if (immop.highestOneBit() < 255) {
                newOps.add(immop);
                return;
            }
            AsmOperand tmp;
            tmp = getVReg(false);
            insts.addAll(MovInst.loadImm(p, tmp, immop));
            newOps.add(tmp);
        } else {
            // 不变
            newOps.add(op);
        }
    }

    public List<AsmInst> expandBinOp(BinOpInst bin) {
        List<AsmInst> ret = new ArrayList<>();
        var op1 = bin.uses.get(0);
        var op2 = bin.uses.get(1);
        if (op1 instanceof Imm) { // 如果是label也要展开
            var tmp = getVReg(op1.isFloat); // 需要单个临时寄存器直接用ip
            ret.addAll(MovInst.loadImm(bin.parent, tmp, ((Imm)op1)));
            op1 = tmp;
        }
        if (op2 instanceof Imm) {
            // 0-4095的#imm12仅在Thumb模式下有。
            if (op2 instanceof IntImm && (bin.op == BinaryOp.ADD || bin.op == BinaryOp.SUB) && ((IntImm) op2).highestOneBit() < 255) {
                // OK to use imm
            } else {
                var tmp = getVReg(op2.isFloat); // 需要单个临时寄存器直接用ip
                ret.addAll(MovInst.loadImm(bin.parent, tmp, ((IntImm)op2)));
                op2 = tmp;
            }
        }
        bin.uses = new ArrayList<>(List.of(op1, op2));
        ret.add(bin);
        return ret;
    }

    // 使用IP寄存器作为临时寄存器的版本。不支持两个操作数都是需要MOV的常量
    public static List<AsmInst> expandBinOpIP(BinOpInst bin) {
        List<AsmInst> ret = new ArrayList<>();
        var op1 = bin.uses.get(0);
        var op2 = bin.uses.get(1);
        boolean ipUsed = false;
        if (op1 instanceof Imm) { // 如果是label也要展开
            assert ipUsed == false;
            ipUsed = true;
            var tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
            ret.addAll(MovInst.loadImm(bin.parent, tmp, ((Imm)op1)));
            op1 = tmp;
        }
        if (op2 instanceof Imm) {
            // 0-4095的#imm12仅在Thumb模式下有。
            if (op2 instanceof IntImm && (bin.op == BinaryOp.ADD || bin.op == BinaryOp.SUB) && ((IntImm) op2).highestOneBit() < 255) {
                // OK to use imm
            } else {
                assert ipUsed == false;
                ipUsed = true;
                var tmp = new Reg(Reg.Type.ip); // 需要单个临时寄存器直接用ip
                ret.addAll(MovInst.loadImm(bin.parent, tmp, ((IntImm)op2)));
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
        assert inst instanceof backend.arm.inst.LoadInst || inst instanceof backend.arm.inst.StoreInst
                || inst instanceof VSTRInst || inst instanceof VLDRInst;
        if (inst instanceof backend.arm.inst.StoreInst || inst instanceof VSTRInst) {
            newuse.add(inst.uses.get(0));
            expandStackOperandIP((StackOpInst)inst, inst.uses.get(1)/*可能的StackOperand*/, inst.uses.get(0), newuse, ret, inst.parent);
        } else if (inst instanceof backend.arm.inst.LoadInst || inst instanceof VLDRInst) {
            expandStackOperandIP((StackOpInst)inst, inst.uses.get(0), inst.defs.get(0), newuse, ret, inst.parent);
        }
        inst.uses = newuse;
        ret.add(inst);
        return ret;
    }

    // 使用IP作为临时寄存器
    public static void expandStackOperandIP(StackOpInst inst, AsmOperand op, AsmOperand target, List<AsmOperand> newOps, List<AsmInst> insts,
                                        AsmBlock p) {
        if (op instanceof StackOperand) {
            var so = (StackOperand) op;
            // – 4095 to +4095 then OK。但由于后面要加减一些？，所以范围放窄一些？TODO
            if (inst.isImmFit(so)) {
                newOps.add(so);
                return;
            }
            assert target instanceof Reg || target instanceof VfpReg;
            // 超出了4095，则add sub的imm字段也放不下? TODO
            AsmOperand tmp= new Reg(Reg.Type.ip);
            var tmp2 = new Reg(Reg.Type.ip);
            if (so.type == StackOperand.Type.SELF_ARG) {
                insts.addAll(MovInst.loadImm(p, tmp, new IntImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.LOCAL || so.type == StackOperand.Type.SPILL) {
                insts.addAll(MovInst.loadImm(p, tmp, new IntImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.SUB, tmp2, new Reg(Reg.Type.fp), tmp));
            } else if (so.type == StackOperand.Type.CALL_PARAM) {
                insts.addAll(MovInst.loadImm(p, tmp, new IntImm(Math.toIntExact(so.offset))));
                insts.add(new BinOpInst(p, BinaryOp.ADD, tmp2, new Reg(Reg.Type.sp), tmp));
            } else {throw new UnsupportedOperationException();}

            newOps.add(tmp2);
        } else {
            // 不变
            newOps.add(op);
        }
    }

}
