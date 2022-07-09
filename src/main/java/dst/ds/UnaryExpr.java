package dst.ds;

import ir.ds.Scope;

public class UnaryExpr extends Expr {
    public Expr expr;
    public UnaryOp op;

    public UnaryExpr(Expr expr, UnaryOp op) {
        this.expr = expr;
        this.op = op;
        // this.eval();
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        return EvaluatedValue.fromOperation(expr.eval(scope), op);
    }
}