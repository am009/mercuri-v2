package ssa;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import dst.ds.AssignStatement;
import dst.ds.BasicType;
import dst.ds.BinaryExpr;
import dst.ds.Block;
import dst.ds.BlockStatement;
import dst.ds.BreakStatement;
import dst.ds.CastExpr;
import dst.ds.CompUnit;
import dst.ds.ContinueStatement;
import dst.ds.Decl;
import dst.ds.DeclType;
import dst.ds.EvaluatedValue;
import dst.ds.Expr;
import dst.ds.ExprStatement;
import dst.ds.FuncCall;
import dst.ds.FuncType;
import dst.ds.IfElseStatement;
import dst.ds.InitValue;
import dst.ds.LValExpr;
import dst.ds.LiteralExpr;
import dst.ds.LogicExpr;
import dst.ds.LoopStatement;
import dst.ds.ReturnStatement;
import dst.ds.UnaryExpr;
import dst.ds.CastExpr.CastType;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BinopInst;
import ssa.ds.CallInst;
import ssa.ds.CastInst;
import ssa.ds.CastOp;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.FuncValue;
import ssa.ds.GetElementPtr;
import ssa.ds.GlobalVariable;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.ParamValue;
import ssa.ds.PrimitiveTypeTag;
import ssa.ds.RetInst;
import ssa.ds.StoreInst;
import ssa.ds.TerminatorInst;
import ssa.ds.Type;
import ssa.ds.Value;

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
        new NumValueNamer().visitModule(ctx.module);
    }

    private void visitBuiltinDstFunc(FakeSSAGeneratorContext ctx, dst.ds.Func dstFunc) {
        var pvs = new ArrayList<ParamValue>();
        dstFunc.params.forEach(decl -> {pvs.add(new ParamValue(decl.id, convertDstType(decl.type)));});
        Func func = new Func(dstFunc.id, dstFunc.retType, pvs);
        if (dstFunc.isVariadic != null) {
            func.setIsVariadic(dstFunc.isVariadic);
        }
        ctx.module.builtins.add(func);
        FuncValue val = func.getValue();
        ctx.funcMap.put(dstFunc, val);
    }

    private Type convertDstType(dst.ds.Type type) {
        if (type.basicType == BasicType.STRING_LITERAL) {
            // string literal应该不可能是array
            assert !type.isArray;
            return Type.String;
        } else if (type.basicType == null) {
            return Type.Void;
        } else {
            PrimitiveTypeTag tag;
            if (type.basicType == BasicType.INT) {
                tag = PrimitiveTypeTag.INT;
            } else if (type.basicType == BasicType.FLOAT) {
                tag = PrimitiveTypeTag.FLOAT;
            } else {
                throw new RuntimeException("Unknown basicType " + type.basicType.toString());
            }
            boolean isPointer = type.isPointer;
            List<Integer> dims = type.dims;
            return new Type(tag, dims, isPointer);
        }
    }

    private void visitGlobalDecl(FakeSSAGeneratorContext ctx, Decl decl) {
        if ((!decl.isArray()) && decl.isConst()) { // 只有这个不用生成全局变量
            // initVal必然不为null
            ctx.globVarMap.put(decl, convertEvaledValue(decl.initVal.evaledVal));
            return;
        }
        GlobalVariable gv = new GlobalVariable(decl.id, convertDstType(decl.type));
        gv.isConst = decl.isConst();
        ctx.module.globs.add(gv);
        ctx.globVarMap.put(decl, gv);
        // 初始值
        if (decl.initVal != null) {
            gv.init = visitConstInitVal(ctx, decl.type, decl.initVal);
        }
    }

    // 处理initVal里都是ConstExpr的情况。语义分析的时候已经平坦化展开和eval过了，只需要取即可。
    private ConstantValue visitConstInitVal(FakeSSAGeneratorContext ctx, dst.ds.Type type, InitValue initVal) {
        if (!type.isArray) {
            return convertEvaledValue(initVal.evaledVal);
        } else {
            return convertArrayEvaledValue(convertDstType(type), initVal);
        }
    }

    private static ConstantValue convertArrayEvaledValue(Type type, InitValue initVal) {
        if (!type.isArray()) { // 简单情况
            return convertEvaledValue(initVal.evaledVal);
        } else { // 递归
            List<ConstantValue> result = new ArrayList<>();
            int currentSize = type.dims.get(0);
            // 语义检查后应该initVal的层次结构和type的dims匹配
            assert initVal.values.size() == currentSize;
            for (int i=0;i<currentSize;i++) {
                var v = convertArrayEvaledValue(type.subArrType(), initVal.values.get(i));
                result.add(v);
            }
            return new ConstantValue(type, result);
        }
    }

    private static ConstantValue convertEvaledValue(EvaluatedValue evaledVal) {
        if (evaledVal.basicType == BasicType.STRING_LITERAL) { // String转i8数组
            List<ConstantValue> chars = new ArrayList<>();
            for (byte c: evaledVal.stringValue.getBytes()) {
                chars.add(ConstantValue.ofChar(c));
            }
            chars.add(ConstantValue.ofChar(0));
            return new ConstantValue(new Type(PrimitiveTypeTag.CHAR, List.of(chars.size()), false), chars);
        }
        switch (evaledVal.basicType) {
            case FLOAT:
                return ConstantValue.ofFloat(evaledVal.floatValue);
            case INT:
                return ConstantValue.ofInt(evaledVal.intValue);
            default:
                throw new RuntimeException("Unknown basicType "+evaledVal.basicType.toString());
        }
    }

    private void visitDstFunc(FakeSSAGeneratorContext ctx, dst.ds.Func dstFunc) {
        var pvs = new ArrayList<ParamValue>();
        dstFunc.params.forEach(decl -> {pvs.add(new ParamValue(decl.id, convertDstType(decl.type)));});
        Func func = new Func(dstFunc.id, dstFunc.retType, pvs);
        if (dstFunc.isVariadic != null) {
            func.setIsVariadic(dstFunc.isVariadic);
        }
        ctx.module.funcs.add(func);
        FuncValue val = func.getValue();
        ctx.funcMap.put(dstFunc, val);

        // 入口基本块
        func.bbs = new LinkedList<>();
        BasicBlock ent = new BasicBlock("entry");
        func.bbs.add(ent);
        ctx.current = ent;

        // 函数参数
        // dstFunc.params.forEach(param -> visitFuncParamDecl(ctx, ));
        dstFunc.params.forEach(param -> {
            var ref = new ParamValue(param.id, convertDstType(param.type));
            ref.name = param.id;
            if (param.isArray()) {
                // array参数不需要创建alloca指令
                ctx.varMap.put(param, ref);
            } else {
                var alloc = new AllocaInst.Builder(ent).addType(convertDstType(param.type)).build();
                alloc.name = param.id+"_";
                ctx.addToCurrent(alloc);
                ctx.varMap.put(param, alloc);
                var inst = new StoreInst.Builder(ent).addOperand(ref, alloc).build();
                ctx.addToCurrent(inst);
            }
        });

        
        dstFunc.body.statements.forEach(bs -> {visitDstStmt(ctx, func, bs);});
        // 指令生成是线性模型，比如int f(){if(){..} else {...}}我也需要在末尾加上一个基本块，保证是和if else同级的，
        // 如果最后一个基本块最后不是返回指令，加上返回指令。
        var current = ctx.current;
        // 如果基本块没有用TerminatorInst结尾，加入RetInst
        if (current.insts.size() == 0 || !(current.insts.get(current.insts.size()-1) instanceof TerminatorInst)) {
            var b  = new RetInst.Builder(current);
            if (func.retType == FuncType.VOID) {
                b.addType(Type.Void);
            } else if (func.retType == FuncType.INT) {
                b.addType(Type.Int);
                b.addOperand(ConstantValue.ofInt(0));
            } else if (func.retType == FuncType.FLOAT) {
                b.addType(Type.Float);
                b.addOperand(ConstantValue.ofFloat(0f));
            } else {
                throw new RuntimeException("Unknown FuncType.");
            }
            current.insts.add(b.build());
        }
        // 如果基本块以非RetInst结尾，报错。
        if (!(current.insts.get(current.insts.size()-1) instanceof RetInst)) {
            throw new RuntimeException("Function " + dstFunc.id + ": generated code not end with return.");
        }
        // TODO 修复基本块之间的双向链接？？(trivial compiler)
    }

    private void visitDstStmt(FakeSSAGeneratorContext ctx, Func curFunc, BlockStatement stmt_) {
        if (stmt_ instanceof AssignStatement) {
            var stmt = (AssignStatement) stmt_;
            
            return;
        }

        if (stmt_ instanceof Block) {
            var stmt = (Block) stmt_;
            stmt.statements.forEach(innerStmt -> this.visitDstStmt(ctx, curFunc, innerStmt));
            return;
        }

        if (stmt_ instanceof BreakStatement) {
            var stmt = (BreakStatement) stmt_;

            return;
        }

        if (stmt_ instanceof ContinueStatement) {
            var stmt = (ContinueStatement) stmt_;

            return;
        }

        if (stmt_ instanceof Decl) {
            var stmt = (Decl) stmt_;
            this.visitDstDecl(ctx, curFunc, stmt);
            return;
        }

        if (stmt_ instanceof ExprStatement) {
            var stmt = (ExprStatement) stmt_;
            this.visitDstExpr(ctx, curFunc, stmt.expr);
            return;
        }

        if (stmt_ instanceof IfElseStatement) {
            var stmt = (IfElseStatement) stmt_;
            this.visitDstExpr(ctx, curFunc, stmt.condition);
            this.visitDstStmt(ctx, curFunc, stmt.thenBlock);
            if (stmt.elseBlock != null) {
                this.visitDstStmt(ctx, curFunc, stmt.elseBlock);
            }
            return;
        }

        if (stmt_ instanceof LoopStatement) {
            var stmt = (LoopStatement) stmt_;
            var cond = this.visitDstExpr(ctx, curFunc, stmt.condition);
            
            this.visitDstStmt(ctx, curFunc, stmt.bodyBlock);

            return;
        }

        if (stmt_ instanceof ReturnStatement) {
            var stmt = (ReturnStatement) stmt_;
            var val = visitDstExpr(ctx, curFunc, stmt.retval);
            var b = new RetInst.Builder(ctx.current);
            b.addType(curFunc.getRetType());
            if (!val.type.equals(Type.Void)) {
                b.addOperand(val);
            }
            ctx.addToCurrent(b.build());
            // TODO 如果current末尾有jump可以消除掉？
            return;
        }
    }

    private Value visitDstExpr(FakeSSAGeneratorContext ctx, Func curFunc, Expr expr_) {
        if (expr_ instanceof CastExpr) {
            var expr = (CastExpr) expr_;
            var val = visitDstExpr(ctx, curFunc, expr.child);
            CastOp op = null;
            if (expr.castType == CastType.I2F) {
                op = CastOp.I2F;
            } else if (expr.castType == CastType.F2I) {
                op = CastOp.F2I;
            }
            var cast = new CastInst.Builder(ctx.current, val).addOp(op).build();
            cast.comments = expr.reason;
            ctx.addToCurrent(cast);
            return cast;
        }

        if (expr_ instanceof BinaryExpr) {
            var expr = (BinaryExpr) expr_;
            var l = visitDstExpr(ctx, curFunc, expr.left);
            var r = visitDstExpr(ctx, curFunc, expr.right);
            var ret = ctx.addToCurrent(new BinopInst(ctx.current, l.type, expr.op, l, r));
            return ret;
        }

        if (expr_ instanceof FuncCall) {
            var expr = (FuncCall) expr_;
            var fv = (FuncValue) ctx.funcMap.get(expr.funcSymbol.func);
            var cb = new CallInst.Builder(ctx.current, fv);
            if (expr.args != null) {
                for(int i=0;i<expr.args.length;i++) {
                    cb.addArg(visitDstExpr(ctx, curFunc, expr.args[i]));
                }
            }
            return ctx.addToCurrent(cb.build());
        }

        if (expr_ instanceof LiteralExpr) {
            var expr = (LiteralExpr) expr_;
            var constant = convertEvaledValue(expr.value);
            if (expr.value.basicType == BasicType.STRING_LITERAL) {
                // 字符串放到全局变量里，然后返回i8*
                var gv = new GlobalVariable(".str."+ctx.getStrInd(), constant.type.clone());
                gv.init = constant;
                ctx.module.globs.add(gv);
                // LLVM 是用 get element ptr 获取起始i8* 地址，我这里直接bitcast应该也行
                return ctx.addToCurrent(new CastInst.Builder(ctx.current, gv).strBitCast(gv.type).build());
            }
            return constant;
        }

        if (expr_ instanceof LogicExpr) {
            var expr = (LogicExpr) expr_;
            return null;
        }

        if (expr_ instanceof LValExpr) {
            var expr = (LValExpr) expr_;
            Value val;
            if (expr.declSymbol.decl.isGlobal) {
                val = ctx.globVarMap.get(expr.declSymbol.decl);
            } else  {
                val = ctx.varMap.get(expr.declSymbol.decl);
            }
            if (!expr.isArray) {
                if (expr.declSymbol.decl.isConst()) { // 最简单情况，直接找到ConstantValue返回
                    return val;
                } else {
                    // 生成load语句
                    return ctx.addToCurrent(new LoadInst(ctx.current, val));
                }
            } else { // 担心常量数组可能传函数参数，所以就当普通数组处理了。
                // 此时val应该是指针。
                var gep = new GetElementPtr(ctx.current, val);
                ctx.addToCurrent(gep);
                expr.indices.forEach(exp -> {
                    var val_ = visitDstExpr(ctx, curFunc, exp);
                    gep.addIndex(val_);
                });
                if (gep.type.isArray()) {
                    return gep;
                } else {                
                    return ctx.addToCurrent(new LoadInst(ctx.current, gep));
                }
            }
        }

        if (expr_ instanceof UnaryExpr) {
            var expr = (UnaryExpr) expr_;
            return null;
        }
        throw new RuntimeException("Unknown Expr type.");
    }

    // 局部变量的Decl
    private void visitDstDecl(FakeSSAGeneratorContext ctx, ssa.ds.Func curFunc, Decl decl) {
        // 是非array的Const变量则不需要生成语句，直接后面用ConstantValue即可。
        if (!decl.type.isArray && decl.isConst()) {
            ConstantValue cv = convertEvaledValue(decl.initVal.evaledVal);
            ctx.varMap.put(decl, cv);
            return;
        }
        // 生成alloca语句，即使是array也能一个指令解决
        var alloc = new AllocaInst.Builder(ctx.current).addType(convertDstType(decl.type)).build();
        ctx.addToCurrent(alloc);
        alloc.name = decl.id+"_";
        ctx.varMap.put(decl, alloc);
        // 处理非Const普通变量的初始值，可能是复杂表达式。
        // 语义分析没有计算evaledVal
        if(decl.initVal != null) {
            if (!decl.type.isArray) {
                var cv = visitDstExpr(ctx, curFunc, decl.initVal.value);
                var inst = new StoreInst.Builder(ctx.current).addOperand(cv, alloc).build();
                ctx.addToCurrent(inst);
            } else {
                // TODO array memset to 0 and getelementptr and set content.
                setArrayInitialVal(ctx, curFunc, alloc, decl.initVal, decl.type.dims);
            }
        }
    }

    void setArrayInitialVal(FakeSSAGeneratorContext ctx, ssa.ds.Func curFunc, Value ptr, InitValue initValue, List<Integer> dims) {
        if (initValue.isAllZero) { // 为了第一次调用时检查。
            return;
        }

        assert initValue.isArray;
        if (dims.size() > 1) {
            int currentSize = dims.get(0);
            dims = dims.subList(1, dims.size());
            for (int i=0;i<currentSize;i++) {
                var iv = initValue.values.get(i);
                if (!iv.isAllZero) {
                    // 生成Gep
                    var ptr_ = new GetElementPtr(ctx.current, ptr);
                    ptr_.addIndex(ConstantValue.ofInt(i));
                    ctx.addToCurrent(ptr_);
                    // 递归调用
                    setArrayInitialVal(ctx, curFunc, ptr_, iv, dims);
                }
            }
        } else {
            int currentSize = dims.get(0);
            for (int i=0;i<currentSize;i++) {
                var iv = initValue.values.get(i);
                assert !iv.isArray;
                // 如果iv不是默认值则生成gep和store
                if (iv.value instanceof LiteralExpr && ((LiteralExpr)(iv.value)).value.isDefault()) {
                } else {
                    var ptr_ = new GetElementPtr(ctx.current, ptr);
                    ptr_.addIndex(ConstantValue.ofInt(i));
                    ctx.addToCurrent(ptr_);
                    var val = visitDstExpr(ctx, curFunc, iv.value);
                    var inst = new StoreInst.Builder(ctx.current).addOperand(val, ptr_).build();
                    ctx.addToCurrent(inst);
                }
            }
        }
    }
}