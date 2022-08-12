package ssa;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ds.Global;
import dst.ds.AssignStatement;
import dst.ds.BasicType;
import dst.ds.BinaryExpr;
import dst.ds.BinaryOp;
import dst.ds.Block;
import dst.ds.BlockStatement;
import dst.ds.BreakStatement;
import dst.ds.CastExpr;
import dst.ds.CompUnit;
import dst.ds.ContinueStatement;
import dst.ds.Decl;
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
import dst.ds.NonShortLogicExpr;
import dst.ds.ReturnStatement;
import dst.ds.UnaryExpr;
import dst.ds.UnaryOp;
import dst.ds.CastExpr.CastType;
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
import ssa.ds.JumpInst;
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
        // params
        var pvs = new ArrayList<ParamValue>();
        dstFunc.params.forEach(decl -> {
            pvs.add(new ParamValue(decl.id, convertDstType(decl.type)));
        });

        // create ssa func
        Func func = new Func(dstFunc.id, dstFunc.retType, pvs);
        if (dstFunc.isVariadic != null) {
            func.setIsVariadic(dstFunc.isVariadic);
        }
        ctx.module.builtins.add(func);
        FuncValue val = func.getValue();
        ctx.funcMap.put(dstFunc, val);
    }

    /**
     * 用于将 dst Type 转换为 SSA Type
     * 
     * @param type
     * @return
     */
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
        if (decl.initVal != null && (!decl.initVal.isAllZero)) {
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
            for (int i = 0; i < currentSize; i++) {
                var v = convertArrayEvaledValue(type.subArrType(), initVal.values.get(i));
                result.add(v);
            }
            return new ConstantValue(type, result);
        }
    }

    /**
     * Dst EvaluatedValue 转化为 SSA 常数（组）
     */
    private static ConstantValue convertEvaledValue(EvaluatedValue evaledVal) {
        if (evaledVal.basicType == BasicType.STRING_LITERAL) { // String转i8数组
            List<ConstantValue> chars = new ArrayList<>();
            for (byte c : evaledVal.stringValue.getBytes()) {
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
                throw new RuntimeException("Unknown basicType " + evaledVal.basicType.toString());
        }
    }

    private void visitDstFunc(FakeSSAGeneratorContext ctx, dst.ds.Func dstFunc) {
        // params
        var pvs = new ArrayList<ParamValue>();
        dstFunc.params.forEach(decl -> {
            pvs.add(new ParamValue(decl.id, convertDstType(decl.type)));
        });

        // create func object
        Func func = new Func(dstFunc.id, dstFunc.retType, pvs);
        if (dstFunc.isVariadic != null) {
            func.setIsVariadic(dstFunc.isVariadic);
        }
        ctx.module.funcs.add(func);
        FuncValue val = func.getValue();
        ctx.funcMap.put(dstFunc, val);
        ctx.currentFunc = func;

        // entry basic block for func
        func.bbs = new LinkedList<>();
        BasicBlock ent = new BasicBlock("entry");
        func.bbs.add(ent);
        ctx.current = ent;

        // create insts for params and add to current bb
        for (int i = 0; i < dstFunc.params.size(); i++) {
            var param = dstFunc.params.get(i);
            ParamValue ref = pvs.get(i);
            ref.name = param.id;
            if (param.isArray()) {
                // array参数不需要创建alloca指令
                ctx.varMap.put(param, ref);
            } else {
                var alloc = new AllocaInst.Builder(ent).addType(convertDstType(param.type)).build();
                alloc.name = ctx.nameLocal(param.id);
                ctx.addToCurrentBB(alloc);
                ctx.varMap.put(param, alloc);
                var inst = new StoreInst.Builder(ent).addOperand(ref, alloc).build();
                ctx.addToCurrentBB(inst);
            }
        }

        // 生成函数体的 insts
        dstFunc.body.statements.forEach(bs -> {
            visitDstStmt(ctx, func, bs);
        });
        // 指令生成是线性模型，比如int f(){if(){..} else {...}}我也需要在末尾加上一个基本块，保证是和if else同级的，
        // 如果最后一个基本块最后不是返回指令，加上返回指令。
        var current = ctx.current;

        // 如果基本块没有用 TerminatorInst 结尾，加入 RetInst
        if (current.insts.size() == 0 || !(current.insts.get(current.insts.size() - 1) instanceof TerminatorInst)) {
            var b = new RetInst.Builder(current);
            // 自动生成返回值
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
        if (!(current.insts.get(current.insts.size() - 1) instanceof RetInst)) {
            throw new RuntimeException("Function " + dstFunc.id + ": generated code not end with return.");
        }
    }

    // 生成表达式的 SSA IR
    private void visitDstStmt(FakeSSAGeneratorContext ctx, Func curFunc, BlockStatement stmt_) {
        if (stmt_ instanceof AssignStatement) {
            var stmt = (AssignStatement) stmt_;
            // 在assign左边的LVal一般直接是值
            var left = visitLValExpr(ctx, curFunc, stmt.left, true); // 获取代表的地址
            var right = visitDstExpr(ctx, curFunc, stmt.expr);
            ctx.addToCurrentBB(new StoreInst.Builder(ctx.current).addOperand(right, left).build());
            return;
        }

        if (stmt_ instanceof Block) {
            var stmt = (Block) stmt_;
            stmt.statements.forEach(innerStmt -> this.visitDstStmt(ctx, curFunc, innerStmt));
            return;
        }

        if (stmt_ instanceof BreakStatement) {
            var stmt = (BreakStatement) stmt_;
            var inst = ctx.addToCurrentBB(new JumpInst(ctx.current, ctx.breakMap.get(stmt.loop)));
            inst.comments = "break";
            return;
        }

        if (stmt_ instanceof ContinueStatement) {
            var stmt = (ContinueStatement) stmt_;
            var inst = ctx.addToCurrentBB(new JumpInst(ctx.current, ctx.continueMap.get(stmt.loop)));
            inst.comments = "continue";
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
            int ind = ctx.nextBBIdx();
            var trueBlock = new BasicBlock("if_true_" + ind);
            var exitBlock = new BasicBlock("if_end_" + ind);
            var falseBlock = exitBlock;
            if (stmt.elseBlock != null) {
                falseBlock = new BasicBlock("if_false_" + ind);
            }
            // var cond = this.visitDstExpr(ctx, curFunc, stmt.condition);
            // ctx.addToCurrent(new BranchInst(ctx.current, cond, trueBlock.getValue(),
            // falseBlock.getValue()));
            visitCondExprs(ctx, curFunc, stmt.condition.expr, trueBlock.getValue(), falseBlock.getValue());

            // 在true block生成指令
            ctx.current = trueBlock;
            curFunc.bbs.add(trueBlock);
            this.visitDstStmt(ctx, curFunc, stmt.thenBlock);
            ctx.addToCurrentBB(new JumpInst(ctx.current, exitBlock.getValue()));
            if (stmt.elseBlock != null) {
                ctx.current = falseBlock;
                curFunc.bbs.add(falseBlock);
                this.visitDstStmt(ctx, curFunc, stmt.elseBlock);
                ctx.addToCurrentBB(new JumpInst(ctx.current, exitBlock.getValue()));
            }
            ctx.current = exitBlock;
            curFunc.bbs.add(exitBlock);
            return;
        }

        if (stmt_ instanceof LoopStatement) {
            /*
             * <while_entry>  <--\
             * while (cond) {     |
             * <while_body>       |
             *     block---------/
             * }
             * <while_end>:
             */
            var loopStmt = (LoopStatement) stmt_;
            // 由于要跳转到expr计算的前面，这里要分割一个基本块
            int bbid = ctx.nextBBIdx();
            var entBlock = new BasicBlock("while_entry_" + bbid);
            {
                ctx.addToCurrentBB(new JumpInst(ctx.current, entBlock.getValue()));
                ctx.current = entBlock;
                curFunc.bbs.add(entBlock);
            }
            var bodyBlock = new BasicBlock("while_body_" + bbid);
            var exitBlock = new BasicBlock("while_end_" + bbid);
            // var cond = this.visitDstExpr(ctx, curFunc, stmt.condition);
            // cx.addToCurrent(new BranchInst(ctx.current, cond, bodyBlock.getValue(),
            // exitBlock.getValue()));

            // 生成循环条件指令并关联条件跳转目标
            visitCondExprs(ctx, curFunc, loopStmt.condition.expr, bodyBlock.getValue(), exitBlock.getValue());

            // 设置本循环语句的 continue 和 break 语句的跳转目标
            ctx.breakMap.put(loopStmt, exitBlock.getValue());
            ctx.continueMap.put(loopStmt, entBlock.getValue());

            ctx.current = bodyBlock;
            curFunc.bbs.add(bodyBlock);

            // 生成循环体的指令
            this.visitDstStmt(ctx, curFunc, loopStmt.bodyBlock);
            // 循环体末尾跳转到 <while_entry>
            ctx.addToCurrentBB(new JumpInst(ctx.current, entBlock.getValue()));

            ctx.current = exitBlock;
            curFunc.bbs.add(exitBlock);
            return;
        }

        if (stmt_ instanceof ReturnStatement) {
            var stmt = (ReturnStatement) stmt_;
            var val = visitDstExpr(ctx, curFunc, stmt.retval);
            var b = new RetInst.Builder(ctx.current);
            b.addType(curFunc.getRetType());
            // 若有返回值
            if (curFunc.retType != FuncType.VOID) {
                b.addOperand(val);
            }
            ctx.addToCurrentBB(b.build());
            return;
        }
    }

    /**
     * 处理各种各样类型的表达式 SSA 生成
     * 
     * @param ctx
     * @param curFunc
     * @param expr_
     * @return
     */
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
            ctx.addToCurrentBB(cast);
            return cast;
        }

        if (expr_ instanceof BinaryExpr) {
            var expr = (BinaryExpr) expr_;
            assert !expr.op.isShortCircuit();
            var l = visitDstExpr(ctx, curFunc, expr.left);
            var r = visitDstExpr(ctx, curFunc, expr.right);
            var ret = ctx.addToCurrentBB(new BinopInst(ctx.current, expr.op, l, r));
            // 保证比较等布尔运算返回的是i32类型
            if (ret.type.equals(Type.Boolean)) { // op.isBoolean()
                ret = new CastInst.Builder(ctx.current, ret).boolExtCast().build();
                ctx.addToCurrentBB(ret);
            }
            return ret;
        }

        if (expr_ instanceof FuncCall) {
            var expr = (FuncCall) expr_;
            var fv = ctx.funcMap.get(expr.funcSymbol.func);
            var cb = new CallInst.Builder(ctx.current, fv);
            if (expr.args != null) {
                for (int i = 0; i < expr.args.length; i++) {
                    // 考虑数组传一部分的情况？
                    var val = visitDstExpr(ctx, curFunc, expr.args[i]);
                    // Float需要提升到Double再传入vararg？
                    if ((i >= expr.funcSymbol.func.params.size()) && val.type.equals(Type.Float)) {
                        assert expr.funcSymbol.func.isVariadic;
                        val = ctx.addToCurrentBB(new CastInst.Builder(ctx.current, val).f2d().build());
                    }
                    cb.addArg(val);
                }
            }
            return ctx.addToCurrentBB(cb.build());
        }

        if (expr_ instanceof LiteralExpr) {
            var expr = (LiteralExpr) expr_;
            var constant = convertEvaledValue(expr.value);
            if (expr.value.basicType == BasicType.STRING_LITERAL) {
                // 字符串放到全局变量里，然后返回i8*
                var gv = new GlobalVariable(".str." + ctx.nextStrIdx(), constant.type.clone());
                gv.init = constant;
                ctx.module.globs.add(gv);
                // LLVM 是用 get element ptr 获取起始i8* 地址，我这里直接bitcast应该也行
                return ctx.addToCurrentBB(new CastInst.Builder(ctx.current, gv).strBitCast(gv.type).build());
            }
            return constant;
        }

        if (expr_ instanceof LValExpr) {
            var expr = (LValExpr) expr_;
            return visitLValExpr(ctx, curFunc, expr, false);
        }

        if (expr_ instanceof UnaryExpr) {
            var expr = (UnaryExpr) expr_;
            if (expr.op == UnaryOp.POS) {
                Global.logger.warning("Will someone actually use UnaryOp.POS`+` ?");
                return visitDstExpr(ctx, curFunc, expr.expr);
            } else if (expr.op == UnaryOp.NEG) {
                var val = visitDstExpr(ctx, curFunc, expr.expr);
                return ctx.addToCurrentBB(
                        new BinopInst(ctx.current, BinaryOp.SUB, ConstantValue.getDefault(val.type), val));
            } else if (expr.op == UnaryOp.NOT) {
                var val = visitDstExpr(ctx, curFunc, expr.expr);
                var ret = ctx.addToCurrentBB(
                        new BinopInst(ctx.current, BinaryOp.LOG_EQ, ConstantValue.getDefault(val.type), val));
                ret = new CastInst.Builder(ctx.current, ret).boolExtCast().build();
                ctx.addToCurrentBB(ret);
                return ret;
            } else {
                throw new RuntimeException("Unknown UnaryOp type.");
            }
        }

        if (expr_ instanceof NonShortLogicExpr) { // 说明要cast到i1类型了
            var expr = (NonShortLogicExpr) expr_;
            var val = visitDstExpr(ctx, curFunc, expr.expr);
            if (val.type.equals(Type.Boolean)) {
                return val; // 是逻辑表达式
            }
            // cast val to i1; (x) -> (x ne 0)
            return ctx.addToCurrentBB(
                    new BinopInst(ctx.current, BinaryOp.LOG_NEQ, ConstantValue.getDefault(val.type), val));
        }

        if (expr_ instanceof LogicExpr) {
            throw new RuntimeException("Cannot process logic expr in visitDstExpr, use visitCondExprs instead.");
        }

        if (expr_ == null) { // empty stmt
            return null;
        }

        throw new RuntimeException("Unknown Expr type.");
    }

    /**
     * 处理LVal中取数组下标的情况
     * 
     * 由于存在语义规则
     * primaryExpr
     * : '(' expr ')' #primaryExprQuote
     * | lVal #primaryExprLVal
     * | number #primaryExprNumber
     * ;
     * 会导致 LVal 不一定真的是左值，有可能只是一个能作为左值但不是左值的表达式。因此 toAssign 参数表明是否真的是左值（真的被赋值）
     * 
     * @param ctx
     * @param curFunc
     * @param expr
     * @param toAssign 是否位于Assign语句左边
     * @return
     */
    private Value visitLValExpr(FakeSSAGeneratorContext ctx, Func curFunc, LValExpr expr, boolean toAssign) {
        // 取出 expr 对应的符号
        Value val;
        if (expr.declSymbol.decl.isGlobal) {
            val = ctx.globVarMap.get(expr.declSymbol.decl);
        } else {
            val = ctx.varMap.get(expr.declSymbol.decl);
        }

        // 如果没有数组访问表达式（方括号，下标访问）
        if (!expr.isArray) {
            // 进一步判断是否数组类型
            if (expr.declSymbol.decl.isArray()) { // 没有取下标，但是是数组
                // 数组要么是alloca，要么是全局变量，要么是函数参数，这两种情况都直接是地址
                assert toAssign == false;// 也就是说，数组名称不可能直接被赋值
                // 如果给了维度信息，忘记数组第一维
                if (!(expr.declSymbol.decl.isDimensionOmitted)) {
                    assert !(val instanceof ParamValue);
                    var gep = new GetElementPtr(ctx.current, val);
                    gep.comments = "forget first dim";
                    ctx.addToCurrentBB(gep);
                    gep.addIndex(ConstantValue.ofInt(0));
                    return gep;
                }
                assert val instanceof ParamValue;
                return val;
            } else { // 不需要处理数组访问也不是数组

                if (expr.declSymbol.decl.isConst()) { // 最简单情况，直接找到ConstantValue返回
                    assert toAssign == false;
                    return val;
                }
                // 赋值语句，直接返回 decl
                if (toAssign) {
                    return val;
                }
                // 非 toAssign，则是访问右值，因此生成 load 语句
                return ctx.addToCurrentBB(new LoadInst(ctx.current, val));

            }
        } else { // 是数组，同时也有取下标运算
            // 担心常量数组可能传函数参数，所以就当普通数组处理了。
            // 此时val应该是指针。
            var gep = new GetElementPtr(ctx.current, val);
            expr.indices.forEach(exp -> {
                var val_ = visitDstExpr(ctx, curFunc, exp);
                gep.addIndex(val_);
            });
            ctx.addToCurrentBB(gep);
            if (gep.type.isArray()) { // index没有取到底
                assert toAssign == false;
                // 一般是函数调用出现，因此要再省略一维下标
                var gep2 = new GetElementPtr(ctx.current, gep);
                gep2.comments = "forget one dim";
                ctx.addToCurrentBB(gep2);
                // 非ParamValue自动加一个0
                gep2.addIndex(ConstantValue.ofInt(0));
                return gep2;
            } else {
                // 都返回地址，后面要用到的时候再load？
                if (toAssign) {
                    return gep;
                } else {
                    return ctx.addToCurrentBB(new LoadInst(ctx.current, gep));
                }
            }
        }
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
        var alloca = new AllocaInst.Builder(ctx.current).addType(convertDstType(decl.type)).build();
        ctx.addAllocaToEntry(alloca);
        alloca.name = ctx.nameLocal(decl.id);
        ctx.varMap.put(decl, alloca);

        // 处理非Const普通变量的初始值，可能是复杂表达式。
        // 语义分析没有计算evaledVal
        if (decl.initVal != null) {
            if (!decl.type.isArray) {
                var val = visitDstExpr(ctx, curFunc, decl.initVal.value);
                var inst = new StoreInst.Builder(ctx.current).addOperand(val, alloca).build();
                ctx.addToCurrentBB(inst);
            } else {
                // bitcast alloca to int*
                var cast = new CastInst.Builder(ctx.current, alloca).strBitCast(alloca.type).build();
                ctx.addToCurrentBB(cast);
                // memset ptr, char, size.
                var memsetFunc = ctx.funcMap.get(ir.ds.Module.builtinFuncs.get(ir.ds.Module.MEMSET));
                var memset = new CallInst.Builder(ctx.current, memsetFunc)
                                .addArg(cast)
                                .addArg(ConstantValue.ofInt(0))
                                .addArg(ConstantValue.ofInt(Math.toIntExact(alloca.ty.getSize()))).build();
                ctx.addToCurrentBB(memset);
                setArrayInitialVal(ctx, curFunc, alloca, decl.initVal, decl.type.dims);
            }
        }
    }

    /**
     * 处理条件跳转表达式。
     * 递归处理and和or的短路求值
     * 
     * @param ctx
     * @param curFunc
     * @param inLogic expr in LogicExpr (normally `stmt.condition.expr`)
     * @param trueBlock 条件为真时跳转到的 bb
     * @param falseBlock
     */
    private void visitCondExprs(FakeSSAGeneratorContext ctx, ssa.ds.Func curFunc, Expr inLogic, BasicBlockValue trueBlock,
            BasicBlockValue falseBlock) {
        // 如果是可以短路求值的二元表达式
        if (inLogic instanceof BinaryExpr && ((BinaryExpr) inLogic).op.isShortCircuit()) { // logic and/or
            BinaryExpr expr = (BinaryExpr) inLogic;
            // 如果是and，true跳转到右边，false跳转到f。
            // 如果是or，true跳转到t，false跳转到右边。
            // 继续在右边递归生成。
            int bbid = ctx.nextBBIdx();
            BasicBlock next;
            if (expr.op == BinaryOp.LOG_AND) {
                next = new BasicBlock("and_right_" + bbid);
                visitCondExprs(ctx, curFunc, expr.left, next.getValue(), falseBlock);
            } else {
                assert expr.op == BinaryOp.LOG_OR;
                next = new BasicBlock("or_right_" + bbid);
                visitCondExprs(ctx, curFunc, expr.left, trueBlock, next.getValue());
            }
            ctx.current = next;
            curFunc.bbs.add(next);
            visitCondExprs(ctx, curFunc, expr.right, trueBlock, falseBlock);
        } else { // 正常生成，通过 NonShortLogicExpr 已经确保是 i1 类型
            var cond = this.visitDstExpr(ctx, curFunc, inLogic);
            ctx.addToCurrentBB(new BranchInst(ctx.current, cond, trueBlock, falseBlock));
        }
    }

    void setArrayInitialVal(FakeSSAGeneratorContext ctx, ssa.ds.Func curFunc, Value ptr, InitValue initValue,
            List<Integer> dims) {
        // 先把整个内存区域memset为0，再仅赋值非零元素
        if (initValue.isAllZero) { // 为了第一次调用时检查。
            return;
        }

        assert initValue.isArray;
        // 处理多维度
        if (dims.size() > 1) {
            int currentSize = dims.get(0);
            dims = dims.subList(1, dims.size());
            for (int i = 0; i < currentSize; i++) {
                var iv = initValue.values.get(i);
                // TODO 先把整个内存区域memset为0，再仅赋值非零元素
                // if (!iv.isAllZero) {
                    // 生成Gep
                    var ptr_ = new GetElementPtr(ctx.current, ptr);
                    ptr_.addIndex(ConstantValue.ofInt(i));
                    ctx.addToCurrentBB(ptr_);
                    // 递归调用
                    setArrayInitialVal(ctx, curFunc, ptr_, iv, dims);
                // }
            }
        } else {
            int dim = dims.get(0);
            // 逐个维度进行初始化
            for (int i = 0; i < dim; i++) {
                var initval = initValue.values.get(i);
                assert !initval.isArray;
                
                // 先把整个内存区域memset为0，再仅赋值非零元素
                if (initval.value instanceof LiteralExpr && ((LiteralExpr) (initval.value)).value.isDefault()) {
                    
                } else {// 如果iv不是默认值则生成gep和store
                    // 计算地址
                    var ptr_ = new GetElementPtr(ctx.current, ptr);
                    ptr_.addIndex(ConstantValue.ofInt(i));
                    ctx.addToCurrentBB(ptr_);
                    // 根据初始化值表达式生成初始化指令
                    var val = visitDstExpr(ctx, curFunc, initval.value);
                    // 然后把初始化的结果写入到 gep 指令算出的地址那里
                    var inst = new StoreInst.Builder(ctx.current).addOperand(val, ptr_).build();
                    ctx.addToCurrentBB(inst);
                }
            }
        }
    }
}
