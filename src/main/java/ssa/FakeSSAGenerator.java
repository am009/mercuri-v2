package ssa;

import java.util.ArrayList;
import java.util.LinkedList;

import dst.ds.CompUnit;
import dst.ds.Decl;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.Func;
import ssa.ds.FuncValue;
import ssa.ds.GlobalVariable;
import ssa.ds.Module;
import ssa.ds.ParamValue;
import ssa.ds.StoreInst;

public class FakeSSAGenerator {
    public Module process(CompUnit dst) {
        var module = new Module(dst.file);
        var genContext = new FakeSSAGeneratorContext(module);
        this.visitDstCompUnit(genContext, dst);
        return genContext.module;
    }

    private void visitDstCompUnit(FakeSSAGeneratorContext ctx, CompUnit dst) {
        ir.ds.Module.builtinFuncs.forEach(func -> visitBuiltinDstFunc(ctx, func));
        dst.decls.forEach(decl -> this.visitGlobalDecl(ctx, decl));
        dst.funcs.forEach(func -> this.visitDstFunc(ctx, func));
    }

    private void visitBuiltinDstFunc(FakeSSAGeneratorContext ctx, dst.ds.Func dstFunc) {
        var pvs = new ArrayList<ParamValue>();
        dstFunc.params.forEach(decl -> {pvs.add(new ParamValue(decl.id, decl.type));});
        Func func = new Func(dstFunc.id, dstFunc.retType, pvs);
        if (dstFunc.isVariadic != null) {
            func.setIsVariadic(dstFunc.isVariadic);
        }
        ctx.module.builtins.add(func);
        FuncValue val = func.getValue();
        ctx.func_map.put(dstFunc, val);
    }

    private void visitDstFunc(FakeSSAGeneratorContext ctx, dst.ds.Func dstFunc) {
        var pvs = new ArrayList<ParamValue>();
        dstFunc.params.forEach(decl -> {pvs.add(new ParamValue(decl.id, decl.type));});
        Func func = new Func(dstFunc.id, dstFunc.retType, pvs);
        if (dstFunc.isVariadic != null) {
            func.setIsVariadic(dstFunc.isVariadic);
        }
        ctx.module.funcs.add(func);
        FuncValue val = func.getValue();
        ctx.func_map.put(dstFunc, val);

        // 入口基本块
        func.bbs = new LinkedList<>();
        // TODO BasicBlockEntry?
        BasicBlock ent = new BasicBlock("entry");
        func.bbs.add(ent);

        // 函数参数
        // dstFunc.params.forEach(param -> visitFuncParamDecl(ctx, ));
        dstFunc.params.forEach(param -> {
            if (param.isArray()) {
                // array参数不需要创建alloca指令
                ctx.var_map.put(param, new ParamValue(param.id, param.type));
            } else {
                var alloc = new AllocaInst.Builder(ent).addType(param.type).build();
                ctx.var_map.put(param, alloc);
                var para = new ParamValue(param.id, param.type);
                new StoreInst.Builder(ent).addType(param.type).addOperand(para, alloc);
            }
        });
        // TODO function body
    }

    private void visitGlobalDecl(FakeSSAGeneratorContext ctx, Decl decl) {
        GlobalVariable gv = new GlobalVariable(decl.id, decl.type);
        gv.isConst = decl.type.isConst;
        ctx.module.globs.add(gv);
        ctx.var_map.put(decl, gv);
    }

    private void visitDstDecl(FakeSSAGeneratorContext ctx, ssa.ds.Func func, Decl decl) {
        
    }
}
