package dst.ds;

public class UnaryExpr extends Expr {
    public Expr expr;
    public UnaryOp op;

    public UnaryExpr(Expr expr, UnaryOp op) {
        this.expr = expr;
        this.op = op;
        this.eval();
    }

    @Override
    public EvaluatedValue eval() {
        if (this.value != null) {
            return this.value;
        }
        this.value = EvaluatedValue.fromOperation(expr.value, op);
        return this.value;
    }
}