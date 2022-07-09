package dst.ds;

import ir.ds.Scope;

public abstract class Expr {
    public boolean isConst;
    public Type type;
    // public Boolean evaluable() {
    //     return value != null;
    // }

    public abstract EvaluatedValue eval(Scope scope);

    public void setType(Type type) {
        this.type = type;
    }
}
