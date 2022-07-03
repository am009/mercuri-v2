package dst.ds;

import java.util.List;

public class AssignStatement extends BlockStatement {
    // left
    public String id;
    public boolean isArray;
    public List<Expr> indexExprs;
    // right
    public Expr expr;

    public AssignStatement(String id, boolean isArray, List<Expr> indexExprs, Expr expr) {
        this.id = id;
        this.isArray = isArray;
        this.indexExprs = indexExprs;
        this.expr = expr;
    }
}
