package dst.ds;

public abstract class Expr {
    public boolean isConst;
    public InitVal value;

    public Boolean evaluable() {
        return value != null;
    }

    public abstract InitVal eval();
}
