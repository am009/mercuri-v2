package dst.ds;

public class ExprStatement extends BlockStatement {
    public Expr expr;

    public ExprStatement(Expr expr) {
        this.expr = expr;
    }
}