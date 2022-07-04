package dst;

import ast.SysyBaseVisitor;
import ast.SysyParser.AddExprContext;
import ast.SysyParser.ConstExprContext;
import ast.SysyParser.ExprContext;
import ast.SysyParser.FuncArgContext;
import ast.SysyParser.FuncArgExprContext;
import ast.SysyParser.FuncArgStrContext;
import ast.SysyParser.FuncArgsContext;
import ast.SysyParser.LValContext;
import ast.SysyParser.MulExprContext;
import ast.SysyParser.NumberContext;
import ast.SysyParser.PrimaryExprLValContext;
import ast.SysyParser.PrimaryExprNumberContext;
import ast.SysyParser.PrimaryExprQuoteContext;
import ast.SysyParser.UnaryExprContext;
import ast.SysyParser.UnaryFuncContext;
import ast.SysyParser.UnaryOpExprContext;
import ast.SysyParser.UnaryPrimaryExprContext;
import dst.ds.BinaryExpr;
import dst.ds.BinaryOp;
import dst.ds.DstGeneratorContext;
import dst.ds.EvaluatedValue;
import dst.ds.Expr;
import dst.ds.FuncCall;
import dst.ds.LiteralExpr;
import dst.ds.UnaryExpr;
import dst.ds.UnaryOp;

public class AstExprVisitor extends SysyBaseVisitor<Expr> {

    public Expr visitConstExpr(ConstExprContext ast, DstGeneratorContext ctx) {
        return this.visitAddExpr(ast.addExpr(), ctx);
    }

    public Expr visitExpr(ExprContext ast, DstGeneratorContext ctx) {
        return this.visitAddExpr(ast.addExpr(), ctx);
    }

    /**
     * addExpr
     * : mulExpr
     * | addExpr ('+' | '-') mulExpr
     * ;
     */
    private Expr visitAddExpr(AddExprContext ast, DstGeneratorContext ctx) {
        if (ast.addExpr() != null) {
            var left = this.visitAddExpr(ast.addExpr(), ctx);
            var right = this.visitMulExpr(ast.mulExpr(), ctx);
            return new BinaryExpr(left, right, BinaryOp.fromString(ast.getChild(1).getText()));
        } else {
            return this.visitMulExpr(ast.mulExpr(), ctx);
        }
    }

    /**
     * mulExpr
     * : unaryExpr
     * | mulExpr ('*' | '/' | '%') unaryExpr
     * ;
     */
    private Expr visitMulExpr(MulExprContext ast, DstGeneratorContext ctx) {
        if (ast.mulExpr() != null) {
            var left = this.visitMulExpr(ast.mulExpr(), ctx);
            var right = this.visitUnaryExpr(ast.unaryExpr(), ctx);
            return new BinaryExpr(left, right, BinaryOp.fromString(ast.getChild(1).getText()));
        } else {
            return this.visitUnaryExpr(ast.unaryExpr(), ctx);
        }
    }

    /**
     * unaryExpr
     * : primaryExpr #unaryPrimaryExpr
     * | ID '(' (funcArgs)? ')' #unaryFunc
     * | unaryOp unaryExpr #unaryOpExpr
     * ;
     */
    private Expr visitUnaryExpr(UnaryExprContext ast, DstGeneratorContext ctx) {
        if (ast instanceof UnaryPrimaryExprContext) {
            return this.visitUnaryPrimaryExpr((UnaryPrimaryExprContext) ast, ctx);
        } else if (ast instanceof UnaryFuncContext) {
            return this.visitUnaryFunc((UnaryFuncContext) ast, ctx);
        } else if (ast instanceof UnaryOpExprContext) {
            return this.visitUnaryOpExpr((UnaryOpExprContext) ast, ctx);
        } else {
            ctx.panic("Unreachable");
            return null;
        }
    }

    /**
     * primaryExpr
     * : '(' expr ')' #primaryExprQuote
     * | lVal #primaryExprLVal
     * | number #primaryExprNumber
     * ;
     */
    private Expr visitUnaryPrimaryExpr(UnaryPrimaryExprContext ast0, DstGeneratorContext ctx) {
        var ast = ast0.primaryExpr();
        if (ast instanceof PrimaryExprQuoteContext) {
            return this.visitPrimaryExprQuote((PrimaryExprQuoteContext) ast, ctx);
        } else if (ast instanceof PrimaryExprLValContext) {
            return this.visitPrimaryExprLVal((PrimaryExprLValContext) ast, ctx);
        } else if (ast instanceof PrimaryExprNumberContext) {
            return this.visitPrimaryExprNumber((PrimaryExprNumberContext) ast, ctx);
        } else {
            ctx.panic("Unreachable");
            return null;
        }
    }

    private Expr visitPrimaryExprQuote(PrimaryExprQuoteContext ast0, DstGeneratorContext ctx) {
        return this.visitExpr(ast0.expr(), ctx);
    }

    private Expr visitPrimaryExprLVal(PrimaryExprLValContext ast0, DstGeneratorContext ctx) {
        return this.visitLVal(ast0.lVal(), ctx);
    }

    private Expr visitLVal(LValContext lVal, DstGeneratorContext ctx) {
        return null;
    }

    private LiteralExpr visitPrimaryExprNumber(PrimaryExprNumberContext ast0, DstGeneratorContext ctx) {
        return this.visitNumber(ast0.number(), ctx);
    }

    private LiteralExpr visitNumber(NumberContext number, DstGeneratorContext ctx) {
        if (number.INT_CONSTANT() != null) {
            var value = Integer.parseInt(number.getText());
            return new LiteralExpr(EvaluatedValue.ofInt(value));
        } else if (number.FLOAT_CONSTANT() != null) {
            var value = Float.parseFloat(number.getText());
            return new LiteralExpr(EvaluatedValue.ofFloat(value));
        } else {
            ctx.panic("Unreachable");
            return null;
        }
    }

    private FuncCall visitUnaryFunc(UnaryFuncContext ast0, DstGeneratorContext ctx) {
        return new FuncCall(ast0.ID().getText(), this.visitFuncArgs(ast0.funcArgs(), ctx));
    }

    private Expr[] visitFuncArgs(FuncArgsContext funcArgs, DstGeneratorContext ctx) {
        return funcArgs.funcArg().stream().map(e -> this.visitFuncArg(e, ctx)).toArray(Expr[]::new);
    }

    /**
     * funcArg
     * : expr # funcArgExpr
     * | STRING_LITERAL # funcArgStr
     * ;
     */
    private Expr visitFuncArg(FuncArgContext arg, DstGeneratorContext ctx) {
        if (arg instanceof FuncArgExprContext) {
            return this.visitFuncArgExpr((FuncArgExprContext) arg, ctx);
        } else if (arg instanceof FuncArgStrContext) {
            return this.visitFuncArgStr((FuncArgStrContext) arg, ctx);
        } else {
            ctx.panic("Unreachable");
            return null;
        }
    }

    private LiteralExpr visitFuncArgStr(FuncArgStrContext arg, DstGeneratorContext ctx) {
        return new LiteralExpr(EvaluatedValue.ofString(arg.STRING_LITERAL().getText()));
    }

    private Expr visitFuncArgExpr(FuncArgExprContext arg, DstGeneratorContext ctx) {
        return this.visitExpr(arg.expr(), ctx);
    }

    private Expr visitUnaryOpExpr(UnaryOpExprContext ast0, DstGeneratorContext ctx) {
        var expr = this.visitUnaryExpr(ast0.unaryExpr(), ctx);
        var op = UnaryOp.fromString(ast0.unaryOp().getText());
        if(op == UnaryOp.POS){
            return expr;
        }
        return new UnaryExpr(expr, op);
    }

}
