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
        dst.decls.forEach(decl -> this.visitDstDecl(ctx, decl));
        dst.funcs.forEach(func -> this.visitDstFunc(ctx, func));
    }

    private void visitDstDecl(SemanticAnalysisContext ctx, Decl decl) {
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
            var evaled = decl.initVal.value.eval();
            if (evaled.basicType != decl.basicType) {
                throw new RuntimeException("type mismatch");
            }
        }
        if (decl.declType == DeclType.CONST && decl.initVal == null) {
            throw new RuntimeException("constant must be initialized");
        }
        // TODO: fill zero for global variable (pay attention to array!)

        var symbol = new DeclSymbol(decl);
        var ok = ctx.curScope.register(symbol);
        if (!ok) {
            throw new RuntimeException("duplicate decl symbol");
        }
    }

    private void visitDstFunc(SemanticAnalysisContext ctx, Func func) {
        var symbol = new FuncSymbol(func);
        var ok = ctx.curScope.register(symbol);
        if (!ok) {
            throw new RuntimeException("duplicate func symbol: " + func.id);
        }
        // enter function scope for params and body
        ctx.enterScope();
        {
            func.params.forEach(param -> this.visitDstDecl(ctx, param));
            this.visitDstFuncBody(ctx, func);
        }
        ctx.leaveScope();
    }

    private void visitDstFuncBody(SemanticAnalysisContext ctx, Func func) {
        func.body.statements.forEach(stmt -> this.visitDstStmt(ctx, func, stmt));
    }

    private void visitDstStmt(SemanticAnalysisContext ctx, Func func, BlockStatement stmt_) {
        if (stmt_ instanceof AssignStatement) {
            var stmt = (AssignStatement) stmt_;
            var symbol = ctx.curScope.resolve(stmt.id);
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
            return;
        }
        if (stmt_ instanceof Block) {
            var stmt = (Block) stmt_;
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
            return;
        }
        if (stmt_ instanceof ExprStatement) {
            var stmt = (ExprStatement) stmt_;
            this.visitDstExpr(ctx, func, stmt.expr);
            return;
        }
        if (stmt_ instanceof IfElseStatement) {
            var stmt = (IfElseStatement) stmt_;
            return;
        }
        if (stmt_ instanceof LoopStatement) {
            var stmt = (LoopStatement) stmt_;
            return;
        }
        if (stmt_ instanceof ReturnStatement) {
            var stmt = (ReturnStatement) stmt_;
            return;
        }
    }

    private void visitDstExpr(SemanticAnalysisContext ctx, Func func, Expr expr_) {
        if (expr_ instanceof BinaryExpr) {
            var expr = (BinaryExpr) expr_;
            visitDstExpr(ctx, func, expr.left);
            visitDstExpr(ctx, func, expr.right);
            expr.setType(Type.getCommon(expr.left.type, expr.right.type));
            return;
        }
        if (expr_ instanceof FuncCall) {
            var expr = (FuncCall) expr_;
            var symbol = ctx.curScope.resolve(expr.funcName);
            if (!(symbol instanceof FuncSymbol)) {
                throw new RuntimeException("undefined function: " + expr.funcName);
            }
            var funcSymbol = (FuncSymbol) symbol;
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
                visitDstExpr(ctx, func, expr.binaryExpr.left);
                visitDstExpr(ctx, func, expr.binaryExpr.right);
                expr.setType(Type.getCommon(expr.binaryExpr.left.type, expr.binaryExpr.right.type));
            } else {
                visitDstExpr(ctx, func, expr.unaryExpr);
                expr.setType(expr.unaryExpr.type);                
            }
            return;
        }
        if (expr_ instanceof LValExpr) {
            var expr = (LValExpr) expr_;
            var symbol = ctx.curScope.resolve(expr.id);
            if (symbol == null) {
                throw new RuntimeException("undefined symbol: " + expr.id);
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
            expr.setType(decl.decl.type);
            return;
        }
        if (expr_ instanceof UnaryExpr) {
            var expr = (UnaryExpr) expr_;
            visitDstExpr(ctx, func, expr.expr);
            expr.setType(expr.expr.type);
            return;
        }
    }

}
