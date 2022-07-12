package ir;

import ir.ds.DeclSymbol;
import ir.ds.FuncSymbol;
import ir.ds.Module;
import ir.ds.Scope;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import ds.Global;
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
import dst.ds.Func;
import dst.ds.FuncCall;
import dst.ds.FuncType;
import dst.ds.IfElseStatement;
import dst.ds.InitValType;
import dst.ds.InitValue;
import dst.ds.LValExpr;
import dst.ds.LiteralExpr;
import dst.ds.LogicExpr;
import dst.ds.LoopStatement;
import dst.ds.NonShortLogicExpr;
import dst.ds.ReturnStatement;
import dst.ds.Type;
import dst.ds.UnaryExpr;
import dst.ds.CastExpr.CastType;

public class SemanticAnalyzer {

    public Module process(CompUnit dst) {
        var module = new Module(new Scope(), "main");
        this.visitDstCompUnit(new SemanticAnalysisContext(module), dst);
        return module;
    }

    private void visitDstCompUnit(SemanticAnalysisContext ctx, CompUnit dst) {
        Module.builtinFuncs.forEach(func -> this.visitDstFunc(ctx, func));
        dst.decls.forEach(decl -> this.visitDstDecl(ctx, null, decl));
        dst.funcs.forEach(func -> this.visitDstFunc(ctx, func));
    }

    /**
     * Visit a dst.ds.Decl.
     * 
     * @param ctx
     * @param decl
     */
    private void visitDstDecl(SemanticAnalysisContext ctx, FuncSymbol curFunc, Decl decl) {
        // Decl的dims必然可以编译期求值 之后存到Type里
        List<Integer> evaledDims = null;

        if (decl.hasDims()) {
            // eval dims to evaledDims
            evaledDims = decl.dims.stream().map(i -> i.eval(ctx.scope).intValue).collect(Collectors.toList());
            // 确保是非负整数
            evaledDims.forEach(i -> {assert i > 0;});

            if (decl.initVal != null) {
                // 将数组的初始化展开
                assert decl.initVal.isArray;
                
                // 准备默认值
                InitValType ty = decl.initVal.initType;
                InitValue def = InitValue.ofExpr(ty, new LiteralExpr(EvaluatedValue.getDefault(decl.basicType)));
                var flattened = flattenInitVal(evaledDims, new LinkedList<InitValue>(decl.initVal.values), decl.initVal.initType, def);
                Global.logger.trace("--- flatten " + decl.id + " ---");
                Global.logger.trace(flattened.toString());
                decl.initVal = flattened;
            }
        }

        {
            var basicType = decl.basicType;
            var isArray = decl.isArray();
            var isVarlen = false;
            decl.type = new Type(basicType, isArray, isVarlen, evaledDims);
            decl.type.isPointer = decl.isDimensionOmitted; // for func param
        }

        // 先填入type，再递归访问expr。
        if (decl.initVal != null) {
            visitInitValue(ctx, curFunc, decl.initVal);
            if (!decl.type.isArray) { // 插入类型转换节点
                if (!Type.isMatch(decl.type, decl.initVal.value.type)) {
                    var cast = new CastExpr(decl.initVal.value, getCastType(decl.type, decl.initVal.value.type), "decl initial value");
                    decl.initVal.value = cast;
                }
            }
        }

        if (decl.isConst()) {
            if (decl.isConst() && decl.initVal == null) {
                throw new RuntimeException("constant must be initialized");
            }
            // 处理初始值，填充 InitValue.evaledVal
            evalInitValue(ctx.scope, decl.initVal);
        }

        if (curFunc == null && decl.initVal != null) { // 全局变量的初始化值也是constExpr
            // 处理初始值，填充 InitValue.evaledVal
            evalInitValue(ctx.scope, decl.initVal);            
        }

        var symbol = new DeclSymbol(decl);

        var ok = ctx.scope.register(symbol);
        if (!ok) {
            throw new RuntimeException("duplicate decl symbol");
        }
    }

    /**
     * Visit a dst.ds.Func, function definition.
     * 
     * @param ctx
     * @param func
     */
    private void visitDstFunc(SemanticAnalysisContext ctx, Func func) {
        var symbol = new FuncSymbol(func);
        var ok = ctx.scope.register(symbol);
        if (!ok) {
            throw new RuntimeException("duplicate func symbol: " + func.id);
        }
        // enter function scope for params and body
        ctx.enterScope();
        {
            func.params.forEach(param -> this.visitDstDecl(ctx, symbol, param));
            this.visitDstFuncBody(ctx, symbol);
        }
        ctx.leaveScope();
    }

    private void visitDstFuncBody(SemanticAnalysisContext ctx, FuncSymbol curFunc) {
        curFunc.func.body.statements.forEach(stmt -> this.visitDstStmt(ctx, curFunc, stmt));
    }

    private void visitDstStmt(SemanticAnalysisContext ctx, FuncSymbol curFunc, BlockStatement stmt_) {
        if (stmt_ instanceof AssignStatement) {
            var stmt = (AssignStatement) stmt_;
            visitDstExpr(ctx, curFunc, stmt.left); // set left type
            var decl = stmt.left.declSymbol.decl;
            if (decl.isConst()) { // 这里才能确定LValExpr在左边
                throw new RuntimeException("cannot assign to constant");
            }
            visitDstExpr(ctx, curFunc, stmt.expr); // set right type
            if (!Type.isMatch(stmt.left.type, stmt.expr.type)) { // 右边cast为左边的类型
                var cast = new CastExpr(stmt.expr, getCastType(stmt.left.type, stmt.expr.type), "assign");
                stmt.expr = cast;
            }
            return;
        }

        if (stmt_ instanceof Block) {
            var stmt = (Block) stmt_;
            ctx.enterScope();
            {
                stmt.statements.forEach(innerStmt -> this.visitDstStmt(ctx, curFunc, innerStmt));
            }
            ctx.leaveScope();
            return;
        }

        if (stmt_ instanceof BreakStatement) {
            var stmt = (BreakStatement) stmt_;
            if (!ctx.inLoop()) {
                throw new RuntimeException("return statement must be in loop");
            }
            stmt.loop = ctx.currentLoop();
            return;
        }

        if (stmt_ instanceof ContinueStatement) {
            var stmt = (ContinueStatement) stmt_;
            if (!ctx.inLoop()) {
                throw new RuntimeException("return statement must be in loop");
            }
            stmt.loop = ctx.currentLoop();
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
            this.visitDstExpr(ctx, curFunc, stmt.condition);
            ctx.enterLoop(stmt);
            {
                this.visitDstStmt(ctx, curFunc, stmt.bodyBlock);
            }
            ctx.leaveLoop();
            return;
        }

        if (stmt_ instanceof ReturnStatement) {
            var stmt = (ReturnStatement) stmt_;
            if (!ctx.inFunc()) {
                throw new RuntimeException("return statement must be in function");
            }
            stmt.funcSymbol = curFunc;
            if (curFunc.func.retType == FuncType.VOID) {
                if (stmt.retval != null) {
                    throw new RuntimeException("void function cannot return value");
                }
                return;
            }
            if (stmt.retval == null) {
                throw new RuntimeException("non-void function must return value");
            }
            visitDstExpr(ctx, curFunc, stmt.retval);
            if (!Type.isMatch(FuncType.toType(curFunc.func.retType), stmt.retval.type)) {
                var cast = new CastExpr(stmt.retval, getCastType(FuncType.toType(curFunc.func.retType), stmt.retval.type), "func ret");
                stmt.retval = cast;
            }

            return;
        }
    }

    private void visitDstExpr(SemanticAnalysisContext ctx, FuncSymbol curFunc, Expr expr_) {
        if (expr_ instanceof BinaryExpr) {
            var expr = (BinaryExpr) expr_;
            visitDstExpr(ctx, curFunc, expr.left);
            visitDstExpr(ctx, curFunc, expr.right);
            if (expr.left.type.basicType != expr.right.type.basicType) { // 类型提升
                var c = getCastType(expr.left.type, expr.right.type);
                if (c == CastType.F2I) { // 左i右f
                    var cast = new CastExpr(expr.left, CastExpr.CastType.I2F, "expr promote");
                    expr.left = cast;
                } else {
                    var cast = new CastExpr(expr.right, CastExpr.CastType.I2F, "expr promote");
                    expr.right = cast;
                }
            }
            expr.setType(Type.getCommon(expr.left.type, expr.right.type));
            return;
        }

        if (expr_ instanceof FuncCall) {
            var expr = (FuncCall) expr_;
            var symbol = ctx.scope.resolve(expr.funcName);
            if (!(symbol instanceof FuncSymbol)) {
                throw new RuntimeException("undefined function: " + expr.funcName);
            }
            var funcSymbol = (FuncSymbol) symbol;
            expr.funcSymbol = funcSymbol;

            int len = 0;
            if (expr.args != null) {
                len = expr.args.length;
            }
            if (funcSymbol.func.params.size() != len) {
                if (!(funcSymbol.func.isVariadic && funcSymbol.func.params.size() < len)) {
                    throw new RuntimeException("function call argument count mismatch");
                }
            }
            for (var i = 0; i < len; i++) {
                var arg = expr.args[i];
                visitDstExpr(ctx, curFunc, arg); // visit的同时设置类型
                if (i < funcSymbol.func.params.size()) { // non vararg
                    var param = funcSymbol.func.params.get(i);
                    if (!Type.isMatch(param.type, arg.type)) { // 类型转换
                        var cast = new CastExpr(arg, getCastType(param.type, arg.type), "func param");
                        expr.args[i] = cast;
                    }
                }
            }
            expr.setType(Type.fromFuncType(funcSymbol.func.retType));
            return;
        }

        if (expr_ instanceof LiteralExpr) {
            var expr = (LiteralExpr) expr_;
            expr.setType(Type.frombasicType(expr.value.basicType));
            return;
        }

        if (expr_ instanceof LogicExpr) {
            var expr = (LogicExpr) expr_;
            visitDstExpr(ctx, curFunc, expr.expr);
            expr.setType(expr.expr.type);
            return;
        }

        if (expr_ instanceof NonShortLogicExpr) {
            var expr = (NonShortLogicExpr) expr_;
            visitDstExpr(ctx, curFunc, expr.expr);
            expr.setType(expr.expr.type);
            return;
        }

        if (expr_ instanceof LValExpr) {
            var expr = (LValExpr) expr_;
            var symbol = ctx.scope.resolve(expr.id);
            if (symbol == null) {
                throw new RuntimeException("undefined symbol: " + expr.id);
            }
            if (symbol instanceof FuncSymbol) {
                throw new RuntimeException("cannot access function");
            }
            if (!(symbol instanceof DeclSymbol)) {
                throw new RuntimeException("cannot access non-decl symbol");
            }
            var declSymbol = (DeclSymbol) symbol;
            expr.declSymbol = declSymbol;
            for (var i = 0; i < expr.indices.size(); i++) {
                Expr index = expr.indices.get(i);
                visitDstExpr(ctx, curFunc, index);
                if (!Type.isMatch(Type.Integer, index.type)) {
                    throw new RuntimeException("array index must be integer");
                }
            }
            if (expr.isArray) {
                Type base = declSymbol.decl.type.clone();
                // no need for bound check
                expr.indices.forEach(i -> {base.dims.remove(0);});
                if (base.dims.size() == 0) {
                    base.isArray = false;
                    base.dims = null;
                }
                expr.setType(base);
            } else {
                expr.setType(expr.declSymbol.decl.type);
            }
            return;
        }

        if (expr_ instanceof UnaryExpr) {
            var expr = (UnaryExpr) expr_;
            visitDstExpr(ctx, curFunc, expr.expr);
            expr.setType(expr.expr.type);
            return;
        }
    }

    public CastExpr.CastType getCastType(Type to, Type from) {
        assert !to.isArray && !from.isArray;
        if (to.basicType == BasicType.INT && from.basicType ==  BasicType.FLOAT) {
            return CastExpr.CastType.F2I;
        } else if (to.basicType == BasicType.FLOAT && from.basicType ==  BasicType.INT) {
            return CastExpr.CastType.I2F;
        }
        throw new RuntimeException("Cannot get cast type");
    }

    // 递归调用visitDstExpr
    private void visitInitValue(SemanticAnalysisContext ctx, FuncSymbol curFunc, InitValue initVal) {
        if (initVal.isArray) {
            for (var i: initVal.values) {
                visitInitValue(ctx, curFunc, i);
            }
        } else {
            this.visitDstExpr(ctx, curFunc, initVal.value);
        }
    }

    // 递归对内部的value调用Eval并放到evaledVal
    private void evalInitValue(Scope scope, InitValue initVal) {
        if (initVal.isArray) {
            for (var i: initVal.values) {
                evalInitValue(scope, i);
            }
        } else {
            initVal.evaledVal = initVal.value.eval(scope);
        }
    }

    // 根据dims，基于initVal和数组初始化规则（算法见文档），填充得到新的initVal。使用InitValue.ofDefault填充
    // 内部是抽象的Expr类型，并不一定是int或Float常量
    // 同时填入initVal的isAllZero属性，
    private InitValue flattenInitVal(List<Integer> dims, Queue<InitValue> initVal, InitValType ty, InitValue def) {
        if (dims.size() > 1) { // 削减一维得到子问题，递归求解
            boolean isAllZero = true;
            List<InitValue> result = new LinkedList<>();
            int currentSize = dims.get(0);
            dims = dims.subList(1, dims.size());
            for (int i=0;i<currentSize;i++) {
                var front = initVal.size() > 0 ? initVal.peek() : null;
                Queue<InitValue> subQueue;
                if (front != null) {
                    isAllZero = false;
                    if (front.isArray) { // 解包大括号成功，子问题仅使用front
                        subQueue = new LinkedList<>(front.values);
                        initVal.remove();
                    } else { // 展开模式，直接传递当前queue
                        subQueue = initVal;
                    }
                } else {
                    subQueue = new LinkedList<>(); // 完全空了，后面元素都初始化为0
                }
                result.add(flattenInitVal(dims, subQueue, ty, def));
            }
            var ret = InitValue.ofArray(ty, result);
            ret.isAllZero = isAllZero;
            return ret;
        } else { // 处理仅一维的情况
            boolean isAllZero = true;
            List<InitValue> result = new LinkedList<>();
            int currentSize = dims.get(0);
            for (int i=0;i<currentSize;i++) {
                if (!initVal.isEmpty()) {
                    isAllZero = false;
                    if (! (initVal.peek().isArray)) { 
                        result.add(initVal.poll());
                    } else {
                        // 现在只需要叶子节点，但是出现了Array
                        Global.logger.error("Array initializer decode error");
                        // when debugging
                        throw new RuntimeException("Array initializer decode error");

                    }
                } else {
                    result.add(def); // 结果缺失，放入int 0，类型转换由后面考虑
                }
            }
            var ret = InitValue.ofArray(ty, result);
            ret.isAllZero = isAllZero;
            return ret;
        }
    }

}
