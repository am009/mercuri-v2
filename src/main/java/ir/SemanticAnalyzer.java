package ir;

import ir.ds.DeclSymbol;
import ir.ds.FuncSymbol;
import ir.ds.Module;
import ir.ds.Scope;
import dst.ds.AssignStatement;
import dst.ds.BinaryExpr;
import dst.ds.Block;
import dst.ds.BlockStatement;
import dst.ds.BreakStatement;
import dst.ds.CompUnit;
import dst.ds.ContinueStatement;
import dst.ds.Decl;
import dst.ds.DeclType;
import dst.ds.Expr;
import dst.ds.ExprStatement;
import dst.ds.Func;
import dst.ds.FuncCall;
import dst.ds.FuncType;
import dst.ds.IfElseStatement;
import dst.ds.LValExpr;
import dst.ds.LiteralExpr;
import dst.ds.LogicExpr;
import dst.ds.LoopStatement;
import dst.ds.ReturnStatement;
import dst.ds.Type;
import dst.ds.UnaryExpr;
import dst.ds.LogicExpr.AryType;

public class SemanticAnalyzer {

    public Module process(CompUnit dst) {
        var module = new Module(new Scope(), "main");
        this.visitDstCompUnit(new SemanticAnalysisContext(module), dst);
        return module;
    }

    private void visitDstCompUnit(SemanticAnalysisContext ctx, CompUnit dst) {
        ctx.module.builtinFuncs.forEach(func -> this.visitDstFunc(ctx, func));
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
        if (decl.dims != null) {
            // make sure array dims are non-negative
            for (var dim : decl.dims) {
                if (dim < 0) {
                    throw new RuntimeException("array dims must be non-negative");
                }
            }
            // TODO: large array align cache line
        }

        if (decl.initVal != null) {
            // TODO: constant function eval
            this.visitDstExpr(ctx, curFunc, decl.initVal.value);
        }

        if (decl.declType == DeclType.CONST && decl.initVal == null) {
            throw new RuntimeException("constant must be initialized");
        }
        // TODO: fill zero for global variable (pay attention to array!)
        {
            var basicType = decl.basicType;
            var isConst = decl.declType == DeclType.CONST;
            var isArray = decl.isArray();
            var isVarlen = false;
            var dims = decl.dims;
            decl.type = new Type(basicType, isConst, isArray, isVarlen, dims);
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
            var symbol = ctx.scope.resolve(stmt.id);
            if (symbol == null) {
                throw new RuntimeException("undefined symbol: " + stmt.id);
            }
            if (symbol instanceof FuncSymbol) {
                throw new RuntimeException("cannot assign to function");
            }
            if (!(symbol instanceof DeclSymbol)) {
                throw new RuntimeException("cannot assign to non-decl symbol");
            }
            var decl = (DeclSymbol) symbol;
            if (decl.decl.declType == DeclType.CONST) {
                throw new RuntimeException("cannot assign to constant");
            }
            if (!Type.isMatch(decl.decl.type, stmt.expr.type)) {
                throw new RuntimeException("type mismatch");
            }
            stmt.symbol = decl;
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
                throw new RuntimeException("type mismatch");
            }

            return;
        }
    }

    private void visitDstExpr(SemanticAnalysisContext ctx, FuncSymbol curFunc, Expr expr_) {
        if (expr_ instanceof BinaryExpr) {
            var expr = (BinaryExpr) expr_;
            visitDstExpr(ctx, curFunc, expr.left);
            visitDstExpr(ctx, curFunc, expr.right);
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
            // TODO: var args
            if (funcSymbol.func.params.size() != expr.args.length) {
                throw new RuntimeException("function call argument count mismatch");
            }
            for (var i = 0; i < funcSymbol.func.params.size(); i++) {
                var param = funcSymbol.func.params.get(i);
                var arg = expr.args[i];
                if (!Type.isMatch(param.type, arg.type)) {
                    throw new RuntimeException(
                            "function call of " + funcSymbol.getName() + " argument type mismatch at index " + i);
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
            if (expr.aryType == AryType.Binary) {
                visitDstExpr(ctx, curFunc, expr.binaryExpr.left);
                visitDstExpr(ctx, curFunc, expr.binaryExpr.right);
                expr.setType(Type.getCommon(expr.binaryExpr.left.type, expr.binaryExpr.right.type));
            } else {
                visitDstExpr(ctx, curFunc, expr.unaryExpr);
                expr.setType(expr.unaryExpr.type);
            }
            return;
        }

        if (expr_ instanceof LValExpr) {
            var expr = (LValExpr) expr_;
            var symbol = ctx.scope.resolve(expr.id);
            if (symbol == null) {
                throw new RuntimeException("undefined symbol: " + expr.id);
            }
            if (symbol instanceof FuncSymbol) {
                throw new RuntimeException("cannot assign to function");
            }
            if (!(symbol instanceof DeclSymbol)) {
                throw new RuntimeException("cannot assign to non-decl symbol");
            }
            var declSymbol = (DeclSymbol) symbol;
            if (declSymbol.decl.declType == DeclType.CONST) {
                throw new RuntimeException("cannot assign to constant");
            }
            expr.declSymbol = declSymbol;
            for (var i = 0; i < expr.indices.size(); i++) {
                Expr index = expr.indices.get(i);
                visitDstExpr(ctx, curFunc, index);
                if (!Type.isMatch(Type.Integer, index.type)) {
                    throw new RuntimeException("array index must be integer");
                }
            }
            expr.setType(declSymbol.decl.type);
            return;
        }

        if (expr_ instanceof UnaryExpr) {
            var expr = (UnaryExpr) expr_;
            visitDstExpr(ctx, curFunc, expr.expr);
            expr.setType(expr.expr.type);
            return;
        }
    }

}
