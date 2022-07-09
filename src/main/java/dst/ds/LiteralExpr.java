package dst.ds;

import ir.ds.Scope;

public class LiteralExpr extends Expr {
    public EvaluatedValue value;

    public LiteralExpr(EvaluatedValue value) {
        this.value = value;
        isConst = true;
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        return this.value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}