package dst.ds;

public class UnaryExpr extends Expr {
    public Expr expr;
    public UnaryOp op;

    public UnaryExpr(Expr expr, UnaryOp op) {
        this.expr = expr;
        this.op = op;
    }

    @Override
    public InitVal eval() {
        // TODO Auto-generated method stub
        return null;
    }
}