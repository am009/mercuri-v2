package dst.ds;

public abstract class Expr {
    public boolean isConst;
    public EvaluatedValue value;

    public Boolean evaluable() {
        return value != null;
    }

    public abstract EvaluatedValue eval();
}
