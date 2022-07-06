package dst.ds;

import java.util.List;

import ir.ds.DeclSymbol;

public class AssignStatement extends BlockStatement {
    // left
    public String id;
    public boolean isArray;
    public List<Expr> indexExprs;
    // right
    public Expr expr;

    // after semantic analysis, the symbol of the left side of the assignment statement
    public DeclSymbol symbol;

    public AssignStatement(String id, boolean isArray, List<Expr> indexExprs, Expr expr) {
        this.id = id;
        this.isArray = isArray;
        this.indexExprs = indexExprs;
        this.expr = expr;
    }

    public AssignStatement(LValExpr lValExpr, Expr expr) {
        this.id = lValExpr.id;
        this.isArray = lValExpr.isArray;
        this.indexExprs = lValExpr.indices;
        this.expr = expr;
    }
}
