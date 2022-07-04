package dst.ds;

import java.util.List;

public class LValExpr extends Expr {
    public String id;
    public boolean isArray;
    public List<Expr> indexExprs;

    public LValExpr(String id, boolean isArray, List<Expr> indexExprs) {
        this.id = id;
        this.isArray = isArray;
        this.indexExprs = indexExprs;
    }

    @Override
    public EvaluatedValue eval() {
        return null;
    }
}
