package dst.ds;

public abstract class Expr {
    public boolean isConst;
    public EvaluatedValue value;
    public Type type;
    public Boolean evaluable() {
        return value != null;
    }

    public abstract EvaluatedValue eval();

    public void setType(Type type) {
        this.type = type;
    }
}
