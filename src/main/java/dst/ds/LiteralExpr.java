package dst.ds;

public class LiteralExpr extends Expr {

    public LiteralExpr(EvaluatedValue value) {
        this.value = value;
    }

    @Override
    public EvaluatedValue eval() {
        return this.value;
    }
}