package dst;

import java.util.List;

import ast.SysyBaseVisitor;
import ast.SysyParser.AddExprContext;
import ast.SysyParser.CondContext;
import ast.SysyParser.ConstExprContext;
import ast.SysyParser.EqExprContext;
import ast.SysyParser.ExprContext;
import ast.SysyParser.FuncArgContext;
import ast.SysyParser.FuncArgExprContext;
import ast.SysyParser.FuncArgStrContext;
import ast.SysyParser.FuncArgsContext;
import ast.SysyParser.LValContext;
import ast.SysyParser.LogicAndExprContext;
import ast.SysyParser.LogicOrExpContext;
import ast.SysyParser.MulExprContext;
import ast.SysyParser.NumberContext;
import ast.SysyParser.PrimaryExprLValContext;
import ast.SysyParser.PrimaryExprNumberContext;
import ast.SysyParser.PrimaryExprQuoteContext;
import ast.SysyParser.RelExprContext;
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
import dst.ds.LValExpr;
import dst.ds.LiteralExpr;
import dst.ds.LogicExpr;
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
    public Expr visitAddExpr(AddExprContext ast, DstGeneratorContext ctx) {
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
    public Expr visitMulExpr(MulExprContext ast, DstGeneratorContext ctx) {
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
    public Expr visitUnaryExpr(UnaryExprContext ast, DstGeneratorContext ctx) {
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
    public Expr visitUnaryPrimaryExpr(UnaryPrimaryExprContext ast0, DstGeneratorContext ctx) {
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

    public Expr visitPrimaryExprQuote(PrimaryExprQuoteContext ast0, DstGeneratorContext ctx) {
        return this.visitExpr(ast0.expr(), ctx);
    }

    public Expr visitPrimaryExprLVal(PrimaryExprLValContext ast0, DstGeneratorContext ctx) {
        return this.visitLVal(ast0.lVal(), ctx);
    }

    /**
     * 
     * lVal
     * : ID ('[' expr ']')*
     * ;
     */
    public LValExpr visitLVal(LValContext lVal, DstGeneratorContext ctx) {
        String id = lVal.ID().getText();
        boolean isArray = lVal.expr().size() > 0;
        List<Expr> indexExprs = lVal.expr().stream().map(e -> this.visitExpr(e, ctx))
                .collect(java.util.stream.Collectors.toList());
        return new LValExpr(id, isArray, indexExprs);
    }

    public LiteralExpr visitPrimaryExprNumber(PrimaryExprNumberContext ast0, DstGeneratorContext ctx) {
        return this.visitNumber(ast0.number(), ctx);
    }

    public LiteralExpr visitNumber(NumberContext number, DstGeneratorContext ctx) {
        if (number.INT_CONSTANT() != null) {
            String raw = number.getText();
            Integer value;
            if (raw.charAt(0) != '0' || raw.length() == 1) {
                value = (int) Long.parseLong(raw, 10); // 支持 -2147483648
            } else if (raw.charAt(1) == 'x' || raw.charAt(1) == 'X') {
                value = Integer.parseInt(raw.substring(2), 16);
            } else if (raw.charAt(1) == 'b' || raw.charAt(1) == 'B') {
                value = Integer.parseInt(raw.substring(2), 2);
            } else if (raw.charAt(1) <= '7' && raw.charAt(1) >= '0') {
                value = Integer.parseInt(raw.substring(1), 8);
            } else {
                throw new NumberFormatException();
            }
            return new LiteralExpr(EvaluatedValue.ofInt(value));
        } else if (number.FLOAT_CONSTANT() != null) {
            var value = Float.parseFloat(number.getText());
            return new LiteralExpr(EvaluatedValue.ofFloat(value));
        } else {
            ctx.panic("Unreachable");
            return null;
        }
    }

    public FuncCall visitUnaryFunc(UnaryFuncContext ast0, DstGeneratorContext ctx) {
        return new FuncCall(ast0.ID().getText(), this.visitFuncArgs(ast0.funcArgs(), ctx));
    }

    public Expr[] visitFuncArgs(FuncArgsContext funcArgs, DstGeneratorContext ctx) {
        if (funcArgs != null) {
            return funcArgs.funcArg().stream().map(e -> this.visitFuncArg(e, ctx)).toArray(Expr[]::new);
        } else { // 没有参数
            return null;
        }
        
    }

    /**
     * funcArg
     * : expr # funcArgExpr
     * | STRING_LITERAL # funcArgStr
     * ;
     */
    public Expr visitFuncArg(FuncArgContext arg, DstGeneratorContext ctx) {
        if (arg instanceof FuncArgExprContext) {
            return this.visitFuncArgExpr((FuncArgExprContext) arg, ctx);
        } else if (arg instanceof FuncArgStrContext) {
            return this.visitFuncArgStr((FuncArgStrContext) arg, ctx);
        } else {
            ctx.panic("Unreachable");
            return null;
        }
    }

    public LiteralExpr visitFuncArgStr(FuncArgStrContext arg, DstGeneratorContext ctx) {
        return new LiteralExpr(EvaluatedValue.ofString(arg.STRING_LITERAL().getText()));
    }

    public Expr visitFuncArgExpr(FuncArgExprContext arg, DstGeneratorContext ctx) {
        return this.visitExpr(arg.expr(), ctx);
    }

    public Expr visitUnaryOpExpr(UnaryOpExprContext ast0, DstGeneratorContext ctx) {
        var expr = this.visitUnaryExpr(ast0.unaryExpr(), ctx);
        var op = UnaryOp.fromString(ast0.unaryOp().getText());
        if (op == UnaryOp.POS) {
            return expr;
        }
        return new UnaryExpr(expr, op);
    }

    /**
     * logicOrExp
     * : logicAndExpr
     * | logicOrExp '||' logicAndExpr
     * ;
     */
    public LogicExpr visitLogicOrExpr(LogicOrExpContext ast, DstGeneratorContext ctx) {
        if (ast.logicOrExp() == null) {
            return this.visitLogicAndExpr(ast.logicAndExpr(), ctx);
        } else {
            Expr left = this.visitLogicOrExpr(ast.logicOrExp(), ctx);
            Expr right = this.visitLogicAndExpr(ast.logicAndExpr(), ctx);
            var op = BinaryOp.LOG_OR;
            return new LogicExpr(new BinaryExpr(left, right, op));
        }
    }

    /**
     * logicAndExpr
     * : eqExpr
     * | logicAndExpr '&&' eqExpr
     * ;
     */
    private LogicExpr visitLogicAndExpr(LogicAndExprContext logicAndExpr, DstGeneratorContext ctx) {
        if (logicAndExpr.logicAndExpr() == null) {
            return this.visitEqExpr(logicAndExpr.eqExpr(), ctx);
        } else {
            Expr left = this.visitLogicAndExpr(logicAndExpr.logicAndExpr(), ctx);
            Expr right = this.visitEqExpr(logicAndExpr.eqExpr(), ctx);
            var op = BinaryOp.LOG_AND;
            return new LogicExpr(new BinaryExpr(left, right, op));
        }
    }

    /**
     * eqExpr
     * : relExpr
     * | eqExpr ('==' | '!=') relExpr
     * ;
     */
    private LogicExpr visitEqExpr(EqExprContext eqExpr, DstGeneratorContext ctx) {
        if (eqExpr.eqExpr() == null) {
            return this.visitRelExpr(eqExpr.relExpr(), ctx);
        } else {
            Expr left = this.visitEqExpr(eqExpr, ctx);
            Expr right = this.visitRelExpr(eqExpr.relExpr(), ctx);
            var op = BinaryOp.fromString(eqExpr.getChild(1).getText());
            return new LogicExpr(new BinaryExpr(left, right, op));
        }
    }

    /**
     * relExpr
     * : addExpr
     * | relExpr ('<' | '>' | '<=' | '>=' ) addExpr
     * ;
     */
    private LogicExpr visitRelExpr(RelExprContext relExpr, DstGeneratorContext ctx) {
        if (relExpr.relExpr() == null) {
            var exprGeneric = this.visitAddExpr(relExpr.addExpr(), ctx);
            if (exprGeneric instanceof BinaryExpr) {

                return new LogicExpr((BinaryExpr) exprGeneric);
            } else {
                return new LogicExpr((Expr) exprGeneric);
            }
        } else {
            Expr left = this.visitRelExpr(relExpr.relExpr(), ctx);
            Expr right = this.visitAddExpr(relExpr.addExpr(), ctx);
            var op = BinaryOp.fromString(relExpr.getChild(1).getText());
            return new LogicExpr(new BinaryExpr(left, right, op));
        }
    }

    /**
     * cond
     * : logicOrExp
     * ;
     */
    public LogicExpr visitCond(CondContext cond, DstGeneratorContext ctx) {
        return visitLogicOrExpr(cond.logicOrExp(), ctx);
    }

}
